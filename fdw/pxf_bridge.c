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
 *
 */

#include "pxf_bridge.h"
#include "pxf_header.h"

#include "cdb/cdbtm.h"
#include "cdb/cdbvars.h"
#include "utils/builtins.h"

/* helper function declarations */
static void BuildUriForRead(PxfFdwScanState *pxfsstate);
static void BuildUriForWrite(PxfFdwModifyState *pxfmstate);
static size_t FillBuffer(PxfFdwScanState *pxfsstate, char *start, int minlen, int maxlen);

/*
 * Clean up churl related data structures from the PXF FDW modify state.
 */
void
PxfBridgeCleanup(PxfFdwModifyState *pxfmstate)
{
	if (pxfmstate == NULL)
		return;

	churl_cleanup(pxfmstate->churl_handle, false);
	pxfmstate->churl_handle = NULL;

	churl_headers_cleanup(pxfmstate->churl_headers);
	pxfmstate->churl_headers = NULL;

	if (pxfmstate->uri.data)
	{
		pfree(pxfmstate->uri.data);
	}

	if (pxfmstate->options)
	{
		pfree(pxfmstate->options);
	}
}

/*
 * Sets up data before starting import
 */
void
PxfBridgeImportStart(PxfFdwScanState *pxfsstate)
{
	pxfsstate->churl_headers = churl_headers_init();

	BuildUriForRead(pxfsstate);
	BuildHttpHeaders(pxfsstate->churl_headers,
					 pxfsstate->options,
					 pxfsstate->relation,
					 pxfsstate->filter_str,
					 pxfsstate->retrieved_attrs,
					 pxfsstate->projectionInfo);

	pxfsstate->churl_handle = churl_init_download(pxfsstate->uri.data, pxfsstate->churl_headers);

	/* read some bytes to make sure the connection is established */
	churl_read_check_connectivity(pxfsstate->churl_handle);
}

/*
 * Sets up data before starting export
 */
void
PxfBridgeExportStart(PxfFdwModifyState *pxfmstate)
{
	BuildUriForWrite(pxfmstate);
	pxfmstate->churl_headers = churl_headers_init();
	BuildHttpHeaders(pxfmstate->churl_headers,
					 pxfmstate->options,
					 pxfmstate->relation,
					 NULL,
					 NULL,
					 NULL);
	pxfmstate->churl_handle = churl_init_upload(pxfmstate->uri.data, pxfmstate->churl_headers);
}

/*
 * Reads data from the PXF server into the given buffer of a given size
 */
int
PxfBridgeRead(void *outbuf, int minlen, int maxlen, void *extra)
{
	size_t		n = 0;
	PxfFdwScanState *pxfsstate = (PxfFdwScanState *) extra;

	n = FillBuffer(pxfsstate, outbuf, minlen, maxlen);

	if (n == 0)
	{
		/* check if the connection terminated with an error */
		churl_read_check_connectivity(pxfsstate->churl_handle);
	}

	elog(DEBUG5, "pxf PxfBridgeRead: segment %d read %zu bytes from %s",
		 PXF_SEGMENT_ID, n, pxfsstate->options->resource);

	return (int) n;
}

/*
 * Writes data from the given buffer of a given size to the PXF server
 */
int
PxfBridgeWrite(PxfFdwModifyState *pxfmstate, char *databuf, int datalen)
{
	size_t		n = 0;

	if (datalen > 0)
	{
		n = churl_write(pxfmstate->churl_handle, databuf, datalen);
		elog(DEBUG5, "pxf PxfBridgeWrite: segment %d wrote %zu bytes to %s", PXF_SEGMENT_ID, n, pxfmstate->options->resource);
	}

	return (int) n;
}

/*
 * Format the URI for reading by adding PXF service endpoint details
 */
static void
BuildUriForRead(PxfFdwScanState *pxfsstate)
{
	PxfOptions *options = pxfsstate->options;

	resetStringInfo(&pxfsstate->uri);
	appendStringInfo(&pxfsstate->uri, "http://%s:%d/%s/read", options->pxf_host, options->pxf_port, PXF_SERVICE_PREFIX);
	elog(DEBUG2, "pxf_fdw: uri %s for read", pxfsstate->uri.data);
}

