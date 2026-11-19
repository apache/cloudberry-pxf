-- @description query02 for PXF parallel scan with Parallel Append - partitioned table with foreign partitions

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT pxf_parallel_scan_count('SELECT count(*) FROM pxf_parallel_partitioned');
SELECT count(*), sum(id) FROM pxf_parallel_partitioned;
SELECT count(*) FROM pxf_parallel_partitioned WHERE id > 9000;
