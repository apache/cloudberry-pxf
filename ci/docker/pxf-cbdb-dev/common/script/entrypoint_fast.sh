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
# Fast entrypoint for test-ready Docker images.
# Skips package installs, SSH setup, Cloudberry install, and demo cluster
# creation (all pre-baked into the image). Only runs:
#   1. Start sshd
#   2. Start Cloudberry cluster (gpstart -a)
#   3. Build PXF (dynamic, changes per PR)
#   4. Configure PXF
#   5. Start Hadoop/Hive/HBase services
#   6. Start MinIO
#   7. Health check
# --------------------------------------------------------------------
set -euo pipefail

export TZ=UTC

log() { echo "[entrypoint-fast][$(date '+%F %T')] $*"; }
die() { log "ERROR $*"; exit 1; }

ROOT_DIR=/home/gpadmin/workspace
REPO_DIR=${ROOT_DIR}/cloudberry-pxf
GPHD_ROOT=${ROOT_DIR}/singlecluster
COMMON_SCRIPTS=${REPO_DIR}/ci/docker/pxf-cbdb-dev/common/script
source "${COMMON_SCRIPTS}/utils.sh"

HADOOP_ROOT=${GPHD_ROOT}/hadoop
HIVE_ROOT=${GPHD_ROOT}/hive
HBASE_ROOT=${GPHD_ROOT}/hbase
ZOOKEEPER_ROOT=${GPHD_ROOT}/zookeeper

# Fallback: if not a test-ready image, use the full entrypoint
if [ ! -f /etc/pxf-test-ready ]; then
  log "Not a test-ready image, falling back to full entrypoint"
  exec "${COMMON_SCRIPTS}/entrypoint.sh" "$@"
fi

# ---- OS detection ----
if command -v apt-get >/dev/null 2>&1; then
  OS_FAMILY="deb"
else
  OS_FAMILY="rpm"
fi

detect_java_paths() {
  if [ "$OS_FAMILY" = "deb" ]; then
    case "$(uname -m)" in
      aarch64|arm64) JAVA_BUILD=/usr/lib/jvm/java-11-openjdk-arm64;  JAVA_HADOOP=/usr/lib/jvm/java-8-openjdk-arm64 ;;
      *)             JAVA_BUILD=/usr/lib/jvm/java-11-openjdk-amd64;  JAVA_HADOOP=/usr/lib/jvm/java-8-openjdk-amd64 ;;
    esac
  else
    JAVA_BUILD=/usr/lib/jvm/java-11-openjdk
    JAVA_HADOOP=/usr/lib/jvm/java-1.8.0-openjdk
  fi
  export JAVA_BUILD JAVA_HADOOP
}

start_sshd() {
  log "configuring and starting sshd"
  # Rocky 9 crypto-policies (pre-baked but re-apply to be safe)
  if [ "$OS_FAMILY" = "rpm" ] && command -v update-crypto-policies >/dev/null 2>&1; then
    sudo update-crypto-policies --set LEGACY 2>/dev/null || true
  fi
  sudo ssh-keygen -A 2>/dev/null || true
  # Ensure password auth is enabled (use sed to guarantee first-match wins)
  sudo sed -i 's/^#*PasswordAuthentication .*/PasswordAuthentication yes/' /etc/ssh/sshd_config
  if ! grep -q '^PasswordAuthentication yes' /etc/ssh/sshd_config; then
    echo "PasswordAuthentication yes" | sudo tee -a /etc/ssh/sshd_config >/dev/null
  fi
  # Re-set password in case chpasswd didn't persist from Docker build
  echo "gpadmin:cbdb@123" | sudo chpasswd
  sudo rm -rf /run/nologin
  sudo mkdir -p /var/run/sshd && sudo chmod 0755 /var/run/sshd
  sudo mkdir -p /var/empty/sshd && sudo chmod 0755 /var/empty/sshd
  id sshd &>/dev/null || sudo useradd -r -d /var/empty/sshd -s /sbin/nologin sshd 2>/dev/null || true
  sudo /usr/sbin/sshd -E /tmp/sshd.log || die "Failed to start sshd"
  sleep 1
  ssh-keyscan -t rsa,ecdsa,ed25519 mdw cdw localhost 127.0.0.1 2>/dev/null > /home/gpadmin/.ssh/known_hosts || true
  if ! ss -tlnp | grep -q ':22 '; then
    log "WARN: sshd not on port 22, trying foreground mode"
    sudo /usr/sbin/sshd -D -e &
    sleep 1
  fi
  log "sshd is running on port 22"
}

