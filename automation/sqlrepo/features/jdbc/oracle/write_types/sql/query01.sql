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

-- @description Oracle JDBC write: insert full row then verify via readable external table/fdw
SET timezone='utc';

INSERT INTO pxf_ora_oracle_write_types (
    i_int,
    b_big,
    f_real,
    d_double,
    dec_num,
    t_text,
    d_date,
    d_ts
) VALUES (
    1,
    3,
    1.25,
    3.1415926,
    CAST('12345.6789012345' AS numeric),
    'hello',
    DATE '2020-01-02',
    TIMESTAMP '2020-01-02 03:04:05.006'
);

SELECT
    i_int,
    b_big,
    f_real,
    d_double,
    dec_num,
    t_text,
    d_date,
    d_ts
FROM pxf_ora_oracle_write_verify
    LIMIT 1;
