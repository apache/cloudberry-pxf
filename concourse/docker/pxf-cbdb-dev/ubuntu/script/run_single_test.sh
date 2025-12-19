#!/bin/bash
set -euo pipefail

# Run a single test group with proper environment setup
# Usage: ./run_single_test.sh <test_group>

TEST_GROUP="${1:-}"
if [ -z "$TEST_GROUP" ]; then
  echo "Usage: $0 <test_group>"
  echo "Available groups: cli, server, sanity, smoke, hdfs, hcatalog, hcfs, hive, hbase, profile, jdbc, proxy, unused, s3, features, gpdb"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"

# Load centralized env
source "${SCRIPT_DIR}/pxf-env.sh"
source "${SCRIPT_DIR}/utils.sh"

# Test-related defaults
export PXF_TEST_KEEP_DATA=${PXF_TEST_KEEP_DATA:-true}
export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-admin}
export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-password}

# Hadoop/Hive/HBase env
export JAVA_HOME="${JAVA_HADOOP}"
export PATH="$JAVA_HOME/bin:$PATH"
source "${GPHD_ROOT}/bin/gphd-env.sh"

# PostgreSQL settings
export PGHOST=127.0.0.1
export PGOPTIONS=${PGOPTIONS:-"-c extra_float_digits=0 -c timezone='GMT-1'"}

