-- @description query01 for PXF parallel scan with Parallel Append - UNION ALL of parallel foreign tables

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT pxf_parallel_scan_count('SELECT count(*) FROM pxf_parallel_enabled');
SELECT pxf_parallel_scan_count('SELECT count(*) FROM (SELECT id FROM pxf_parallel_enabled UNION ALL SELECT id FROM pxf_parallel_enabled) s');
SELECT count(*), sum(id) FROM (SELECT id FROM pxf_parallel_enabled UNION ALL SELECT id FROM pxf_parallel_enabled) s;
SELECT count(*), sum(id) FROM (SELECT id FROM pxf_parallel_enabled UNION ALL SELECT id FROM pxf_parallel_disabled) s;
SELECT count(*), sum(id) FROM (SELECT DISTINCT id FROM pxf_parallel_enabled UNION ALL SELECT id FROM pxf_parallel_enabled) s;
