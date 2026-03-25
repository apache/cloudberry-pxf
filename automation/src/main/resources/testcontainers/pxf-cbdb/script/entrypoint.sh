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
set -euo pipefail
set -x

log() { echo "[entrypoint][$(date '+%F %T')] $*"; }
die() { log "ERROR $*"; exit 1; }

ROOT_DIR=/home/gpadmin/workspace
REPO_DIR=${ROOT_DIR}/cloudberry-pxf
PXF_SCRIPTS=${REPO_DIR}/automation/src/main/resources/testcontainers/pxf-cbdb/script
source "${PXF_SCRIPTS}/utils.sh"

detect_java_paths() {
  case "$(uname -m)" in
    aarch64|arm64) JAVA_BUILD=/usr/lib/jvm/java-11-openjdk-arm64 ;;
    x86_64|amd64)  JAVA_BUILD=/usr/lib/jvm/java-11-openjdk-amd64 ;;
    *)             JAVA_BUILD=/usr/lib/jvm/java-11-openjdk-amd64 ;;
  esac
  export JAVA_BUILD
}

setup_locale_and_packages() {
  log "install locales"
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
host all all ::1/128 trust
host all all 0.0.0.0/0 trust
EOF
    source /usr/local/cloudberry-db/cloudberry-env.sh >/dev/null 2>&1 || true
    GPPORT=${GPPORT:-7000}
    COORDINATOR_DATA_DIRECTORY=/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1
    gpstop -u || true
  fi
}

setup_cloudberry() {
  log "cleanup stale gpdemo data and PG locks"
  rm -rf /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs
  rm -f /tmp/.s.PGSQL.700*
}

create_demo_cluster() {
  log "set up Cloudberry demo cluster"
  source /usr/local/cloudberry-db/cloudberry-env.sh
  make create-demo-cluster -C ~/workspace/cloudberry
  source ~/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
  psql -P pager=off template1 -c 'SELECT * from gp_segment_configuration'
  psql template1 -c 'SELECT version()'
}

build_pxf() {
  log "build PXF"
  "${PXF_SCRIPTS}/build_pxf.sh"
}

# pxf_regress is copied from the host via Testcontainers; the binary may be macOS or wrong CPU.
# Rebuild here so RegressApplication runs a Linux executable matching the container arch.
build_pxf_regress() {
  log "build pxf_regress (linux)"
  export PATH="/usr/local/go/bin:${PATH}"
  make -C "${REPO_DIR}/automation/pxf_regress" clean pxf_regress
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

main() {
  detect_java_paths
  setup_locale_and_packages
  setup_ssh
  setup_cloudberry
  create_demo_cluster
  relax_pg_hba
  build_pxf
  build_pxf_regress
  configure_pxf
  health_check
  log "entrypoint finished; environment ready for tests"
}

main "$@"
