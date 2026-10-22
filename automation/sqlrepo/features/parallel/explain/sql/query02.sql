-- @description query02 for PXF parallel scan EXPLAIN - no Parallel Foreign Scan when parallel disabled

-- start_matchsubs
--
-- m/Gather Motion \d+:1  \(slice\d+; segments: \d+\)/
-- s/Gather Motion \d+:1  \(slice\d+; segments: \d+\)/Gather Motion N:1  (sliceN; segments: N)/
--
-- m/actual rows=\d+ loops=\d+/
-- s/actual rows=\d+ loops=\d+/actual rows=N loops=N/
--
-- end_matchsubs

SET optimizer = off;
SET enable_parallel = false;
EXPLAIN (COSTS OFF) SELECT count(*) FROM pxf_parallel_disabled;
