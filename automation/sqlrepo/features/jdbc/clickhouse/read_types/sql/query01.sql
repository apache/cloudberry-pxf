-- @description ClickHouse JDBC read: expect one row of primitive types from PXF (values compared by regress output)
SET timezone='utc';
SET bytea_output='hex';

SELECT
    i_int,
    s_small,
    b_big,
    f_float32,
    d_float64,
    b_bool,
    dec,
    t_text,
    bin,
    d_date,
    d_ts,
    d_tstz,
    d_uuid
FROM pxf_ch_clickhouse_read_types
LIMIT 1;
