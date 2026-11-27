-- Check if metatata is supported in this version of Greengage (6.31+)
with version as (
    select string_to_array(substring(version(), '(?:Database\s)(\d+\.\d+)(?:\.\d+)'), '.') as ver
)
select ver[1]::int = 6 and ver[2]::int >= 31 from version;

CREATE EXTENSION IF NOT EXISTS pxf_fdw;

2:!& python3 pxf_mock.py > /tmp/pxf_mock.log 2>&1;

1:! curl --retry 10 --retry-delay 1 --retry-connrefused -s -o /dev/null http://localhost:5889/;

CREATE FOREIGN DATA WRAPPER pxf_ext_v1
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS (protocol 'system', mpp_execute 'all segments',
        pxf_protocol 'http', pxf_port '5889', ext_protocol_version 'v1'
    );

CREATE SERVER pxf_ext_v1_server
    FOREIGN DATA WRAPPER pxf_ext_v1;

CREATE USER MAPPING FOR CURRENT_USER SERVER pxf_ext_v1_server;

CREATE FOREIGN TABLE test_t(
    t0 text,
    a1 integer
) SERVER pxf_ext_v1_server
OPTIONS (resource 'fdw_file', format 'csv', delimiter ',');

-- ANALYZE is not supported by pxf_fdw
set gp_autostats_mode = none;
set client_min_messages = info;

INSERT INTO test_t VALUES('hello world', 1);

SELECT * FROM test_t ORDER BY t0;

INSERT INTO test_t VALUES('hello world', 1);

SELECT * FROM test_t ORDER BY t0;

1:!& curl http://localhost:5889/shutdown;

1:! wait $(pgrep -f "python3 pxf_mock.py" | head -1);

-- sleep to allow the shell properly close the files
1: select pg_sleep(1);

1:! cat /tmp/pxf_mock.log;

