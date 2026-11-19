-- @description query02 for PXF parallel scan plan - no Parallel Foreign Scan when parallel disabled

-- pxf_parallel_plan returns the plan lines as query results, with the
-- Gather Motion size and actual row counts masked. Do not write the plan
-- statement out in this file, see ParallelScanTest.createPlanFunctions.

SET optimizer = off;
SET enable_parallel = false;
SELECT line FROM pxf_parallel_plan('COSTS OFF', 'SELECT count(*) FROM pxf_parallel_disabled') WITH ORDINALITY AS p(line, n) ORDER BY n;
