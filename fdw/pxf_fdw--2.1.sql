-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

/* fdw/pxf_fdw--2.1.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "CREATE EXTENSION pxf_fdw" to load this file. \quit

CREATE FUNCTION pxf_fdw_handler()
RETURNS fdw_handler
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT;

CREATE FUNCTION pxf_fdw_validator(text[], oid)
RETURNS void
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT;

CREATE FOREIGN DATA WRAPPER jdbc_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'jdbc', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER hdfs_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'hdfs', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER hive_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'hive', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER hbase_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'hbase', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER s3_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 's3', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER gs_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'gs', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER abfss_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'abfss', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER wasbs_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'wasbs', mpp_execute 'all segments' );

CREATE FOREIGN DATA WRAPPER file_pxf_fdw
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'file', mpp_execute 'all segments' );

-- Raw per-segment accessor: each segment asks its local PXF instance for the
-- activity that originates from its own segment id and returns the JSON body
-- verbatim as a single row. Dispatched to every segment; the typed columns are
-- produced by the pxf_stat_activity view below. The function is set-returning
-- (one row per segment) because EXECUTE ON ALL SEGMENTS is only permitted for
-- set-returning functions.
CREATE FUNCTION pxf_stat_activity_raw() RETURNS SETOF text
AS 'MODULE_PATHNAME', 'pxf_stat_activity_raw'
LANGUAGE C VOLATILE EXECUTE ON ALL SEGMENTS;

-- pg_stat_activity-like view of the queries currently running inside PXF,
-- aggregated across all segment hosts. DISTINCT is a safety net; PXF already
-- de-duplicates by filtering each response to the requesting segment id.
CREATE VIEW pxf_stat_activity AS
SELECT DISTINCT
    (a->>'segmentId')::int                              AS segment_id,
    (a->>'gpSessionId')::int                            AS session_id,
    (a->>'gpCommandCount')::int                         AS command_count,
    a->>'transactionId'                                 AS xid,
    a->>'requestType'                                   AS operation,
    a->>'user'                                          AS usename,
    a->>'serverName'                                    AS server,
    a->>'profile'                                       AS profile,
    a->>'schemaName'                                    AS schema_name,
    a->>'tableName'                                     AS table_name,
    a->>'dataSource'                                    AS data_source,
    to_timestamp((a->>'startTimeMs')::bigint / 1000.0)  AS query_start,
    a->>'host'                                          AS pxf_host
FROM (
    SELECT json_array_elements(raw::json -> 'activities') AS a
    FROM pxf_stat_activity_raw() AS raw
) s;

-- Per-segment cancellation primitives backing pxf_cancel_backend /
-- pxf_interrupt_backend. Each segment asks its local PXF instance to terminate
-- the in-flight requests of the given Cloudberry session that originate from its
-- own segment id, returning the JSON body verbatim as a single row (e.g.
-- {"cancelled":N} / {"interrupted":N}). Dispatched to every segment; the counts
-- are summed by the SQL wrappers below. Set-returning because EXECUTE ON ALL
-- SEGMENTS is only permitted for set-returning functions.
CREATE FUNCTION pxf_cancel_backend_raw(session_id int) RETURNS SETOF text
AS 'MODULE_PATHNAME', 'pxf_cancel_backend_raw'
LANGUAGE C VOLATILE STRICT EXECUTE ON ALL SEGMENTS;

CREATE FUNCTION pxf_interrupt_backend_raw(session_id int) RETURNS SETOF text
AS 'MODULE_PATHNAME', 'pxf_interrupt_backend_raw'
LANGUAGE C VOLATILE STRICT EXECUTE ON ALL SEGMENTS;

-- Gracefully cancels the in-flight PXF requests of a Cloudberry session across
-- the whole cluster by ending their current bridge. Returns the number of
-- requests that were signalled. Analogous to pg_cancel_backend, but keyed by
-- the Cloudberry session id (as reported in pxf_stat_activity.session_id).
CREATE FUNCTION pxf_cancel_backend(session_id int) RETURNS int AS $$
    SELECT coalesce(sum((raw::json ->> 'cancelled')::int), 0)::int
    FROM pxf_cancel_backend_raw(session_id) AS raw
$$ LANGUAGE sql VOLATILE;

-- Interrupts the worker thread(s) of the in-flight PXF requests of a Cloudberry
-- session across the whole cluster. Returns the number of requests that were
-- interrupted. A forceful complement to pxf_cancel_backend for requests that do
-- not observe cancellation (e.g. blocked in a non-interruptible read).
CREATE FUNCTION pxf_interrupt_backend(session_id int) RETURNS int AS $$
    SELECT coalesce(sum((raw::json ->> 'interrupted')::int), 0)::int
    FROM pxf_interrupt_backend_raw(session_id) AS raw
$$ LANGUAGE sql VOLATILE;
