-- @description query03 for PXF parallel scan plan - ANALYZE, the Parallel Foreign Scan is executed

-- pxf_parallel_plan returns the plan lines as query results, with the
-- Gather Motion size and actual row counts masked. Do not write the plan
-- statement out in this file, see ParallelScanTest.createPlanFunctions.

SET optimizer = off;
SET enable_parallel = true;
SET max_parallel_workers_per_gather = 4;
SELECT line FROM pxf_parallel_plan('ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF', 'SELECT count(*) FROM pxf_parallel_enabled') WITH ORDINALITY AS p(line, n) ORDER BY n;
