-- @description query01 for PXF parallel scan EXPLAIN - Gather node present when parallel enabled

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
EXPLAIN SELECT count(*) FROM pxf_parallel_enabled;
