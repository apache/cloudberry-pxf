-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

-- @description MSSQL JDBC write: insert full row then verify via readable external table/fdw
SET timezone='utc';
SET bytea_output='hex';

INSERT INTO pxf_mssql_write_types (
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
    d_tstz
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
             TIMESTAMPTZ '2020-01-02 03:04:05.006+00'
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
    d_tstz
FROM pxf_mssql_write_verify
    LIMIT 1;
