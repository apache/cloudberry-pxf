-- @description query01 for PXF parallel scan correctness - count with parallel

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT count(*) FROM pxf_parallel_enabled;
