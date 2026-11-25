-- @description query04 for PXF parallel scan correctness - ORDER BY with LIMIT

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT id, val FROM pxf_parallel_enabled ORDER BY id LIMIT 10;
