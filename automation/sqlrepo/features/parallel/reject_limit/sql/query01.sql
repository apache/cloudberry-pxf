-- @description query01 for PXF parallel scan with a segment reject limit - the limit is counted per segment, so the scan is not parallel

-- start_matchsubs
--
-- m/ERROR:\s*(S|s)egment reject limit reached/
-- s/ERROR:\s*(S|s)egment reject limit reached.*/ERROR: segment reject limit reached/
--
-- m/CONTEXT:\s*Last error was/
-- s/CONTEXT:.*/GP_IGNORE:/
--
-- m/CONTEXT:\s*External table/
-- s/CONTEXT:.*/GP_IGNORE:/
--
-- end_matchsubs

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT pxf_parallel_scan_count('SELECT count(*) FROM pxf_parallel_reject_3');
-- 12 files with one malformed row each: on up to 3 segments each segment reads
-- at least 4 malformed rows, more than the limit of 3
SELECT count(*) FROM pxf_parallel_reject_3;
SELECT pxf_parallel_scan_count('SELECT count(*) FROM pxf_parallel_reject_20');
SELECT count(*), sum(id) FROM pxf_parallel_reject_20;