start_cloudberry() {
  log "starting Cloudberry cluster"
  source /usr/local/cloudberry-db/cloudberry-env.sh
  # Demo cluster cannot be pre-baked in Docker image (hostname mismatch
  # between build-time 'buildkitsandbox' and runtime 'mdw').
  # Create it at first run; subsequent runs just gpstart.
  if [ -f ~/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh ]; then
    source ~/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
    gpstart -a || {
      log "gpstart failed, re-creating demo cluster"
      rm -rf ~/workspace/cloudberry/gpAux/gpdemo/datadirs
      rm -f /tmp/.s.PGSQL.700*
      make create-demo-cluster -C ~/workspace/cloudberry
      source ~/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
    }
  else
    log "demo cluster not found, creating..."
    rm -f /tmp/.s.PGSQL.700*
    make create-demo-cluster -C ~/workspace/cloudberry || {
      log "create-demo-cluster failed, trying manual setup"
      cd ~/workspace/cloudberry
      ./configure --prefix=/usr/local/cloudberry-db --enable-debug --with-perl --with-python --with-libxml --enable-depend
      make create-demo-cluster
    }
    source ~/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
  fi
  psql -P pager=off template1 -c 'SELECT * from gp_segment_configuration' || true
  psql template1 -c 'SELECT version()' || true
}

relax_pg_hba() {
  local pg_hba=/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1/pg_hba.conf
  if [ -f "${pg_hba}" ] && ! grep -q "127.0.0.1/32 trust" "${pg_hba}"; then
    cat >> "${pg_hba}" <<'EOF'
host all all 127.0.0.1/32 trust
host all all ::1/128 trust
EOF
    source /usr/local/cloudberry-db/cloudberry-env.sh >/dev/null 2>&1 || true
    gpstop -u || true
  fi
}

build_pxf() {
  log "build PXF"
  "${COMMON_SCRIPTS}/build_pxf.sh"
}

