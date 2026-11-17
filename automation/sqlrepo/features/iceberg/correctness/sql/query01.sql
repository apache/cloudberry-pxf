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

-- @description query01 for the correctness issues reported in apache/cloudberry-pxf#126, through PXF external tables
INSERT INTO pxf_iceberg_corr_s_w VALUES (1, ''), (2, 'éa'), (3, 'z'), (4, NULL);

-- an IN list that contains NULL is not pushed down
SELECT id FROM pxf_iceberg_corr_s_r WHERE id IN (1, NULL) ORDER BY id;

-- strings are compared by the collation of the column, not by code point
CREATE TEMP TABLE corr_s_heap AS SELECT * FROM pxf_iceberg_corr_s_r DISTRIBUTED RANDOMLY;

SELECT (SELECT count(*) FROM pxf_iceberg_corr_s_r WHERE s >= 'é') = (SELECT count(*) FROM corr_s_heap WHERE s >= 'é') AS same_count;

SELECT (SELECT count(*) FROM pxf_iceberg_corr_s_r WHERE s < 'é') = (SELECT count(*) FROM corr_s_heap WHERE s < 'é') AS same_count;

-- with filter pushdown off, a column used only in WHERE is still read
SET gp_external_enable_filter_pushdown = off;

SELECT id FROM pxf_iceberg_corr_s_r WHERE s = 'z';

RESET gp_external_enable_filter_pushdown;

-- a transaction reads the rows it has inserted
BEGIN;

SELECT count(*) FROM pxf_iceberg_corr_s_r WHERE s = 'own';

INSERT INTO pxf_iceberg_corr_s_w VALUES (10, 'own');

SELECT count(*) FROM pxf_iceberg_corr_s_r WHERE s = 'own';

COMMIT;

-- int4 into an Iceberg long column
INSERT INTO pxf_iceberg_corr_n_w (id, l) VALUES (1, 5);

SELECT id, l FROM pxf_iceberg_corr_n_r WHERE id = 1;

-- time zone offsets with seconds
SET timezone = 'Asia/Shanghai';

INSERT INTO pxf_iceberg_corr_n_w (id, tsz) VALUES (2, '1582-10-15 12:00:00+00');

SELECT id, tsz FROM pxf_iceberg_corr_n_r WHERE id = 2;

RESET timezone;

-- NaN is greater than any other value; files with NaN are not skipped by a pushed down comparison
INSERT INTO pxf_iceberg_corr_n_w (id, d) VALUES (6, 'NaN');

INSERT INTO pxf_iceberg_corr_n_w (id, d) VALUES (7, 'NaN'), (8, -5);

SELECT string_agg(id::text, ',' ORDER BY id) FROM pxf_iceberg_corr_n_r WHERE d > 0;

-- a float8 value that doesn't fit into an Iceberg float fails the INSERT, and none of its rows are committed
INSERT INTO pxf_iceberg_corr_n_w (id, f) SELECT g, CASE WHEN g = 500 THEN 1e300 ELSE 1 END FROM generate_series(100, 1000) g;

SELECT count(*) FROM pxf_iceberg_corr_n_r WHERE id >= 100;
