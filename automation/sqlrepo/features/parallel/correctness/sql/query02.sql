-- @description query02 for PXF parallel scan correctness - sum aggregation

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT sum(id) FROM pxf_parallel_enabled;
