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
 * pxf_stat_activity.c
 *
 * Backs the pxf_stat_activity SQL view. The function pxf_stat_activity_raw()
 * is dispatched with EXECUTE ON ALL SEGMENTS: each segment asks its local PXF
 * instance for the requests that originate from its own segment id (passed via
 * the X-GP-SEGMENT-ID header). Because a single PXF instance serves every
 * segment co-located on the host, this per-segment filter guarantees each
 * running query is reported exactly once, by its owning segment.
 *
 * The function is set-returning (one row per segment, carrying the PXF response
 * verbatim as a JSON text value) because EXECUTE ON ALL SEGMENTS is only
 * permitted for set-returning functions; the typed columns are produced by the
 * SQL view, keeping this layer schema-agnostic.
 */

#include "libchurl.h"

#include "fmgr.h"
#include "funcapi.h"
#include "lib/stringinfo.h"
#include "cdb/cdbvars.h"
#include "utils/builtins.h"

/* PXF service defaults, mirror of pxf_option.h / external-table pxfutils.h */
#define PXF_STAT_DEFAULT_HOST "localhost"
#define PXF_STAT_DEFAULT_PORT "5888"
#define PXF_STAT_SERVICE_PREFIX "pxf"
#define PXF_STAT_READ_BUFFER_SIZE (64 * 1024)

PG_FUNCTION_INFO_V1(pxf_stat_activity_raw);

Datum		pxf_stat_activity_raw(PG_FUNCTION_ARGS);

/*
 * Returns the PXF service authority (host:port) for the local instance,
 * honoring the PXF_HOST / PXF_PORT environment variables and falling back to
 * localhost:5888, consistent with how segments reach their local PXF.
 */
static char *
get_pxf_stat_authority(void)
{
	char	   *host = getenv("PXF_HOST");
	char	   *port = getenv("PXF_PORT");

	return psprintf("%s:%s",
					host ? host : PXF_STAT_DEFAULT_HOST,
					port ? port : PXF_STAT_DEFAULT_PORT);
}

/*
 * Issues an HTTP GET to the local PXF /pxf/stat_activity endpoint, scoped to
 * this segment, and returns the raw JSON response body as a palloc'd text value
 * in the current memory context.
 */
static text *
fetch_stat_activity_body(void)
{
	CHURL_HEADERS headers;
	CHURL_HANDLE handle;
	StringInfoData uri;
	StringInfoData response;
	char		readbuf[PXF_STAT_READ_BUFFER_SIZE];
	char		segment_id[32];
	size_t		n;
	text	   *result;

	/* build the request URI for the local PXF instance */
	initStringInfo(&uri);
	appendStringInfo(&uri, "http://%s/%s/stat_activity",
					 get_pxf_stat_authority(), PXF_STAT_SERVICE_PREFIX);

	/* scope the request to this segment so co-located segments don't duplicate */
	snprintf(segment_id, sizeof(segment_id), "%d", GpIdentity.segindex);

	headers = churl_headers_init();
	churl_headers_append(headers, "X-GP-SEGMENT-ID", segment_id);
	churl_headers_append(headers, "Accept", "application/json");

	elog(DEBUG2, "pxf_stat_activity: segment %d requesting %s",
		 GpIdentity.segindex, uri.data);

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
 * pxf_stat_activity_raw
 *
 * Set-returning function dispatched with EXECUTE ON ALL SEGMENTS (which
 * Cloudberry only permits for set-returning functions). Each segment emits a
 * single row: the raw JSON response body from its local PXF instance, scoped to
 * that segment. The pxf_stat_activity view unions these rows and expands them
 * into typed columns.
 */
Datum
pxf_stat_activity_raw(PG_FUNCTION_ARGS)
{
	FuncCallContext *funcctx;

	if (SRF_IS_FIRSTCALL())
	{
		MemoryContext oldcontext;

		funcctx = SRF_FIRSTCALL_INIT();

		/* the fetched body must outlive this call, so build it in the
		 * multi-call context and hand it back one row at a time */
		oldcontext = MemoryContextSwitchTo(funcctx->multi_call_memory_ctx);

		funcctx->user_fctx = fetch_stat_activity_body();
		funcctx->max_calls = 1;

		MemoryContextSwitchTo(oldcontext);
	}

	funcctx = SRF_PERCALL_SETUP();

	if (funcctx->call_cntr < funcctx->max_calls)
		SRF_RETURN_NEXT(funcctx, PointerGetDatum((text *) funcctx->user_fctx));

	SRF_RETURN_DONE(funcctx);
}
