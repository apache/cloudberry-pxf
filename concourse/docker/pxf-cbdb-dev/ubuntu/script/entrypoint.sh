#!/bin/bash
set -euo pipefail

log() { echo "[entrypoint][$(date '+%F %T')] $*"; }
die() { log "ERROR $*"; exit 1; }

ROOT_DIR=/home/gpadmin/workspace
REPO_DIR=${ROOT_DIR}/cloudberry-pxf
GPHD_ROOT=${ROOT_DIR}/singlecluster
PXF_SCRIPTS=${REPO_DIR}/concourse/docker/pxf-cbdb-dev/ubuntu/script
source "${PXF_SCRIPTS}/utils.sh"

HADOOP_ROOT=${GPHD_ROOT}/hadoop
HIVE_ROOT=${GPHD_ROOT}/hive
HBASE_ROOT=${GPHD_ROOT}/hbase
ZOOKEEPER_ROOT=${GPHD_ROOT}/zookeeper

JAVA_11_ARM=/usr/lib/jvm/java-11-openjdk-arm64
JAVA_11_AMD=/usr/lib/jvm/java-11-openjdk-amd64
JAVA_8_ARM=/usr/lib/jvm/java-8-openjdk-arm64
JAVA_8_AMD=/usr/lib/jvm/java-8-openjdk-amd64

detect_java_paths() {
  case "$(uname -m)" in
    aarch64|arm64) JAVA_BUILD=${JAVA_11_ARM}; JAVA_HADOOP=${JAVA_8_ARM} ;;
    x86_64|amd64)  JAVA_BUILD=${JAVA_11_AMD}; JAVA_HADOOP=${JAVA_8_AMD} ;;
    *)             JAVA_BUILD=${JAVA_11_ARM}; JAVA_HADOOP=${JAVA_8_ARM} ;;
  esac
  export JAVA_BUILD JAVA_HADOOP
}

setup_locale_and_packages() {
  log "install base packages and locales"
  sudo apt-get update
  sudo apt-get install -y wget lsb-release locales maven unzip openssh-server iproute2 sudo \
    openjdk-11-jre-headless openjdk-8-jre-headless
  sudo locale-gen en_US.UTF-8 ru_RU.CP1251 ru_RU.UTF-8
  sudo update-locale LANG=en_US.UTF-8
  sudo localedef -c -i ru_RU -f CP1251 ru_RU.CP1251 || true
  export LANG=en_US.UTF-8 LANGUAGE=en_US:en LC_ALL=en_US.UTF-8
}

setup_ssh() {
  log "configure ssh"
  sudo ssh-keygen -A
  sudo bash -c 'echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config'
  sudo mkdir -p /etc/ssh/sshd_config.d
  sudo bash -c 'cat >/etc/ssh/sshd_config.d/pxf-automation.conf <<EOF
KexAlgorithms +diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1
HostKeyAlgorithms +ssh-rsa,ssh-dss
PubkeyAcceptedAlgorithms +ssh-rsa,ssh-dss
EOF'
  sudo usermod -a -G sudo gpadmin
  echo "gpadmin:cbdb@123" | sudo chpasswd
  echo "gpadmin        ALL=(ALL)       NOPASSWD: ALL" | sudo tee -a /etc/sudoers >/dev/null
  echo "root           ALL=(ALL)       NOPASSWD: ALL" | sudo tee -a /etc/sudoers >/dev/null

  mkdir -p /home/gpadmin/.ssh
  sudo chown -R gpadmin:gpadmin /home/gpadmin/.ssh
  if [ ! -f /home/gpadmin/.ssh/id_rsa ]; then
    sudo -u gpadmin ssh-keygen -q -t rsa -b 4096 -m PEM -C gpadmin -f /home/gpadmin/.ssh/id_rsa -N ""
  fi
  sudo -u gpadmin bash -lc 'cat /home/gpadmin/.ssh/id_rsa.pub >> /home/gpadmin/.ssh/authorized_keys'
  sudo -u gpadmin chmod 0600 /home/gpadmin/.ssh/authorized_keys
  ssh-keyscan -t rsa mdw cdw localhost 2>/dev/null > /home/gpadmin/.ssh/known_hosts || true
  sudo rm -rf /run/nologin
  sudo mkdir -p /var/run/sshd && sudo chmod 0755 /var/run/sshd
  sudo /usr/sbin/sshd || die "Failed to start sshd"
}

