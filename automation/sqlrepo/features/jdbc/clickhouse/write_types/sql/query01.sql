-- @description ClickHouse JDBC write: insert full row then verify via readable external table
SET timezone='utc';
SET bytea_output='hex';

INSERT INTO pxf_ch_clickhouse_write_types (
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
) VALUES (
    1,
    2,
    3,
    1.25,
    3.1415926,
    true,
    CAST('12345.6789012345' AS numeric),
    'hello',
    decode('41424344', 'hex'),
    DATE '2020-01-02',
    TIMESTAMP '2020-01-02 03:04:05.006',
    TIMESTAMPTZ '2020-01-02 03:04:05.006+00',
    '550e8400-e29b-41d4-a716-446655440000'::uuid
);

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
FROM pxf_ch_clickhouse_write_verify
LIMIT 1;
