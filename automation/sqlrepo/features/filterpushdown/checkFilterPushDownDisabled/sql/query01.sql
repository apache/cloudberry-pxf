-- @description query01 for PXF filter pushdown disabled case

SET gp_external_enable_filter_pushdown = off;

SET optimizer = off;
SELECT * FROM test_filter WHERE t0 = 'C' AND a1 = 2 ORDER BY t0, a1;
SELECT * FROM test_filter WHERE t0 = 'J' AND a1 = 9 AND b2 = false AND c3 = 9.91 AND d4 = 'JJ' AND e5 = 'JJ' ORDER BY t0, a1;

SET optimizer = on;
SELECT * FROM test_filter WHERE t0 = 'C' AND a1 = 2 ORDER BY t0, a1;
SELECT * FROM test_filter WHERE t0 = 'J' AND a1 = 9 AND b2 = false AND c3 = 9.91 AND d4 = 'JJ' AND e5 = 'JJ' ORDER BY t0, a1;
