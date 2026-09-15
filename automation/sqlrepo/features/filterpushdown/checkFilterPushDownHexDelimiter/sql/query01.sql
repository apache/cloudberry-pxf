-- @description query01 for PXF filter pushdown with hex delimiter
--
-- start_matchsubs
--
-- # planner and optimizer may produce equivalent filters with different operand order
--
-- m/a0c25s1dJo5a1c23s1d9o5a2c16s4dtrueo0l2a3c1700s4d9.91o5a4c1042s2dJJo5a5c25s2dJJo5l0l0l0l0l0/
-- s/a0c25s1dJo5a1c23s1d9o5a2c16s4dtrueo0l2a3c1700s4d9.91o5a4c1042s2dJJo5a5c25s2dJJo5l0l0l0l0l0/a2c16s4dtrueo0l2a0c25s1dJo5a1c23s1d9o5a3c1700s4d9.91o5a4c1042s2dJJo5a5c25s2dJJo5l0l0l0l0l0/
--
-- end_matchsubs

SET gp_external_enable_filter_pushdown = true;

SET optimizer = off;

SELECT * FROM test_filter WHERE t0 = 'C' AND a1 = 2 ORDER BY t0, a1;
SELECT * FROM test_filter WHERE t0 = 'J' AND a1 = 9 AND b2 = false AND c3 = 9.91 AND d4 = 'JJ' AND e5 = 'JJ' ORDER BY t0, a1;

SET optimizer = on;

SELECT * FROM test_filter WHERE t0 = 'C' AND a1 = 2 ORDER BY t0, a1;
SELECT * FROM test_filter WHERE t0 = 'J' AND a1 = 9 AND b2 = false AND c3 = 9.91 AND d4 = 'JJ' AND e5 = 'JJ' ORDER BY t0, a1;
