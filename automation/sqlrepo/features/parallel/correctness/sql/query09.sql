-- @description query09 for PXF parallel scan correctness - COUNT DISTINCT no duplicates

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT count(DISTINCT id) FROM pxf_parallel_enabled;
