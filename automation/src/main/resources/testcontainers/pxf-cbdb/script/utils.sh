#!/bin/bash
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
