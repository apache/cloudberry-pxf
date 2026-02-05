#!/bin/bash
set -euo pipefail

log() { echo "[entrypoint][$(date '+%F %T')] $*"; }
die() { log "ERROR $*"; exit 1; }

ROOT_DIR=/home/gpadmin/workspace
REPO_DIR=${ROOT_DIR}/cloudberry-pxf
GPHD_ROOT=${ROOT_DIR}/singlecluster
PXF_SCRIPTS=${REPO_DIR}/ci/docker/pxf-cbdb-dev/ubuntu/script
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

install_cloudberry_from_deb() {
  log "installing Cloudberry from .deb package"
  local deb_file=$(find /tmp -name "apache-cloudberry-db*.deb" 2>/dev/null | head -1)
  if [ -z "$deb_file" ]; then
    die "No .deb package found in /tmp"
  fi

  # Install sudo & git
  sudo apt update && sudo apt install -y sudo git

  # Required configuration
  ## Add Cloudberry environment setup to .bashrc
  echo -e '\n# Add Cloudberry entries
  if [ -f /usr/local/cloudberry-db/cloudberry-env.sh ]; then
    source /usr/local/cloudberry-db/cloudberry-env.sh
  fi
  ## US English with UTF-8 character encoding
  export LANG=en_US.UTF-8
  ' >> /home/gpadmin/.bashrc
  ## Set up SSH for passwordless access
  mkdir -p /home/gpadmin/.ssh
  if [ ! -f /home/gpadmin/.ssh/id_rsa ]; then
    ssh-keygen -t rsa -b 2048 -C 'apache-cloudberry-dev' -f /home/gpadmin/.ssh/id_rsa -N ""
  fi
  cat /home/gpadmin/.ssh/id_rsa.pub >> /home/gpadmin/.ssh/authorized_keys
  ## Set proper SSH directory permissions
  chmod 700 /home/gpadmin/.ssh
  chmod 600 /home/gpadmin/.ssh/authorized_keys
  chmod 644 /home/gpadmin/.ssh/id_rsa.pub

# Configure system settings
sudo tee /etc/security/limits.d/90-db-limits.conf << 'EOF'
## Core dump file size limits for gpadmin
gpadmin soft core unlimited
gpadmin hard core unlimited
## Open file limits for gpadmin
gpadmin soft nofile 524288
gpadmin hard nofile 524288
## Process limits for gpadmin
gpadmin soft nproc 131072
gpadmin hard nproc 131072
EOF

  # Verify resource limits
  ulimit -a

  # Install basic system packages
  sudo apt update
  sudo apt install -y bison \
    bzip2 \
    cmake \
    curl \
    flex \
    gcc \
    g++ \
    iproute2 \
    iputils-ping \
    language-pack-en \
    locales \
    libapr1-dev \
    libbz2-dev \
    libcurl4-gnutls-dev \
    libevent-dev \
    libkrb5-dev \
    libipc-run-perl \
    libldap2-dev \
    libpam0g-dev \
    libprotobuf-dev \
    libreadline-dev \
    libssl-dev \
    libuv1-dev \
    liblz4-dev \
    libxerces-c-dev \
    libxml2-dev \
    libyaml-dev \
    libzstd-dev \
    libperl-dev \
    make \
    pkg-config \
    protobuf-compiler \
    python3-dev \
    python3-pip \
    python3-setuptools \
    rsync \
    libsnappy-dev


  # Continue as gpadmin user


  # Prepare the build environment for Apache Cloudberry
  sudo rm -rf /usr/local/cloudberry-db
  sudo chmod a+w /usr/local
  mkdir -p /usr/local/cloudberry-db
  sudo chown -R gpadmin:gpadmin /usr/local/cloudberry-db

  sudo dpkg -i "$deb_file" || sudo apt-get install -f -y
  log "Cloudberry installed from $deb_file"
  
  # Initialize and start Cloudberry cluster
  source /usr/local/cloudberry-db/cloudberry-env.sh
  make create-demo-cluster -C ~/workspace/cloudberry || {
    log "create-demo-cluster failed, trying manual setup"
    cd ~/workspace/cloudberry
    ./configure --prefix=/usr/local/cloudberry-db --enable-debug --with-perl --with-python --with-libxml --enable-depend
    make create-demo-cluster
  }
  source ~/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
  psql -P pager=off template1 -c 'SELECT * from gp_segment_configuration'
  psql template1 -c 'SELECT version()'
}

