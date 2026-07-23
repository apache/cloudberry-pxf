-- @description query01 for PXF parallel scan rescan - correlated subquery triggers rescan

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT t.id, (SELECT count(*) FROM pxf_parallel_enabled WHERE id <= t.id) AS running_count
FROM pxf_parallel_enabled t
WHERE t.id IN (1, 5000, 10000)
ORDER BY t.id;