# Ensure Cloudberry env
[ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
[ -f "/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh" ] && source /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh

export GPHOME=${GPHOME:-/usr/local/cloudberry-db}
export PATH="${GPHOME}/bin:${PATH}"

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

export HIVE_HOST=${HIVE_HOST:-localhost}
export HIVE_PORT=${HIVE_PORT:-10000}
export HIVE_SERVER_HOST=${HIVE_SERVER_HOST:-${HIVE_HOST}}
export HIVE_SERVER_PORT=${HIVE_SERVER_PORT:-${HIVE_PORT}}

# Helper functions from run_tests.sh
cleanup_hive_state() {
  hive -e "
    DROP TABLE IF EXISTS hive_small_data CASCADE;
    DROP TABLE IF EXISTS hive_small_data_orc CASCADE;
    DROP TABLE IF EXISTS hive_small_data_orc_acid CASCADE;
    DROP TABLE IF EXISTS hive_partitioned_table_orc_acid CASCADE;
    DROP TABLE IF EXISTS hive_orc_all_types CASCADE;
    DROP TABLE IF EXISTS hive_orc_multifile CASCADE;
    DROP TABLE IF EXISTS hive_orc_snappy CASCADE;
    DROP TABLE IF EXISTS hive_orc_zlib CASCADE;
    DROP TABLE IF EXISTS hive_table_allowed CASCADE;
    DROP TABLE IF EXISTS hive_table_prohibited CASCADE;
  " >/dev/null 2>&1 || true
  hdfs dfs -rm -r -f /hive/warehouse/hive_small_data >/dev/null 2>&1 || true
  hdfs dfs -rm -r -f /hive/warehouse/hive_small_data_orc >/dev/null 2>&1 || true
}

cleanup_hbase_state() {
  echo "disable 'pxflookup'; drop 'pxflookup';
        disable 'hbase_table'; drop 'hbase_table';
        disable 'hbase_table_allowed'; drop 'hbase_table_allowed';
        disable 'hbase_table_prohibited'; drop 'hbase_table_prohibited';
        disable 'hbase_table_multi_regions'; drop 'hbase_table_multi_regions';
        disable 'hbase_null_table'; drop 'hbase_null_table';
        disable 'long_qualifiers_hbase_table'; drop 'long_qualifiers_hbase_table';
        disable 'empty_table'; drop 'empty_table';" \
    | hbase shell -n >/dev/null 2>&1 || true
}

set_xml_property() {
  local file="$1" name="$2" value="$3"
  [ ! -f "${file}" ] && return
  if grep -q "<name>${name}</name>" "${file}"; then
    perl -0777 -pe 's#(<name>'"${name}"'</name>\s*<value>)[^<]+(</value>)#${1}'"${value}"'${2}#' -i "${file}"
  else
    perl -0777 -pe 's#</configuration>#  <property>\n    <name>'"${name}"'</name>\n    <value>'"${value}"'</value>\n  </property>\n</configuration>#' -i "${file}"
  fi
}

ensure_hive_tez_settings() {
  local hive_site="${HIVE_HOME}/conf/hive-site.xml"
  set_xml_property "${hive_site}" "hive.execution.engine" "tez"
  set_xml_property "${hive_site}" "hive.tez.container.size" "2048"
  set_xml_property "${hive_site}" "hive.tez.java.opts" "-Xmx1536m -XX:+UseG1GC"
  set_xml_property "${hive_site}" "tez.am.resource.memory.mb" "1536"
}

ensure_yarn_vmem_settings() {
  local yarn_site="${HADOOP_CONF_DIR}/yarn-site.xml"
  set_xml_property "${yarn_site}" "yarn.nodemanager.vmem-check-enabled" "false"
  set_xml_property "${yarn_site}" "yarn.nodemanager.vmem-pmem-ratio" "4.0"
}

ensure_minio_bucket() {
  local mc_bin="/home/gpadmin/workspace/mc"
  if [ -x "${mc_bin}" ]; then
    ${mc_bin} alias set local http://localhost:9000 admin password >/dev/null 2>&1 || true
    ${mc_bin} mb local/gpdb-ud-scratch --ignore-existing >/dev/null 2>&1 || true
    ${mc_bin} policy set download local/gpdb-ud-scratch >/dev/null 2>&1 || true
  fi
}

ensure_hadoop_s3a_config() {
  local core_site="${HADOOP_CONF_DIR}/core-site.xml"
  if [ -f "${core_site}" ] && ! grep -q "fs.s3a.endpoint" "${core_site}"; then
    perl -0777 -pe '
s#</configuration>#  <property>
    <name>fs.s3a.endpoint</name>
    <value>http://localhost:9000</value>
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
    <name>fs.s3a.access.key</name>
    <value>'"${AWS_ACCESS_KEY_ID}"'</value>
  </property>
  <property>
    <name>fs.s3a.secret.key</name>
    <value>'"${AWS_SECRET_ACCESS_KEY}"'</value>
  </property>
  <property>
    <name>fs.s3a.aws.credentials.provider</name>
    <value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value>
  </property>
</configuration>#' -i "${core_site}"
  fi
}

configure_pxf_s3_server() {
  local server_dir="${PXF_BASE}/servers/s3"
  mkdir -p "${server_dir}"
  cat > "${server_dir}/s3-site.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property><name>fs.s3a.endpoint</name><value>http://localhost:9000</value></property>
  <property><name>fs.s3a.path.style.access</name><value>true</value></property>
  <property><name>fs.s3a.connection.ssl.enabled</name><value>false</value></property>
  <property><name>fs.s3a.impl</name><value>org.apache.hadoop.fs.s3a.S3AFileSystem</value></property>
  <property><name>fs.s3a.aws.credentials.provider</name><value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value></property>
  <property><name>fs.s3a.access.key</name><value>${AWS_ACCESS_KEY_ID}</value></property>
  <property><name>fs.s3a.secret.key</name><value>${AWS_SECRET_ACCESS_KEY}</value></property>
</configuration>
EOF
  cat > "${server_dir}/core-site.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property><name>fs.defaultFS</name><value>s3a://</value></property>
  <property><name>fs.s3a.path.style.access</name><value>true</value></property>
  <property><name>fs.s3a.connection.ssl.enabled</name><value>false</value></property>
  <property><name>fs.s3a.endpoint</name><value>http://localhost:9000</value></property>
  <property><name>fs.s3a.impl</name><value>org.apache.hadoop.fs.s3a.S3AFileSystem</value></property>
  <property><name>fs.s3a.aws.credentials.provider</name><value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value></property>
  <property><name>fs.s3a.access.key</name><value>${AWS_ACCESS_KEY_ID}</value></property>
  <property><name>fs.s3a.secret.key</name><value>${AWS_SECRET_ACCESS_KEY}</value></property>
</configuration>
EOF
}

configure_pxf_default_s3_server() {
  local default_s3_site="${PXF_BASE}/servers/default/s3-site.xml"
  if [ -f "${default_s3_site}" ]; then
    cat > "${default_s3_site}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property><name>fs.s3a.endpoint</name><value>http://localhost:9000</value></property>
  <property><name>fs.s3a.path.style.access</name><value>true</value></property>
  <property><name>fs.s3a.connection.ssl.enabled</name><value>false</value></property>
  <property><name>fs.s3a.impl</name><value>org.apache.hadoop.fs.s3a.S3AFileSystem</value></property>
  <property><name>fs.s3a.access.key</name><value>${AWS_ACCESS_KEY_ID}</value></property>
  <property><name>fs.s3a.secret.key</name><value>${AWS_SECRET_ACCESS_KEY}</value></property>
</configuration>
EOF
    cat > "${PXF_BASE}/servers/default/core-site.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property><name>fs.defaultFS</name><value>s3a://</value></property>
  <property><name>fs.s3a.path.style.access</name><value>true</value></property>
  <property><name>fs.s3a.connection.ssl.enabled</name><value>false</value></property>
  <property><name>fs.s3a.endpoint</name><value>http://localhost:9000</value></property>
  <property><name>fs.s3a.impl</name><value>org.apache.hadoop.fs.s3a.S3AFileSystem</value></property>
  <property><name>fs.s3a.aws.credentials.provider</name><value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value></property>
  <property><name>fs.s3a.access.key</name><value>${AWS_ACCESS_KEY_ID}</value></property>
  <property><name>fs.s3a.secret.key</name><value>${AWS_SECRET_ACCESS_KEY}</value></property>
</configuration>
EOF
    for f in hdfs-site.xml mapred-site.xml yarn-site.xml hive-site.xml hbase-site.xml; do
      [ -f "${PXF_BASE}/servers/default/${f}" ] && rm -f "${PXF_BASE}/servers/default/${f}"
    done
    "${PXF_HOME}/bin/pxf" restart >/dev/null
  fi
}

configure_pxf_default_hdfs_server() {
  local server_dir="${PXF_BASE}/servers/default"
  mkdir -p "${server_dir}"
  ln -sf "${HADOOP_CONF_DIR}/core-site.xml" "${server_dir}/core-site.xml"
  ln -sf "${HADOOP_CONF_DIR}/hdfs-site.xml" "${server_dir}/hdfs-site.xml"
  ln -sf "${HADOOP_CONF_DIR}/mapred-site.xml" "${server_dir}/mapred-site.xml"
  ln -sf "${HADOOP_CONF_DIR}/yarn-site.xml" "${server_dir}/yarn-site.xml"
  ln -sf "${HBASE_CONF_DIR}/hbase-site.xml" "${server_dir}/hbase-site.xml"
  ln -sf "${HIVE_HOME}/conf/hive-site.xml" "${server_dir}/hive-site.xml"
  JAVA_HOME="${JAVA_BUILD}" "${PXF_HOME}/bin/pxf" restart >/dev/null || true
}

ensure_testuser_pg_hba() {
  local pg_hba="/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1/pg_hba.conf"
  local reload_needed=false
  if [ -f "${pg_hba}" ]; then
    for entry in "host all testuser 127.0.0.1/32 trust" \
                 "host all all 127.0.0.1/32 trust" \
                 "host all all 0.0.0.0/0 trust" \
                 "host all testuser ::1/128 trust" \
                 "host all all ::1/128 trust"; do
      if ! grep -qF "$entry" "${pg_hba}"; then
        echo "$entry" >> "${pg_hba}"
        reload_needed=true
      fi
    done
    if [ "${reload_needed}" = true ]; then
      sudo -u gpadmin /usr/local/cloudberry-db/bin/pg_ctl -D "$(dirname "${pg_hba}")" reload >/dev/null 2>&1 || true
    fi
  fi
}

ensure_gpupgrade_helpers() {
  for helper in pxf-pre-gpupgrade pxf-post-gpupgrade; do
    if [ ! -x "/usr/local/bin/${helper}" ]; then
      cat <<EOF | sudo tee "/usr/local/bin/${helper}" >/dev/null
#!/usr/bin/env bash
export GPHOME=\${GPHOME:-/usr/local/cloudberry-db}
exec /usr/local/pxf/bin/${helper} "\$@"
EOF
      sudo chmod +x "/usr/local/bin/${helper}"
    fi
  done
  python3 - <<'PY'
import pathlib, re
scripts = ["/usr/local/pxf/bin/pxf-pre-gpupgrade", "/usr/local/pxf/bin/pxf-post-gpupgrade"]
for s in scripts:
    p = pathlib.Path(s)
    if not p.exists():
        continue
    text = p.read_text()
    text = re.sub(r"export PGPORT=.*", "export PGPORT=${PGPORT:-7000}", text)
    text = re.sub(r'export PGDATABASE=.*', 'export PGDATABASE="${PGDATABASE:-pxfautomation}"', text)
    p.write_text(text)
PY
  export PATH="/usr/local/bin:${PATH}"
}

ensure_testplugin_jar() {
  if [ ! -f "${PXF_BASE}/lib/pxf-automation-test.jar" ]; then
    pushd "${REPO_ROOT}/automation" >/dev/null
    mvn -q -DskipTests test-compile
    jar cf "${PXF_BASE}/lib/pxf-automation-test.jar" -C target/classes org/greenplum/pxf/automation/testplugin
    popd >/dev/null
    JAVA_HOME="${JAVA_BUILD}" "${PXF_HOME}/bin/pxf" restart >/dev/null || true
  fi
}

# Run health check
health_check

# Run test based on group
cd "${REPO_ROOT}/automation"

case "$TEST_GROUP" in
  cli)
    cd "${REPO_ROOT}/cli"
    make test
    ;;
  server)
    cd "${REPO_ROOT}/server"
    ./gradlew test
    ;;
  hive)
    cleanup_hive_state
    ensure_hive_tez_settings
    ensure_yarn_vmem_settings
    make GROUP="hive"
    ;;
  hbase)
    cleanup_hbase_state
    make GROUP="hbase"
    ;;
  s3)
    ensure_minio_bucket
    ensure_hadoop_s3a_config
    configure_pxf_s3_server
    configure_pxf_default_s3_server
    export PROTOCOL=s3
    export HADOOP_OPTIONAL_TOOLS=hadoop-aws
    make GROUP="s3"
    ;;
  features|gpdb)
    ensure_gpupgrade_helpers
    ensure_testplugin_jar
    ensure_testuser_pg_hba
    ensure_minio_bucket
    ensure_hadoop_s3a_config
    configure_pxf_s3_server
    configure_pxf_default_hdfs_server
    export PROTOCOL=
    make GROUP="$TEST_GROUP"
    ;;
  proxy)
    ensure_testuser_pg_hba
    make GROUP="proxy"
    ;;
  sanity|smoke|hdfs|hcatalog|hcfs|profile|jdbc|unused)
    export PROTOCOL=
    make GROUP="$TEST_GROUP"
    ;;
  *)
    echo "Unknown test group: $TEST_GROUP"
    exit 1
    ;;
esac

echo "[run_single_test] Test group $TEST_GROUP completed"
