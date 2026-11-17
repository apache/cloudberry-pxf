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

-- @description query01 for writing and reading Iceberg binary and fixed columns as bytea through PXF external tables
INSERT INTO pxf_iceberg_bin_w VALUES
  (1, '\x00ff80', '\x0001ff80'),
  (2, '', '\xdeadbeef'),
  (3, NULL, NULL);

SELECT * FROM pxf_iceberg_bin_r ORDER BY id;

SELECT id, length(bin) AS bin_length, length(fix) AS fix_length FROM pxf_iceberg_bin_r ORDER BY id;

-- filters on bytea columns are not pushed down to Iceberg, the database applies them
SELECT id FROM pxf_iceberg_bin_r WHERE bin = '\x00ff80';

SELECT id FROM pxf_iceberg_bin_r WHERE bin = '' ORDER BY id;
