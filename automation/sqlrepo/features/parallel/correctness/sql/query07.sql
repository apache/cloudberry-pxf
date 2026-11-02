-- @description query07 for PXF parallel scan correctness - column projection with WHERE

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT val FROM pxf_parallel_enabled WHERE id <= 5 ORDER BY val;
