-- start_ignore
-- end_ignore
-- @description query02 test S3 Select access to CSV with headers and no compression
--

SELECT l_orderkey, l_quantity, l_shipmode, l_comment FROM s3select_csv
WHERE l_orderkey IN ('194', '82756')
ORDER BY l_orderkey;
