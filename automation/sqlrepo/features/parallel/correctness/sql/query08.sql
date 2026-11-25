-- @description query08 for PXF parallel scan correctness - empty result edge case

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT count(*) FROM pxf_parallel_enabled WHERE id < 0;
