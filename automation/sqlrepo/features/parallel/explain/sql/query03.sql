-- @description query03 for PXF parallel scan EXPLAIN ANALYZE - Workers Launched present

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
EXPLAIN ANALYZE SELECT count(*) FROM pxf_parallel_enabled;
