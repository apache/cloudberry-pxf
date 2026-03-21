-- @description ClickHouse JDBC read: validate primitive types via PXF (JDBC_DRIVER/DB_URL in external table DDL)
SET timezone='utc';

SELECT
  (i_int = 1) AS i_int_ok,
  (s_small = 2) AS s_small_ok,
  (b_big = 3) AS b_big_ok,
  (abs(f_float32 - 1.25) < 0.000001) AS f_float32_ok,
  (abs(d_float64 - 3.1415926) < 0.000001) AS d_float64_ok,
  (b_bool = true) AS b_bool_ok,
  (dec = CAST('12345.6789012345' AS numeric)) AS dec_ok,
  (t_text = 'hello') AS t_text_ok,
  (bin = decode('41424344','hex')) AS bin_ok,
  (d_date = DATE '2020-01-02') AS d_date_ok,
  (d_ts = TIMESTAMP '2020-01-02 03:04:05.006') AS d_ts_ok,
  (d_tstz = TIMESTAMPTZ '2020-01-02 03:04:05.006+00') AS d_tstz_ok,
  (d_uuid = '550e8400-e29b-41d4-a716-446655440000'::uuid) AS d_uuid_ok
FROM pxf_ch_clickhouse_read_types
LIMIT 1;
