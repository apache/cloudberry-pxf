-- @description query04 for PXF parallel scan EXPLAIN - no Gather with workers=0 on parallel table

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 0;
EXPLAIN SELECT count(*) FROM pxf_parallel_enabled;