configure_pxf() {
  log "configure PXF"
  source "${COMMON_SCRIPTS}/pxf-env.sh"
  export PATH="$PXF_HOME/bin:$PATH"
  export PXF_JVM_OPTS="-Xmx512m -Xms256m -Duser.timezone=UTC"
  export PXF_HOST=localhost
  # Persist settings into pxf-env.sh so they survive `pxf restart`
  cat >> "$PXF_BASE/conf/pxf-env.sh" <<EOF
export JAVA_HOME=${JAVA_BUILD}
export PXF_JVM_OPTS="-Xmx512m -Xms256m -Duser.timezone=UTC"
export TZ=UTC
EOF
  sed -i 's/# server.address=localhost/server.address=0.0.0.0/' "$PXF_BASE/conf/pxf-application.properties"
  echo -e "\npxf.profile.dynamic.regex=test:.*" >> "$PXF_BASE/conf/pxf-application.properties"
  cp -v "$PXF_HOME"/templates/{hdfs,mapred,yarn,core,hbase,hive}-site.xml "$PXF_BASE/servers/default"
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

  # PXF profiles
  cat > "$PXF_BASE/conf/pxf-profiles.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<profiles>
    <profile>
        <name>pxf:parquet</name>
        <description>Profile for reading and writing Parquet files</description>
        <plugins>
            <fragmenter>org.apache.cloudberry.pxf.plugins.hdfs.HdfsDataFragmenter</fragmenter>
            <accessor>org.apache.cloudberry.pxf.plugins.hdfs.ParquetFileAccessor</accessor>
            <resolver>org.apache.cloudberry.pxf.plugins.hdfs.ParquetResolver</resolver>
        </plugins>
    </profile>
    <profile>
        <name>test:text</name>
        <description>Test profile for text files</description>
        <plugins>
            <fragmenter>org.apache.cloudberry.pxf.plugins.hdfs.HdfsDataFragmenter</fragmenter>
            <accessor>org.apache.cloudberry.pxf.plugins.hdfs.LineBreakAccessor</accessor>
            <resolver>org.apache.cloudberry.pxf.plugins.hdfs.StringPassResolver</resolver>
        </plugins>
    </profile>
</profiles>
EOF
  cp "$PXF_BASE/conf/pxf-profiles.xml" "$PXF_HOME/conf/pxf-profiles.xml"

  # S3/MinIO configuration
  mkdir -p "$PXF_BASE/servers/s3" "$PXF_HOME/servers/s3"
  for s3_site in "$PXF_BASE/servers/s3/s3-site.xml" "$PXF_BASE/servers/default/s3-site.xml" "$PXF_HOME/servers/s3/s3-site.xml"; do
    mkdir -p "$(dirname "$s3_site")"
    cat > "$s3_site" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property><name>fs.s3a.endpoint</name><value>http://localhost:9000</value></property>
    <property><name>fs.s3a.access.key</name><value>admin</value></property>
    <property><name>fs.s3a.secret.key</name><value>password</value></property>
    <property><name>fs.s3a.path.style.access</name><value>true</value></property>
    <property><name>fs.s3a.connection.ssl.enabled</name><value>false</value></property>
    <property><name>fs.s3a.impl</name><value>org.apache.hadoop.fs.s3a.S3AFileSystem</value></property>
    <property><name>fs.s3a.aws.credentials.provider</name><value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value></property>
</configuration>
EOF
  done
  mkdir -p /home/gpadmin/.aws/
  cat > "/home/gpadmin/.aws/credentials" <<'EOF'
[default]
aws_access_key_id = admin
aws_secret_access_key = password
EOF
}

prepare_hadoop_stack() {
  log "prepare Hadoop/Hive/HBase stack"
  export JAVA_HOME="${JAVA_HADOOP}"
  export PATH="$JAVA_HOME/bin:$HADOOP_ROOT/bin:$HIVE_ROOT/bin:$PATH"
  source "${GPHD_ROOT}/bin/gphd-env.sh"
  cd "${REPO_DIR}/automation"
  make symlink_pxf_jars
  cp /home/gpadmin/automation_tmp_lib/pxf-hbase.jar "$GPHD_ROOT/hbase/lib/" || true
  if [ ! -f "${GPHD_ROOT}/hbase/lib/pxf-hbase.jar" ]; then
    pxf_app=$(ls -1v /usr/local/pxf/application/pxf-app-*.jar | grep -v 'plain' | tail -n 1)
    unzip -qq -j "${pxf_app}" 'BOOT-INF/lib/pxf-hbase-*.jar' -d "${GPHD_ROOT}/hbase/lib/"
  fi
  rm -f "${GPHD_ROOT}/storage/hive/metastore_db/"*.lck 2>/dev/null || true
  rm -f "${GPHD_ROOT}/storage/pids"/hive-*.pid 2>/dev/null || true

  # Namenode already formatted in image; just ensure ports are free and start
  log "ensuring DataNode ports are free..."
  for port in 50010 50020 50075 50080; do
    fuser -k ${port}/tcp 2>/dev/null || true
  done
  sleep 1
  log "starting HDFS/YARN/HBase via start-gphd.sh..."
  if ! ${GPHD_ROOT}/bin/start-gphd.sh 2>&1; then
    log "start-gphd.sh returned non-zero, continue"
  fi
  # Reuse wait_for_datanode from entrypoint.sh via sourced utils or inline
  log "waiting for HDFS DataNode..."
  for _try in $(seq 1 45); do
    if hdfs dfsadmin -report 2>/dev/null | grep -q "Live datanodes.*[1-9]"; then
      log "HDFS DataNode is available"
      break
    fi
    sleep 2
  done
  if ! ${GPHD_ROOT}/bin/start-zookeeper.sh; then
    log "start-zookeeper.sh returned non-zero"
  fi
  if ! ${GPHD_ROOT}/bin/start-hbase.sh; then
    log "start-hbase.sh returned non-zero"
  fi
  # Wait for HBase RegionServer
  for _i in $(seq 1 60); do
    if pgrep -f HRegionServer >/dev/null 2>&1; then
      log "HBase RegionServer is running"
      break
    fi
    sleep 1
  done
  start_hive_services
}

