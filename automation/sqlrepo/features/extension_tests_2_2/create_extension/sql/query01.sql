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

-- @description query01 for PXF 2.2 install test (pxf_stat_activity + backend control)
-- start_matchsubs
--
-- m{.*/usr/local/pxf-(dev|gp\d).*}
-- s{/usr/local/pxf-(dev|gp\d)}{\$PXF_HOME}
--
-- m{.*\$libdir/pxf.*}
-- s{\$libdir}{\$PXF_HOME/gpextable}
--
-- end_matchsubs
-- start_ignore
\c pxfautomation_extension
-- end_ignore

SELECT extversion FROM pg_extension WHERE extname = 'pxf';

SELECT p.proname
FROM pg_catalog.pg_extension AS e
    INNER JOIN pg_catalog.pg_depend AS d ON (d.refobjid = e.oid)
    INNER JOIN pg_catalog.pg_proc AS p ON (p.oid = d.objid)
WHERE d.deptype = 'e' AND e.extname = 'pxf'
ORDER BY 1;

SELECT c.relname, c.relkind
FROM pg_catalog.pg_extension AS e
    INNER JOIN pg_catalog.pg_depend AS d ON (d.refobjid = e.oid)
    INNER JOIN pg_catalog.pg_class AS c ON (c.oid = d.objid)
WHERE d.deptype = 'e' AND e.extname = 'pxf'
ORDER BY 1;

-- the backend-control functions are reachable end-to-end (C -> local PXF -> registry);
-- with no in-flight requests they are no-ops returning 0
SELECT pxf_cancel_backend(-1);

SELECT pxf_interrupt_backend(-1);

-- the activity view is empty while PXF is idle
SELECT count(*) FROM pxf_stat_activity;
