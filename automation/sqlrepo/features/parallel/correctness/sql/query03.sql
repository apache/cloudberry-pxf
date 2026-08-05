-- @description query03 for PXF parallel scan correctness - cross-check parallel vs non-parallel count

SET optimizer = off;
SET enable_parallel = false;
SELECT count(*) AS non_parallel_count FROM pxf_parallel_disabled;

SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT count(*) AS parallel_count FROM pxf_parallel_enabled;