start_hive_services() {
  log "start Hive metastore and HiveServer2 (NOSASL)"
  export JAVA_HOME="${JAVA_HADOOP}"
  export PATH="${JAVA_HOME}/bin:${HIVE_ROOT}/bin:${HADOOP_ROOT}/bin:${PATH}"
  export HIVE_HOME="${HIVE_ROOT}"
  export HADOOP_HOME="${HADOOP_ROOT}"
  local tez_root="${TEZ_ROOT:-${GPHD_ROOT}/tez}"
  export HADOOP_HEAPSIZE=${HADOOP_HEAPSIZE:-1024}
  export HADOOP_CLIENT_OPTS="-Xmx${HADOOP_HEAPSIZE}m -Xms512m ${HADOOP_CLIENT_OPTS:-}"

  "${HADOOP_ROOT}/bin/hadoop" fs -mkdir -p /apps/tez
  "${HADOOP_ROOT}/bin/hadoop" fs -copyFromLocal -f "${tez_root}"/* /apps/tez

  pkill -f HiveServer2 || true
  pkill -f HiveMetaStore || true
  rm -rf "${GPHD_ROOT}/storage/hive/metastore_db" 2>/dev/null || true
  rm -f "${GPHD_ROOT}/storage/logs/derby.log" 2>/dev/null || true
  rm -f "${GPHD_ROOT}/storage/pids"/hive-*.pid 2>/dev/null || true

  if ! PATH="${HIVE_ROOT}/bin:${HADOOP_ROOT}/bin:${PATH}" \
        JAVA_HOME="${JAVA_HADOOP}" \
        schematool -dbType derby -initSchema -verbose; then
    rm -rf "${GPHD_ROOT}/storage/hive/metastore_db" 2>/dev/null || true
    PATH="${HIVE_ROOT}/bin:${HADOOP_ROOT}/bin:${PATH}" \
      JAVA_HOME="${JAVA_HADOOP}" \
      schematool -dbType derby -initSchema -verbose || die "schematool initSchema failed"
  fi

  HIVE_OPTS="--hiveconf javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=${GPHD_ROOT}/storage/hive/metastore_db;create=true" \
    "${GPHD_ROOT}/bin/hive-service.sh" metastore start

  local ok=false
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if bash -c ">/dev/tcp/localhost/9083" >/dev/null 2>&1; then ok=true; break; fi
    sleep 2
  done
  [ "${ok}" != "true" ] && die "Hive metastore not reachable on 9083"

  HIVE_OPTS="--hiveconf hive.server2.authentication=NOSASL --hiveconf hive.metastore.uris=thrift://localhost:9083 --hiveconf javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=${GPHD_ROOT}/storage/hive/metastore_db;create=true" \
    "${GPHD_ROOT}/bin/hive-service.sh" hiveserver2 start

  log "waiting for HiveServer2 on port 10000..."
  for i in {1..60}; do
    if ss -ln | grep -q ":10000 " || lsof -i :10000 >/dev/null 2>&1; then
      if echo "SHOW DATABASES;" | beeline -u "jdbc:hive2://localhost:10000/default" --silent=true >/dev/null 2>&1; then
        log "HiveServer2 is ready"
        break
      fi
    fi
    [ $i -eq 60 ] && log "WARN: HiveServer2 may not be fully ready"
    sleep 1
  done
}

deploy_minio() {
  log "deploying MinIO"
  bash "${COMMON_SCRIPTS}/start_minio.bash"
}

main() {
  detect_java_paths
  start_sshd
  start_cloudberry
  relax_pg_hba
  build_pxf
  configure_pxf
  prepare_hadoop_stack
  deploy_minio
  health_check
  log "entrypoint_fast finished; environment ready for tests"
}

main "$@"
