-- @description query03 for PXF parallel scan EXPLAIN ANALYZE - Parallel Foreign Scan is executed

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
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF) SELECT count(*) FROM pxf_parallel_enabled;
