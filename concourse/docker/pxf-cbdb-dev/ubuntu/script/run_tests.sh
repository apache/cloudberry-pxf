#!/bin/bash
set -euo pipefail

# Run automation tests only (assumes build/env already prepared)

# Use a unique var name to avoid clobbering by sourced env scripts
RUN_TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Repo root is five levels up from script dir
REPO_ROOT="$(cd "${RUN_TESTS_DIR}/../../../../.." && pwd)"
cd "${REPO_ROOT}/automation"

# Load centralized env (sets JAVA_BUILD/HADOOP, GPHD_ROOT, PGPORT, etc.)
source "${RUN_TESTS_DIR}/pxf-env.sh"
source "${RUN_TESTS_DIR}/utils.sh"

# Test-related defaults (kept close to test runner)
export GROUP=${GROUP:-smoke}
export RUN_TESTS=${RUN_TESTS:-true}
export PXF_SKIP_TINC=${PXF_SKIP_TINC:-false}
export EXCLUDED_GROUPS=${EXCLUDED_GROUPS:-}
# Keep test data on HDFS between classes to avoid missing inputs
export PXF_TEST_KEEP_DATA=${PXF_TEST_KEEP_DATA:-true}

# Hadoop/Hive/HBase env
export JAVA_HOME="${JAVA_HADOOP}"
export PATH="$JAVA_HOME/bin:$PATH"
source "${GPHD_ROOT}/bin/gphd-env.sh"

# Force local PostgreSQL to IPv4 to avoid ::1 pg_hba misses in proxy tests
export PGHOST=127.0.0.1
# Match historical float string output used by expected files
export PGOPTIONS=${PGOPTIONS:-"-c extra_float_digits=0"}

# Ensure Cloudberry env if present
[ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
[ -f "/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh" ] && source /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh

# Add Hadoop/HBase/Hive bins
export HADOOP_HOME=${HADOOP_HOME:-${GPHD_ROOT}/hadoop}
export HBASE_HOME=${HBASE_HOME:-${GPHD_ROOT}/hbase}
export HIVE_HOME=${HIVE_HOME:-${GPHD_ROOT}/hive}
export PATH="${HADOOP_HOME}/bin:${HBASE_HOME}/bin:${HIVE_HOME}/bin:${PATH}"
export HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-${HADOOP_HOME}/etc/hadoop}
export YARN_CONF_DIR=${YARN_CONF_DIR:-${HADOOP_HOME}/etc/hadoop}
export HBASE_CONF_DIR=${HBASE_CONF_DIR:-${HBASE_HOME}/conf}
export HDFS_URI=${HDFS_URI:-hdfs://localhost:8020}
export HADOOP_OPTS="-Dfs.defaultFS=${HDFS_URI} ${HADOOP_OPTS:-}"
export HADOOP_CLIENT_OPTS="${HADOOP_OPTS}"
export MAVEN_OPTS="-Dfs.defaultFS=${HDFS_URI} ${MAVEN_OPTS:-}"

# Force Hive endpoints to localhost unless explicitly overridden (default sut points to cdw)
export HIVE_HOST=${HIVE_HOST:-localhost}
export HIVE_PORT=${HIVE_PORT:-10000}
export HIVE_SERVER_HOST=${HIVE_SERVER_HOST:-${HIVE_HOST}}
export HIVE_SERVER_PORT=${HIVE_SERVER_PORT:-${HIVE_PORT}}

# Run health check
health_check

# Simple wrappers per group
smoke_test() {
  make GROUP="smoke"
  echo "[run_tests] GROUP=smoke finished"
}

hcatalog_test() {
  make GROUP="hcatalog"
  echo "[run_tests] GROUP=hcatalog finished"
}

hcfs_test() {
  make GROUP="hcfs"
  echo "[run_tests] GROUP=hcfs finished"
}

hdfs_test() {
  make GROUP="hdfs"
  echo "[run_tests] GROUP=hdfs finished"
}

hive_test() {
  make GROUP="hive"
  echo "[run_tests] GROUP=hive finished"
}

main() {
  smoke_test
  hcatalog_test
  hcfs_test
  hdfs_test
  hive_test
}

main "$@"