/*
 * Format the URI for writing by adding PXF service endpoint details
 */
static void
BuildUriForWrite(PxfFdwModifyState *pxfmstate)
{
	PxfOptions *options = pxfmstate->options;

	resetStringInfo(&pxfmstate->uri);
	appendStringInfo(&pxfmstate->uri, "http://%s:%d/%s/write", options->pxf_host, options->pxf_port, PXF_SERVICE_PREFIX);
	elog(DEBUG2, "pxf_fdw: uri %s with file name for write: %s", pxfmstate->uri.data, options->resource);
}

/*
 * Read data from churl until the buffer is full or there is no more data to be read
 */
static size_t
FillBuffer(PxfFdwScanState *pxfsstate, char *start, int minlen, int maxlen)
{
	size_t		n = 0;
	char	   *ptr = start;
	char	   *minend = ptr + minlen;
	char	   *maxend = ptr + maxlen;

	while (ptr < minend)
	{
		n = churl_read(pxfsstate->churl_handle, ptr, maxend - ptr);
		if (n == 0)
			break;

		ptr += n;
	}

	return ptr - start;
}

/*
 * ============================================================================
 * Cloudberry Gang-Parallel Support (Virtual Segment ID)
 *
 * In Cloudberry, parallel execution uses "gang expansion" where
 * multiple processes share the same physical segment ID. PostgreSQL's DSM
 * callbacks (InitializeDSMForeignScan, InitializeWorkerForeignScan) are
 * NOT invoked in this model.
 *
 * Instead of fragment-by-fragment coordination, we use "virtual segment IDs":
 * each gang worker sends a unique virtual segment ID to PXF, so PXF's
 * existing round-robin fragment distribution splits the data among workers
 * automatically — no PXF server changes needed.
 *
 * Example: 3 physical segments × 4 workers = 12 virtual segments.
 * Worker i on physical segment S sends virtual_seg_id = S + i * seg_count,
 * with virtual_seg_count = seg_count * workers.
 * ============================================================================
 */

/*
 * PxfBridgeImportStartVirtual
 *		Start import with virtual segment ID for Cloudberry gang-parallel mode.
 *
 * Same as PxfBridgeImportStart, but after building the standard HTTP headers,
 * overrides X-GP-SEGMENT-ID and X-GP-SEGMENT-COUNT with the virtual values.
 * This makes PXF's round-robin assign a unique subset of fragments to each
 * gang worker, eliminating data duplication.
 */
void
PxfBridgeImportStartVirtual(PxfFdwScanState *pxfsstate,
							int virtualSegId, int virtualSegCount)
{
	char		seg_id_str[16];
	char		seg_count_str[16];

	pxfsstate->churl_headers = churl_headers_init();

	BuildUriForRead(pxfsstate);
	BuildHttpHeaders(pxfsstate->churl_headers,
					 pxfsstate->options,
					 pxfsstate->relation,
					 pxfsstate->filter_str,
					 pxfsstate->retrieved_attrs,
					 pxfsstate->projectionInfo);

	/* Override physical segment ID/count with virtual values */
	pg_ltoa(virtualSegId, seg_id_str);
	pg_ltoa(virtualSegCount, seg_count_str);
	churl_headers_override(pxfsstate->churl_headers, "X-GP-SEGMENT-ID", seg_id_str);
	churl_headers_override(pxfsstate->churl_headers, "X-GP-SEGMENT-COUNT", seg_count_str);

	elog(DEBUG3, "pxf_fdw: PxfBridgeImportStartVirtual physical_seg=%d "
		 "virtual_seg_id=%d virtual_seg_count=%d",
		 PXF_SEGMENT_ID, virtualSegId, virtualSegCount);

	pxfsstate->churl_handle = churl_init_download(pxfsstate->uri.data,
												   pxfsstate->churl_headers);

	/* read some bytes to make sure the connection is established */
	churl_read_check_connectivity(pxfsstate->churl_handle);
}
