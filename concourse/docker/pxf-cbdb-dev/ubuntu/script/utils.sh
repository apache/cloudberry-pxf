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

check_jvm_procs() {
  if command -v jps >/dev/null 2>&1; then
    jps_out=$(jps)
  else
    jps_out=$(ps -eo cmd | grep java)
  fi
  echo "$jps_out"
  echo "$jps_out" | grep -q NameNode || die "NameNode not running"
  echo "$jps_out" | grep -q DataNode || die "DataNode not running"
}

check_hbase() {
  local hbase_host="${HBASE_HOST:-$(hostname -I | awk '{print $1}')}"
  hbase_host=${hbase_host:-127.0.0.1}

  if ! echo "$jps_out" | grep -q HMaster && ! pgrep -f HMaster >/dev/null 2>&1; then
    die "HBase HMaster not running"
  fi

  if ! echo "$jps_out" | grep -q HRegionServer && ! pgrep -f HRegionServer >/dev/null 2>&1; then
    die "HBase RegionServer not running"
  fi

  local hbase_ok=true
  if ! printf "status 'simple'\n" | "${HBASE_ROOT}/bin/hbase" shell -n >/tmp/hbase_status.log 2>&1; then
    hbase_ok=false
  fi
  if ! (echo >/dev/tcp/"${hbase_host}"/16000) >/dev/null 2>&1; then
    hbase_ok=false
  fi
  if [ "${hbase_ok}" != "true" ]; then
    [ -f /tmp/hbase_status.log ] && cat /tmp/hbase_status.log
    die "HBase health check failed (status or port 16000 on ${hbase_host})"
  fi
}

check_hdfs() {
  hdfs dfs -test -d / || die "HDFS root not accessible"
}

check_hive() {
  wait_port localhost 9083 10 2 || die "Hive metastore not reachable on 9083"
  wait_port "${HIVE_HOST:-localhost}" "${HIVE_PORT:-10000}" 10 2 || die "HiveServer2 port not reachable"

  local beeline_ok=true
  if command -v beeline >/dev/null 2>&1; then
    beeline_ok=false
    for _ in 1 2 3 4 5; do
      if beeline -u "jdbc:hive2://${HIVE_HOST:-localhost}:${HIVE_PORT:-10000}/default;auth=noSasl" \
          -n "${HIVE_USER:-gpadmin}" -p "${HIVE_PASSWORD:-gpadmin}" \
          -e "select 1" >/tmp/hive_health.log 2>&1; then
        beeline_ok=true
        break
      fi
      sleep 2
    done
  fi

  if [ "${beeline_ok}" != "true" ]; then
    [ -f /tmp/hive_health.log ] && cat /tmp/hive_health.log
    die "HiveServer2 query failed"
  fi
}

check_pxf() {
  if ! curl -sf http://localhost:5888/actuator/health >/dev/null 2>&1; then
    die "PXF actuator health endpoint not responding"
  fi
}

health_check() {
  log "sanity check Hadoop/Hive/HBase/PXF"
  GPHD_ROOT=${GPHD_ROOT:-/home/gpadmin/workspace/singlecluster}
  HADOOP_ROOT=${HADOOP_ROOT:-${GPHD_ROOT}/hadoop}
  HBASE_ROOT=${HBASE_ROOT:-${GPHD_ROOT}/hbase}
  HIVE_ROOT=${HIVE_ROOT:-${GPHD_ROOT}/hive}
  JAVA_HADOOP=${JAVA_HADOOP:-/usr/lib/jvm/java-8-openjdk-amd64}

  export JAVA_HOME="${JAVA_HADOOP}"
  export PATH="$JAVA_HOME/bin:$HADOOP_ROOT/bin:$HIVE_ROOT/bin:$HBASE_ROOT/bin:$PATH"
  [ -f "${GPHD_ROOT}/bin/gphd-env.sh" ] && source "${GPHD_ROOT}/bin/gphd-env.sh"

  check_jvm_procs
  check_hbase
  check_hdfs
  check_hive
  check_pxf
  log "all components healthy: HDFS/HBase/Hive/PXF"
}
