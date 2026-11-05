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

-- @description query01 for writing to and reading from Iceberg tables through pxf_fdw foreign tables
INSERT INTO pxf_iceberg_fdw_customer SELECT g, 'first_' || g, 'last_' || g, g % 90 FROM generate_series(1, 1000) g;

SELECT count(*), sum(id), min(age), max(age) FROM pxf_iceberg_fdw_customer;

-- a second INSERT appends to the table
INSERT INTO pxf_iceberg_fdw_customer VALUES (1001, 'fdw', 'insert', 1);

SELECT count(*) FROM pxf_iceberg_fdw_customer;

SELECT * FROM pxf_iceberg_fdw_customer WHERE first_name = 'fdw';

SELECT * FROM pxf_iceberg_fdw_customer WHERE id IN (5, 1001) OR (id BETWEEN 10 AND 12 AND age > 10) ORDER BY id;

INSERT INTO pxf_iceberg_fdw_types VALUES
  (1, true, 9000000000, 1.5, 2.25, 12345.67, '2024-02-29', '2024-02-29 13:14:15.123456', '2024-02-29 13:14:15.123456+03', 'hello, "world"', '13:14:15', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
  (2, false, -1, -0.5, -1e10, -0.01, '1999-12-31', '1999-12-31 23:59:59', '1999-12-31 23:59:59+00', 'line1', '00:00:00', '00000000-0000-0000-0000-000000000000'),
  (3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
  (4, NULL, NULL, NULL, NULL, 1.5, NULL, NULL, NULL, NULL, NULL, NULL);

SELECT * FROM pxf_iceberg_fdw_types ORDER BY id;

SELECT id FROM pxf_iceberg_fdw_types WHERE b IS NULL ORDER BY id;

SELECT id FROM pxf_iceberg_fdw_types WHERE dec > 100;
