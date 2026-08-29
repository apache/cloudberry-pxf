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

/* external_table/pxf--2.2--2.1.sql */

------------------------------------------------------------------
-- PXF Activity Monitoring
------------------------------------------------------------------

-- remove the objects from the extension
ALTER EXTENSION pxf DROP FUNCTION pxf_interrupt_backend(int);
ALTER EXTENSION pxf DROP FUNCTION pxf_cancel_backend(int);
ALTER EXTENSION pxf DROP FUNCTION pxf_interrupt_backend_raw(int);
ALTER EXTENSION pxf DROP FUNCTION pxf_cancel_backend_raw(int);
ALTER EXTENSION pxf DROP VIEW pxf_stat_activity;
ALTER EXTENSION pxf DROP FUNCTION pxf_stat_activity_raw();

-- remove the objects themselves from the catalog; the view depends on
-- pxf_stat_activity_raw(), so it has to go first
DROP FUNCTION pxf_interrupt_backend(int);
DROP FUNCTION pxf_cancel_backend(int);
DROP FUNCTION pxf_interrupt_backend_raw(int);
DROP FUNCTION pxf_cancel_backend_raw(int);
DROP VIEW pxf_stat_activity;
DROP FUNCTION pxf_stat_activity_raw();
