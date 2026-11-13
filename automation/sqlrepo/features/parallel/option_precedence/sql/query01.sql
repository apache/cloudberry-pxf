-- @description query01 for PXF parallel scan - table-level enable_parallel overrides server-level

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
-- server on, table unset: parallel
SELECT pxf_parallel_scan_count('SELECT count(*) FROM pxf_parallel_srv_on_tbl_unset');
-- server on, table off: not parallel
SELECT pxf_parallel_scan_count('SELECT count(*) FROM pxf_parallel_srv_on_tbl_off');
-- server off, table on: parallel
SELECT pxf_parallel_scan_count('SELECT count(*) FROM pxf_parallel_srv_off_tbl_on');
SELECT count(*), sum(id) FROM pxf_parallel_srv_on_tbl_unset;
SELECT count(*), sum(id) FROM pxf_parallel_srv_on_tbl_off;
SELECT count(*), sum(id) FROM pxf_parallel_srv_off_tbl_on;