relax_pg_hba() {
  local pg_hba=/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1/pg_hba.conf
  if [ -f "${pg_hba}" ] && ! grep -q "127.0.0.1/32 trust" "${pg_hba}"; then
    cat >> "${pg_hba}" <<'EOF'
host all all 127.0.0.1/32 trust
host all all ::1/128 trust
EOF
    source /usr/local/cloudberry-db/cloudberry-env.sh >/dev/null 2>&1 || true
    GPPORT=${GPPORT:-7000}
    COORDINATOR_DATA_DIRECTORY=/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1
    gpstop -u || true
  fi
}

build_cloudberry() {
  log "build Cloudberry"
  log "cleanup stale gpdemo data and PG locks"
  rm -rf /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs
  rm -f /tmp/.s.PGSQL.700*
  sudo chown -R gpadmin:gpadmin "${ROOT_DIR}" || true
  "${PXF_SCRIPTS}/build_cloudberrry.sh"
}

build_pxf() {
  log "build PXF"
  "${PXF_SCRIPTS}/build_pxf.sh"
}

configure_pxf() {
  log "configure PXF"
  source "${PXF_SCRIPTS}/pxf-env.sh"
  export PATH="$PXF_HOME/bin:$PATH"
  export PXF_JVM_OPTS="-Xmx512m -Xms256m"
  export PXF_HOST=localhost
  echo "JAVA_HOME=${JAVA_BUILD}" >> "$PXF_BASE/conf/pxf-env.sh"
  sed -i 's/# server.address=localhost/server.address=0.0.0.0/' "$PXF_BASE/conf/pxf-application.properties"
  echo -e "\npxf.profile.dynamic.regex=test:.*" >> "$PXF_BASE/conf/pxf-application.properties"
  cp -v "$PXF_HOME"/templates/{hdfs,mapred,yarn,core,hbase,hive}-site.xml "$PXF_BASE/servers/default"
  # Some templates do not ship pxf-site.xml per server; create a minimal one when missing.
  for server_dir in "$PXF_BASE/servers/default" "$PXF_BASE/servers/default-no-impersonation"; do
    if [ ! -d "$server_dir" ]; then
      cp -r "$PXF_BASE/servers/default" "$server_dir"
    fi
    if [ ! -f "$server_dir/pxf-site.xml" ]; then
      cat > "$server_dir/pxf-site.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
</configuration>
XML
    fi
  done
  if ! grep -q "pxf.service.user.name" "$PXF_BASE/servers/default-no-impersonation/pxf-site.xml"; then
    sed -i 's#</configuration>#  <property>\n    <name>pxf.service.user.name</name>\n    <value>foobar</value>\n  </property>\n  <property>\n    <name>pxf.service.user.impersonation</name>\n    <value>false</value>\n  </property>\n</configuration>#' "$PXF_BASE/servers/default-no-impersonation/pxf-site.xml"
  fi
}

prepare_hadoop_stack() {
  log "prepare Hadoop/Hive/HBase stack"
  export JAVA_HOME="${JAVA_HADOOP}"
  export PATH="$JAVA_HOME/bin:$HADOOP_ROOT/bin:$HIVE_ROOT/bin:$PATH"
  source "${GPHD_ROOT}/bin/gphd-env.sh"
  cd "${REPO_DIR}/automation"
  make symlink_pxf_jars
  cp /home/gpadmin/automation_tmp_lib/pxf-hbase.jar "$GPHD_ROOT/hbase/lib/" || true
  # Ensure HBase sees PXF comparator classes even if automation_tmp_lib was empty
  if [ ! -f "${GPHD_ROOT}/hbase/lib/pxf-hbase.jar" ]; then
    pxf_app=$(ls -1v /usr/local/pxf/application/pxf-app-*.jar | grep -v 'plain' | tail -n 1)
    unzip -qq -j "${pxf_app}" 'BOOT-INF/lib/pxf-hbase-*.jar' -d "${GPHD_ROOT}/hbase/lib/"
  fi
  # clean stale Hive locks and stop any leftover services to avoid start failures
  rm -f "${GPHD_ROOT}/storage/hive/metastore_db/"*.lck 2>/dev/null || true
  rm -f "${GPHD_ROOT}/storage/pids"/hive-*.pid 2>/dev/null || true
  if pgrep -f HiveMetaStore >/dev/null 2>&1; then
    "${GPHD_ROOT}/bin/hive-service.sh" metastore stop || true
  fi
  if pgrep -f HiveServer2 >/dev/null 2>&1; then
    "${GPHD_ROOT}/bin/hive-service.sh" hiveserver2 stop || true
  fi
  if [ ! -d "${GPHD_ROOT}/storage/hadoop/dfs/name/current" ]; then
    ${GPHD_ROOT}/bin/init-gphd.sh
  fi
  if ! ${GPHD_ROOT}/bin/start-gphd.sh; then
    log "start-gphd.sh returned non-zero (services may already be running), continue"
  fi
  if ! ${GPHD_ROOT}/bin/start-zookeeper.sh; then
    log "start-zookeeper.sh returned non-zero (may already be running)"
  fi
  # ensure HBase is up
  if ! ${GPHD_ROOT}/bin/start-hbase.sh; then
    log "start-hbase.sh returned non-zero (services may already be running), continue"
  fi
  start_hive_services
}

