#!/bin/bash
# --------------------------------------------------------------------
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright
# ownership. The ASF licenses this file to You under the Apache
# License, Version 2.0 (the "License"); you may not use this file
# except in compliance with the License. You may obtain a copy of the
# License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing
# permissions and limitations under the License.
#
# --------------------------------------------------------------------

# Shared health-check helpers for entrypoint and run_tests
set -euo pipefail

# Fallback log/die in case caller didn't define them
log() { echo "[utils][$(date '+%F %T')] $*"; }
die() { log "ERROR $*"; exit 1; }

wait_port() {
  local host="$1" port="$2" retries="${3:-10}" sleep_sec="${4:-2}"
  local i
  for i in $(seq 1 "${retries}"); do
    if (echo >/dev/tcp/"${host}"/"${port}") >/dev/null 2>&1; then
      return 0
    fi
    sleep "${sleep_sec}"
  done
  return 1
}

check_pxf() {
  if ! curl -sf http://localhost:5888/actuator/health >/dev/null 2>&1; then
    die "PXF actuator health endpoint not responding"
  fi
}

check_cloudberry() {
  # shellcheck disable=SC1091
  source /usr/local/cloudberry-db/cloudberry-env.sh >/dev/null 2>&1 || true
  local port="${PGPORT:-7000}"
  if ! psql -p "${port}" -d postgres -tAc "SELECT 1" >/dev/null 2>&1; then
    die "Cloudberry is not responding on port ${port}"
  fi
}

health_check() {
  log "sanity check PXF and Cloudberry"
  check_pxf
  check_cloudberry
  log "all components healthy: PXF, Cloudberry"
}
