-- @description query02 for PXF parallel scan EXPLAIN - no Gather node when parallel disabled

SET optimizer = off;
SET enable_parallel = false;
EXPLAIN SELECT count(*) FROM pxf_parallel_disabled;
