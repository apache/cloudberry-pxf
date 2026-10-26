-- @description query05 for PXF parallel scan correctness - MIN/MAX/AVG aggregates

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT min(id), max(id), avg(id) FROM pxf_parallel_enabled;
