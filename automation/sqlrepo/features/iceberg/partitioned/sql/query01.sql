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

-- @description query01 for writing to and reading from an identity-partitioned Iceberg table through PXF external tables
INSERT INTO pxf_iceberg_events_w SELECT g, (ARRAY['eu', 'us', 'apac'])[g % 3 + 1], g * 1.5 FROM generate_series(1, 3000) g;

SELECT region, count(*), sum(amount) FROM pxf_iceberg_events_r GROUP BY region ORDER BY region;

-- filter on the partition column
SELECT count(*), min(id), max(id) FROM pxf_iceberg_events_r WHERE region = 'us';

SELECT region, count(*) FROM pxf_iceberg_events_r WHERE region IN ('eu', 'apac') AND id <= 30 GROUP BY region ORDER BY region;
