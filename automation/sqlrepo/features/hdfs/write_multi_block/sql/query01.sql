-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements. See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
-- Write 32 million rows through PXF before reading the resulting files.
INSERT INTO pxf_multi_block_write
    SELECT format('t%s', i::varchar(255)), i
        FROM generate_series(1, 32000000) s(i);

-- @description query01 for PXF test on Multi Blocked data
SELECT count(*) FROM pxf_multi_block_read;

-- @description query02 for PXF test on Multi Blocked data
SELECT sum(a1) FROM pxf_multi_block_read;

-- @description query03 for PXF test on Multi Blocked data
SELECT t1, a1 FROM pxf_multi_block_read ORDER BY t1 LIMIT 10;

-- @description query04 for PXF test on Multi Blocked data
SELECT cnt < 32000000 AS check FROM (
	SELECT COUNT(*) AS cnt
		FROM pxf_multi_block_read
		WHERE gp_execution_segment() = 0
	) AS a;