build_cloudberry() {
  log "building Cloudberry from source"
  log "cleanup stale gpdemo data and PG locks"
  rm -rf /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs
  rm -f /tmp/.s.PGSQL.700*
  find "${ROOT_DIR}" -not -path '*/.git/*' -exec sudo chown gpadmin:gpadmin {} + 2>/dev/null || true
  "${PXF_SCRIPTS}/build_cloudberrry.sh"
}

setup_cloudberry() {
  # Auto-detect: if deb exists, install it; otherwise build from source
  if [ -f /tmp/apache-cloudberry-db*.deb ]; then
    log "detected .deb package, using fast install"
    install_cloudberry_from_deb
  elif [ "${CLOUDBERRY_USE_DEB:-}" = "true" ]; then
    die "CLOUDBERRY_USE_DEB=true but no .deb found in /tmp"
  else
    log "no .deb found, building from source (local dev mode)"
    build_cloudberry
  fi
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

  # Configure pxf-profiles.xml for Parquet and test profiles
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

  cat > "$PXF_HOME/conf/pxf-profiles.xml" <<'EOF'
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

  # Configure S3 settings
  mkdir -p "$PXF_BASE/servers/s3" "$PXF_HOME/servers/s3"
  
  for s3_site in "$PXF_BASE/servers/s3/s3-site.xml" "$PXF_BASE/servers/default/s3-site.xml" "$PXF_HOME/servers/s3/s3-site.xml"; do
    mkdir -p "$(dirname "$s3_site")"
    cat > "$s3_site" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property>
        <name>fs.s3a.endpoint</name>
        <value>http://localhost:9000</value>
    </property>
    <property>
        <name>fs.s3a.access.key</name>
        <value>admin</value>
    </property>
    <property>
        <name>fs.s3a.secret.key</name>
        <value>password</value>
    </property>
    <property>
        <name>fs.s3a.path.style.access</name>
        <value>true</value>
    </property>
    <property>
        <name>fs.s3a.connection.ssl.enabled</name>
        <value>false</value>
    </property>
    <property>
        <name>fs.s3a.impl</name>
        <value>org.apache.hadoop.fs.s3a.S3AFileSystem</value>
    </property>
    <property>
        <name>fs.s3a.aws.credentials.provider</name>
        <value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value>
    </property>
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
  local tez_root="${TEZ_ROOT:-${GPHD_ROOT}/tez}"
  # bump HS2 heap to reduce Tez OOMs during tests
  export HADOOP_HEAPSIZE=${HADOOP_HEAPSIZE:-1024}
  export HADOOP_CLIENT_OPTS="-Xmx${HADOOP_HEAPSIZE}m -Xms512m ${HADOOP_CLIENT_OPTS:-}"

  # ensure Tez libs are available on HDFS for hive.execution.engine=tez
  "${HADOOP_ROOT}/bin/hadoop" fs -mkdir -p /apps/tez
  "${HADOOP_ROOT}/bin/hadoop" fs -copyFromLocal -f "${tez_root}"/* /apps/tez

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

  # wait for HiveServer2 to be ready
  log "waiting for HiveServer2 to start on port 10000..."
  for i in {1..60}; do
    if ss -ln | grep -q ":10000 " || lsof -i :10000 >/dev/null 2>&1; then
      log "HiveServer2 port is listening, testing connection..."
      if echo "SHOW DATABASES;" | beeline -u "jdbc:hive2://localhost:10000/default" --silent=true >/dev/null 2>&1; then
        log "HiveServer2 is ready and accessible"
        break
      else
        log "HiveServer2 port is up but not ready for connections, waiting... (attempt $i/60)"
      fi
    else
      log "HiveServer2 port 10000 not yet listening... (attempt $i/60)"
    fi
    if [ $i -eq 60 ]; then
      log "ERROR: HiveServer2 failed to start properly after 60 seconds"
      log "Checking HiveServer2 process:"
      pgrep -f HiveServer2 || log "No HiveServer2 process found"
      log "Checking port 10000:"
      ss -ln | grep ":10000" || lsof -i :10000 || log "Port 10000 not listening"
      log "HiveServer2 logs:"
      tail -20 "${GPHD_ROOT}/storage/logs/hive-gpadmin-hiveserver2-mdw.out" 2>/dev/null || log "No HiveServer2 log found"
      exit 1
    fi
    sleep 1
  done
}

deploy_minio() {
  log "deploying MinIO"
  bash "${PXF_SCRIPTS}/start_minio.bash"
}

main() {
  detect_java_paths
  setup_locale_and_packages
  setup_ssh
  setup_cloudberry
  relax_pg_hba
  build_pxf
  configure_pxf
  prepare_hadoop_stack
  deploy_minio
  health_check
  log "entrypoint finished; environment ready for tests"
}

main "$@"
