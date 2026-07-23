/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * pxf_cancel_activity.c
 *
 * Backs the pxf_cancel_backend and pxf_interrupt_backend SQL functions. Each is
 * dispatched with EXECUTE ON ALL SEGMENTS: every segment asks its local PXF
 * instance to terminate the in-flight requests of a given Greenplum session
 * that originate from its own segment id (passed via X-GP-SEGMENT-ID). Because a
 * single PXF instance serves every segment co-located on the host, this
 * per-segment filter guarantees each running request is acted on exactly once,
 * by its owning segment.
 *
 * Both functions are set-returning (one row per segment, carrying the PXF
 * response verbatim as a JSON text value) because EXECUTE ON ALL SEGMENTS is
 * only permitted for set-returning functions; the counts are summed by the SQL
 * wrappers, keeping this layer schema-agnostic.
 */

#include "libchurl.h"

#include "fmgr.h"
#include "funcapi.h"
#include "lib/stringinfo.h"
#include "cdb/cdbvars.h"
#include "utils/builtins.h"

/* PXF service defaults, mirror of pxf_stat_activity.c */
#define PXF_CANCEL_DEFAULT_HOST "localhost"
#define PXF_CANCEL_DEFAULT_PORT "5888"
#define PXF_CANCEL_SERVICE_PREFIX "pxf"
#define PXF_CANCEL_READ_BUFFER_SIZE (64 * 1024)

PG_FUNCTION_INFO_V1(pxf_cancel_backend_raw);
PG_FUNCTION_INFO_V1(pxf_interrupt_backend_raw);

Datum		pxf_cancel_backend_raw(PG_FUNCTION_ARGS);
Datum		pxf_interrupt_backend_raw(PG_FUNCTION_ARGS);

/*
 * Returns the PXF service authority (host:port) for the local instance,
 * honoring the PXF_HOST / PXF_PORT environment variables and falling back to
 * localhost:5888, consistent with how segments reach their local PXF.
 */
static char *
get_pxf_cancel_authority(void)
{
	char	   *host = getenv("PXF_HOST");
	char	   *port = getenv("PXF_PORT");

	return psprintf("%s:%s",
					host ? host : PXF_CANCEL_DEFAULT_HOST,
					port ? port : PXF_CANCEL_DEFAULT_PORT);
}

/*
 * Issues an HTTP GET to the given local PXF endpoint, scoped to this segment and
 * the target Greenplum session, and returns the raw JSON response body as a
 * palloc'd text value in the current memory context.
 */
static text *
fetch_backend_control_body(const char *endpoint, int32 session_id)
{
	CHURL_HEADERS headers;
	CHURL_HANDLE handle;
	StringInfoData uri;
	StringInfoData response;
	char		readbuf[PXF_CANCEL_READ_BUFFER_SIZE];
	char		segment_id[32];
	char		session_buf[32];
	size_t		n;
	text	   *result;

	/* build the request URI for the local PXF instance */
	initStringInfo(&uri);
	appendStringInfo(&uri, "http://%s/%s/%s",
					 get_pxf_cancel_authority(), PXF_CANCEL_SERVICE_PREFIX, endpoint);

	/* scope the request to this segment so co-located segments don't act twice */
	snprintf(segment_id, sizeof(segment_id), "%d", GpIdentity.segindex);
	snprintf(session_buf, sizeof(session_buf), "%d", session_id);

	headers = churl_headers_init();
	churl_headers_append(headers, "X-GP-SEGMENT-ID", segment_id);
	churl_headers_append(headers, "X-GP-SESSION-ID", session_buf);
	churl_headers_append(headers, "Accept", "application/json");

	elog(DEBUG2, "%s: segment %d requesting %s for session %d",
		 endpoint, GpIdentity.segindex, uri.data, session_id);

	handle = churl_init_download(uri.data, headers);

	/* read the full JSON body */
	initStringInfo(&response);
	while ((n = churl_read(handle, readbuf, sizeof(readbuf))) != 0)
		appendBinaryStringInfo(&response, readbuf, n);

	/* surface any error reported by the PXF service on the closed connection */
	churl_read_check_connectivity(handle);

	churl_cleanup(handle, false);
	churl_headers_cleanup(headers);

	result = cstring_to_text(response.data);

	pfree(uri.data);
	pfree(response.data);

	return result;
}

/*
 * Shared set-returning body: emit a single row carrying the raw JSON response
 * from the local PXF instance for the given endpoint and session id. Dispatched
 * with EXECUTE ON ALL SEGMENTS (which Cloudberry only permits for set-returning
 * functions); the pxf_cancel_backend / pxf_interrupt_backend SQL wrappers sum
 * the per-segment counts.
 */
static Datum
backend_control_srf(FunctionCallInfo fcinfo, const char *endpoint)
{
	FuncCallContext *funcctx;

	if (SRF_IS_FIRSTCALL())
	{
		MemoryContext oldcontext;
		int32		session_id = PG_GETARG_INT32(0);

		funcctx = SRF_FIRSTCALL_INIT();

		/* the fetched body must outlive this call, so build it in the
		 * multi-call context and hand it back one row at a time */
		oldcontext = MemoryContextSwitchTo(funcctx->multi_call_memory_ctx);

		funcctx->user_fctx = fetch_backend_control_body(endpoint, session_id);
		funcctx->max_calls = 1;

		MemoryContextSwitchTo(oldcontext);
	}

	funcctx = SRF_PERCALL_SETUP();

	if (funcctx->call_cntr < funcctx->max_calls)
		SRF_RETURN_NEXT(funcctx, PointerGetDatum((text *) funcctx->user_fctx));

	SRF_RETURN_DONE(funcctx);
}

/*
 * pxf_cancel_backend_raw(session_id int)
 *
 * Asks the local PXF instance to gracefully cancel the in-flight requests of the
 * given session (by ending their current bridge). Returns the raw JSON body,
 * e.g. {"cancelled":N}.
 */
Datum
pxf_cancel_backend_raw(PG_FUNCTION_ARGS)
{
	return backend_control_srf(fcinfo, "cancel_backend");
}

/*
 * pxf_interrupt_backend_raw(session_id int)
 *
 * Asks the local PXF instance to interrupt the worker thread(s) of the in-flight
 * requests of the given session. Returns the raw JSON body, e.g.
 * {"interrupted":N}.
 */
Datum
pxf_interrupt_backend_raw(PG_FUNCTION_ARGS)
{
	return backend_control_srf(fcinfo, "interrupt_backend");
}
