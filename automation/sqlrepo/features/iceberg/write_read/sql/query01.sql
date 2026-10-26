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

-- @description query01 for writing to and reading from an Iceberg table through PXF external tables
INSERT INTO pxf_iceberg_customer_w SELECT g, 'first_' || g, 'last_' || g, g % 90 FROM generate_series(1, 10000) g;

SELECT count(*), sum(id), min(age), max(age) FROM pxf_iceberg_customer_r;

-- a second INSERT appends to the table
INSERT INTO pxf_iceberg_customer_w SELECT g, 'first_' || g, 'last_' || g, g % 90 FROM generate_series(10001, 10010) g;

SELECT count(*), sum(id) FROM pxf_iceberg_customer_r;

-- filter pushdown: IN, BETWEEN, AND, OR
SELECT * FROM pxf_iceberg_customer_r WHERE id IN (5, 10005) ORDER BY id;

SELECT * FROM pxf_iceberg_customer_r WHERE id BETWEEN 10 AND 12 ORDER BY id;

SELECT * FROM pxf_iceberg_customer_r WHERE id IN (5, 10005) OR (id BETWEEN 10 AND 12 AND age > 10) ORDER BY id;

SELECT * FROM pxf_iceberg_customer_r WHERE first_name = 'first_42';

SELECT count(*) FROM pxf_iceberg_customer_r WHERE age = 0 AND id > 10000;