start_hive_services() {
  log "start Hive metastore and HiveServer2 (NOSASL)"
  export JAVA_HOME="${JAVA_HADOOP}"
  export PATH="${JAVA_HOME}/bin:${HIVE_ROOT}/bin:${HADOOP_ROOT}/bin:${PATH}"
  export HIVE_HOME="${HIVE_ROOT}"
  export HADOOP_HOME="${HADOOP_ROOT}"
  # bump HS2 heap to reduce Tez OOMs during tests
  export HADOOP_HEAPSIZE=${HADOOP_HEAPSIZE:-1024}
  export HADOOP_CLIENT_OPTS="-Xmx${HADOOP_HEAPSIZE}m -Xms512m ${HADOOP_CLIENT_OPTS:-}"

  # ensure clean state
  pkill -f HiveServer2 || true
  pkill -f HiveMetaStore || true
  rm -rf "${GPHD_ROOT}/storage/hive/metastore_db" 2>/dev/null || true
  rm -f "${GPHD_ROOT}/storage/logs/derby.log" 2>/dev/null || true
  rm -f "${GPHD_ROOT}/storage/pids"/hive-*.pid 2>/dev/null || true

  # always re-init Derby schema to avoid stale locks; if the DB already exists, wipe and retry once
  if ! PATH="${HIVE_ROOT}/bin:${HADOOP_ROOT}/bin:${PATH}" \
        JAVA_HOME="${JAVA_HADOOP}" \
        schematool -dbType derby -initSchema -verbose; then
    log "schematool failed on first attempt, cleaning metastore_db and retrying"
    rm -rf "${GPHD_ROOT}/storage/hive/metastore_db" 2>/dev/null || true
    rm -f "${GPHD_ROOT}/storage/logs/derby.log" 2>/dev/null || true
    PATH="${HIVE_ROOT}/bin:${HADOOP_ROOT}/bin:${PATH}" \
      JAVA_HOME="${JAVA_HADOOP}" \
      schematool -dbType derby -initSchema -verbose || die "schematool initSchema failed"
  fi

  # start metastore
  HIVE_OPTS="--hiveconf javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=${GPHD_ROOT}/storage/hive/metastore_db;create=true" \
    "${GPHD_ROOT}/bin/hive-service.sh" metastore start

  # wait for 9083
  local ok=false
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if bash -c ">/dev/tcp/localhost/9083" >/dev/null 2>&1; then
      ok=true
      break
    fi
    sleep 2
  done
  if [ "${ok}" != "true" ]; then
    die "Hive metastore not reachable on 9083"
  fi

  # start HS2 with NOSASL
  HIVE_OPTS="--hiveconf hive.server2.authentication=NOSASL --hiveconf hive.metastore.uris=thrift://localhost:9083 --hiveconf javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=${GPHD_ROOT}/storage/hive/metastore_db;create=true" \
    "${GPHD_ROOT}/bin/hive-service.sh" hiveserver2 start
}

run_tests() {
  if [ "${RUN_TESTS:-true}" != "true" ]; then
    log "RUN_TESTS=false, skipping automation run"
    return
  fi
  log "running tests group=${GROUP:-}"
  "${PXF_SCRIPTS}/run_tests.sh" "${GROUP:-}"
}

main() {
  detect_java_paths
  setup_locale_and_packages
  setup_ssh
  build_cloudberry
  relax_pg_hba
  build_pxf
  configure_pxf
  prepare_hadoop_stack
  health_check
  #run_tests
  log "entrypoint finished; keeping container alive"
  tail -f /dev/null
}

main "$@"
