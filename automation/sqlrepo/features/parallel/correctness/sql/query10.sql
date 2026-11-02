-- @description query10 for PXF parallel scan correctness - workers=0 fallback on parallel table

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 0;
SELECT count(*) FROM pxf_parallel_enabled;
