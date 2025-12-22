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
# Provide S3 credentials so MinIO seeding and user-parameter overrides succeed.
export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-admin}
export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-password}

# Hadoop/Hive/HBase env
export JAVA_HOME="${JAVA_HADOOP}"
export PATH="$JAVA_HOME/bin:$PATH"
source "${GPHD_ROOT}/bin/gphd-env.sh"

# Force local PostgreSQL to IPv4 to avoid ::1 pg_hba misses in proxy tests
export PGHOST=127.0.0.1
# Match historical float string output used by expected files and normalize timezone
export PGOPTIONS=${PGOPTIONS:-"-c extra_float_digits=0 -c timezone='GMT-1'"}

# Ensure Cloudberry env if present
[ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
[ -f "/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh" ] && source /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
# Guarantee psql is on PATH for pg_regress/pxf_regress invocations
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

# Force Hive endpoints to localhost unless explicitly overridden (default sut points to cdw)
export HIVE_HOST=${HIVE_HOST:-localhost}
export HIVE_PORT=${HIVE_PORT:-10000}
export HIVE_SERVER_HOST=${HIVE_SERVER_HOST:-${HIVE_HOST}}
export HIVE_SERVER_PORT=${HIVE_SERVER_PORT:-${HIVE_PORT}}

health_check_with_retry() {
  if ( health_check ); then
    return 0
  fi
  echo "[run_tests] health check failed; restarting HiveServer2 and retrying..."
  restart_hiveserver2 || echo "[warn] HiveServer2 restart attempt failed"
  if ! ( health_check ); then
    echo "[warn] health check still failing, continuing anyway"
  fi
}

cleanup_hdfs_test_data() {
  hdfs dfs -rm -r -f /gpdb-ud-scratch/tmp/pxf_automation_data >/dev/null 2>&1 || true
}

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

restart_hiveserver2() {
  pkill -f hiveserver2 >/dev/null 2>&1 || true
  pkill -f proc_hiveserver2 >/dev/null 2>&1 || true
  pkill -f HiveServer2 >/dev/null 2>&1 || true
  export HADOOP_HEAPSIZE=${HADOOP_HEAPSIZE:-1024}
  nohup hiveserver2 >/home/gpadmin/workspace/singlecluster/storage/logs/hive-gpadmin-hiveserver2-mdw.out 2>&1 &
  for _ in {1..20}; do
    sleep 3
    if beeline -u "jdbc:hive2://localhost:10000/default;auth=noSasl" -n gpadmin -p "" -e "select 1" >/dev/null 2>&1; then
      return 0
    fi
  done
  return 1
}

ensure_hive_ready() {
  for _ in {1..2}; do
    if beeline -u "jdbc:hive2://localhost:10000/default;auth=noSasl" -n gpadmin -p "" -e "select 1" >/dev/null 2>&1; then
      return 0
    fi
    restart_hiveserver2 || true
  done
  return 1
}

ensure_minio_bucket() {
  local mc_bin="/home/gpadmin/workspace/mc"
  if [ -x "${mc_bin}" ]; then
    ${mc_bin} alias set local http://localhost:9000 admin password >/dev/null 2>&1 || true
    ${mc_bin} mb local/gpdb-ud-scratch --ignore-existing >/dev/null 2>&1 || true
    ${mc_bin} policy set download local/gpdb-ud-scratch >/dev/null 2>&1 || true
  fi
}

set_xml_property() {
  local file="$1" name="$2" value="$3"
  if [ ! -f "${file}" ]; then
    return
  fi
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

# Configure dedicated PXF server "s3" pointing to local MinIO;
# used by tests that explicitly set server=s3
configure_pxf_s3_server() {
  local server_dir="${PXF_BASE}/servers/s3"
  mkdir -p "${server_dir}"
  cat > "${server_dir}/s3-site.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property>
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
    <name>fs.s3a.impl</name>
    <value>org.apache.hadoop.fs.s3a.S3AFileSystem</value>
  </property>
  <property>
    <name>fs.s3a.aws.credentials.provider</name>
    <value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value>
  </property>
  <property>
    <name>fs.s3a.access.key</name>
    <value>${AWS_ACCESS_KEY_ID}</value>
  </property>
  <property>
    <name>fs.s3a.secret.key</name>
    <value>${AWS_SECRET_ACCESS_KEY}</value>
  </property>
</configuration>
EOF
  cat > "${server_dir}/core-site.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>s3a://</value>
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
    <name>fs.s3a.endpoint</name>
    <value>http://localhost:9000</value>
  </property>
  <property>
    <name>fs.s3a.impl</name>
    <value>org.apache.hadoop.fs.s3a.S3AFileSystem</value>
  </property>
  <property>
    <name>fs.s3a.aws.credentials.provider</name>
    <value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value>
  </property>
  <property>
    <name>fs.s3a.access.key</name>
    <value>${AWS_ACCESS_KEY_ID}</value>
  </property>
  <property>
    <name>fs.s3a.secret.key</name>
    <value>${AWS_SECRET_ACCESS_KEY}</value>
  </property>
</configuration>
EOF
}

# Configure default PXF server to point to local MinIO with explicit creds;
# used by tests that do NOT pass a server=name parameter (default server path)
configure_pxf_default_s3_server() {
  export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-admin}
  export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-password}
  local default_s3_site="${PXF_BASE}/servers/default/s3-site.xml"
  if [ -f "${default_s3_site}" ]; then
    cat > "${default_s3_site}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property>
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
    <name>fs.s3a.impl</name>
    <value>org.apache.hadoop.fs.s3a.S3AFileSystem</value>
  </property>
  <property>
    <name>fs.s3a.access.key</name>
    <value>${AWS_ACCESS_KEY_ID}</value>
  </property>
  <property>
    <name>fs.s3a.secret.key</name>
    <value>${AWS_SECRET_ACCESS_KEY}</value>
  </property>
</configuration>
EOF
    cat > "${PXF_BASE}/servers/default/core-site.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>s3a://</value>
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
    <name>fs.s3a.endpoint</name>
    <value>http://localhost:9000</value>
  </property>
  <property>
    <name>fs.s3a.impl</name>
    <value>org.apache.hadoop.fs.s3a.S3AFileSystem</value>
  </property>
  <property>
    <name>fs.s3a.aws.credentials.provider</name>
    <value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value>
  </property>
  <property>
    <name>fs.s3a.access.key</name>
    <value>${AWS_ACCESS_KEY_ID}</value>
  </property>
  <property>
    <name>fs.s3a.secret.key</name>
    <value>${AWS_SECRET_ACCESS_KEY}</value>
  </property>
</configuration>
EOF
    # hide HDFS/Hive configs so default server is treated as S3-only
    for f in hdfs-site.xml mapred-site.xml yarn-site.xml hive-site.xml hbase-site.xml; do
      [ -f "${PXF_BASE}/servers/default/${f}" ] && rm -f "${PXF_BASE}/servers/default/${f}"
    done
    "${PXF_HOME}/bin/pxf" restart >/dev/null
  fi
}

# Ensure proxy tests can login as testuser from localhost.
ensure_testuser_pg_hba() {
  local pg_hba="/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1/pg_hba.conf"
  local entry="host all testuser 127.0.0.1/32 trust"
  local all_local="host all all 127.0.0.1/32 trust"
  local all_any="host all all 0.0.0.0/0 trust"
  local entry_v6="host all testuser ::1/128 trust"
  local all_local_v6="host all all ::1/128 trust"
  local reload_needed=false
  if [ -f "${pg_hba}" ]; then
    if ! grep -q "testuser.*127.0.0.1/32" "${pg_hba}"; then
      echo "${entry}" >> "${pg_hba}"
      reload_needed=true
    fi
    if ! grep -q "all all 127.0.0.1/32 trust" "${pg_hba}"; then
      echo "${all_local}" >> "${pg_hba}"
      reload_needed=true
    fi
    if ! grep -q "all all 0.0.0.0/0 trust" "${pg_hba}"; then
      echo "${all_any}" >> "${pg_hba}"
      reload_needed=true
    fi
    if ! grep -q "testuser.*::1/128" "${pg_hba}"; then
      echo "${entry_v6}" >> "${pg_hba}"
      reload_needed=true
    fi
    if ! grep -q "all all ::1/128 trust" "${pg_hba}"; then
      echo "${all_local_v6}" >> "${pg_hba}"
      reload_needed=true
    fi

    if [ "${reload_needed}" = true ]; then
      sudo -u gpadmin /usr/local/cloudberry-db/bin/pg_ctl -D "$(dirname "${pg_hba}")" reload >/dev/null 2>&1 || true
    fi
  fi
}

base_test(){
  # keep PROTOCOL empty so tests use HDFS; we'll set minio only for s3 later
  export PROTOCOL=
  # ensure gpdb connections target localhost over IPv4 for proxy tests
  export PGHOST=127.0.0.1
  export PATH="${GPHOME}/bin:${PATH}"
  ensure_testuser_pg_hba

  make GROUP="sanity" || true
  save_test_reports "sanity"
  echo "[run_tests] GROUP=sanity finished"

  make GROUP="smoke" || true
  save_test_reports "smoke"
  echo "[run_tests] GROUP=smoke finished"

  make GROUP="hdfs" || true
  save_test_reports "hdfs"
  echo "[run_tests] GROUP=hdfs finished"

  make GROUP="hcatalog" || true
  save_test_reports "hcatalog"
  echo "[run_tests] GROUP=hcatalog finished"

  make GROUP="hcfs" || true
  save_test_reports "hcfs"
  echo "[run_tests] GROUP=hcfs finished"

  cleanup_hive_state
  ensure_hive_tez_settings
  ensure_yarn_vmem_settings
  make GROUP="hive" || true
  save_test_reports "hive"
  echo "[run_tests] GROUP=hive finished"

  cleanup_hbase_state
  make GROUP="hbase" || true
  save_test_reports "hbase"
  echo "[run_tests] GROUP=hbase finished"

  make GROUP="profile" || true
  save_test_reports "profile"
  echo "[run_tests] GROUP=profile finished"

  make GROUP="jdbc" || true
  save_test_reports "jdbc"
  echo "[run_tests] GROUP=jdbc finished"

  make GROUP="proxy" || true
  save_test_reports "proxy"
  echo "[run_tests] GROUP=proxy finished"

  make GROUP="unused" || true
  save_test_reports "unused"
  echo "[run_tests] GROUP=unused finished"

  ensure_minio_bucket
  ensure_hadoop_s3a_config
  configure_pxf_s3_server
  configure_pxf_default_s3_server
  export PROTOCOL=s3
  export HADOOP_OPTIONAL_TOOLS=hadoop-aws
  make GROUP="s3" || true
  save_test_reports "s3"
  echo "[run_tests] GROUP=s3 finished"
}

# Restore default PXF server to local HDFS/Hive/HBase configuration
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

ensure_gpupgrade_helpers() {
  export PXF_HOME=${PXF_HOME:-/usr/local/pxf}
  export PXF_BASE=${PXF_BASE:-/home/gpadmin/pxf-base}
  export GPHOME=${GPHOME:-/usr/local/cloudberry-db}
  # Provide wrappers so mvn child processes see the binaries on PATH
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
  # Normalize default port/database to demo cluster settings
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
  export PXF_BASE=${PXF_BASE:-/home/gpadmin/pxf-base}
  export PXF_HOME=${PXF_HOME:-/usr/local/pxf}
  if [ ! -f "${PXF_BASE}/lib/pxf-automation-test.jar" ]; then
    pushd "${REPO_ROOT}/automation" >/dev/null
    mvn -q -DskipTests test-compile
    jar cf "${PXF_BASE}/lib/pxf-automation-test.jar" -C target/classes org/greenplum/pxf/automation/testplugin
    popd >/dev/null
    JAVA_HOME="${JAVA_BUILD}" "${PXF_HOME}/bin/pxf" restart >/dev/null || true
  fi
}

feature_test(){
  # Ensure PXF CLI is available for gpupgrade tests and sanity checks
  export PXF_HOME=${PXF_HOME:-/usr/local/pxf}
  export PATH="${PXF_HOME}/bin:${PATH}"
  ensure_gpupgrade_helpers
  ensure_testplugin_jar

  # Make sure core services are alive before preparing configs
  health_check_with_retry || true

  export PGHOST=127.0.0.1
  export PATH="${GPHOME}/bin:${PATH}"
  ensure_testuser_pg_hba
  # Clean stale state from previous runs so feature suite starts fresh
  cleanup_hdfs_test_data
  hdfs dfs -rm -r -f /tmp/pxf_automation_data >/dev/null 2>&1 || true
  cleanup_hive_state
  cleanup_hbase_state

  # Prepare MinIO/S3 and restore default server to local HDFS/Hive/HBase
  ensure_minio_bucket
  ensure_hadoop_s3a_config
  configure_pxf_s3_server
  configure_pxf_default_hdfs_server
  # Only set default server to MinIO when explicitly running S3 groups; keeping
  # it HDFS-backed avoids hijacking Hive/HDFS tests with fs.defaultFS=s3a://
  #configure_pxf_default_s3_server

  export PROTOCOL=
  make GROUP="features" || true
  save_test_reports "features"
  echo "[run_tests] GROUP=features finished"
}

gpdb_test() {
  echo "[run_tests] Starting GROUP=gpdb"
  make GROUP="gpdb" || true
  save_test_reports "gpdb"
  echo "[run_tests] GROUP=gpdb finished"
}

pxf_extension_test(){
  local sudo_cmd=""
  if [ "$(id -u)" -ne 0 ]; then
    sudo_cmd="sudo -n"
  fi
  local extension_dir="${GPHOME}/share/postgresql/extension"
  local pxf_fdw_control="${extension_dir}/pxf_fdw.control"
  if [ -d "${REPO_ROOT}/fdw" ] && [ -d "${extension_dir}" ]; then
    for sql in pxf_fdw--2.0.sql pxf_fdw--1.0--2.0.sql pxf_fdw--2.0--1.0.sql; do
      if [ -f "${REPO_ROOT}/fdw/${sql}" ]; then
        ${sudo_cmd} cp -f "${REPO_ROOT}/fdw/${sql}" "${extension_dir}/${sql}"
      fi
    done
  fi

  set_pxf_fdw_default_version() {
    local version="$1"
    if [ -f "${pxf_fdw_control}" ]; then
      ${sudo_cmd} sed -i "s/^default_version = '.*'/default_version = '${version}'/" "${pxf_fdw_control}"
    fi
  }

  set_pxf_fdw_default_version "2.0"
  make GROUP="pxfExtensionVersion2" || true
  save_test_reports "pxfExtensionVersion2"
  make GROUP="pxfExtensionVersion2_1" || true
  save_test_reports "pxfExtensionVersion2_1"

  set_pxf_fdw_default_version "1.0"
  make GROUP="pxfFdwExtensionVersion1" || true
  save_test_reports "pxfFdwExtensionVersion1"

  set_pxf_fdw_default_version "2.0"
  make GROUP="pxfFdwExtensionVersion2" || true
  save_test_reports "pxfFdwExtensionVersion2"
}

bench_prepare_env() {
  export HADOOP_HEAPSIZE=${HADOOP_HEAPSIZE:-2048}
  export JAVA_HOME="${JAVA_HADOOP}"
  export PATH="${JAVA_HOME}/bin:${HADOOP_HOME}/bin:${PATH}"

  hdfs dfs -rm -r -f /tmp/pxf_automation_data /gpdb-ud-scratch/tmp/pxf_automation_data >/dev/null 2>&1 || true
  for scratch in /tmp/pxf_automation_data /gpdb-ud-scratch/tmp/pxf_automation_data; do
    hdfs dfs -mkdir -p "${scratch}" >/dev/null 2>&1 || true
    hdfs dfs -chmod -R 775 "$(dirname "${scratch}")" >/dev/null 2>&1 || true
  done
  hdfs dfs -mkdir -p /tmp/hive >/dev/null 2>&1 || true
  hdfs dfs -chmod -R 777 /tmp/hive >/dev/null 2>&1 || true

  export PROTOCOL=
  export PXF_TEST_KEEP_DATA=${PXF_TEST_KEEP_DATA:-true}

  ensure_hive_ready
}

load_test(){
  bench_prepare_env
  make GROUP="load" || true
  save_test_reports "load"
  echo "[run_tests] GROUP=load finished"
}

performance_test(){
  bench_prepare_env
  make GROUP="performance" || true
  save_test_reports "performance"
  echo "[run_tests] GROUP=performance finished"
}

bench_test(){
  load_test
  performance_test
}

# Save test reports for a specific group to avoid overwriting
save_test_reports() {
  local group="$1"
  local surefire_dir="${REPO_ROOT}/automation/target/surefire-reports"
  local logs_dir="${REPO_ROOT}/automation/automation_logs"
  local pxf_logs_dir="${PXF_BASE:-/home/gpadmin/pxf-base}/logs"
  local artifacts_dir="${REPO_ROOT}/automation/test_artifacts"
  local group_dir="${artifacts_dir}/${group}"

  mkdir -p "$group_dir"

  if [ -d "$surefire_dir" ] && [ "$(ls -A "$surefire_dir" 2>/dev/null)" ]; then
    echo "[run_tests] Saving $group test reports to $group_dir"
    cp -r "$surefire_dir"/* "$group_dir/" 2>/dev/null || true
  else
    echo "[run_tests] No surefire reports found for $group"
  fi

  if [ -d "$logs_dir" ] && [ "$(ls -A "$logs_dir" 2>/dev/null)" ]; then
    echo "[run_tests] Saving $group test logs to $group_dir"
    cp -r "$logs_dir" "$group_dir/" 2>/dev/null || true
  else
    echo "[run_tests] No automation logs found for $group"
  fi

  # Capture PXF service logs to aid debugging
  if [ -d "$pxf_logs_dir" ] && [ "$(ls -A "$pxf_logs_dir" 2>/dev/null)" ]; then
    echo "[run_tests] Saving PXF logs to $group_dir/pxf-logs"
    mkdir -p "$group_dir/pxf-logs"
    cp -r "$pxf_logs_dir"/* "$group_dir/pxf-logs/" 2>/dev/null || true
  else
    echo "[run_tests] No PXF logs found at $pxf_logs_dir"
  fi
}

# Generate test summary from surefire reports
generate_test_summary() {
  local artifacts_dir="${REPO_ROOT}/automation/test_artifacts"
  local summary_file="${artifacts_dir}/test_summary.json"

  mkdir -p "$artifacts_dir"

  echo "=== Generating Test Summary ==="

  local total_tests=0
  local total_failures=0
  local total_errors=0
  local total_skipped=0

  # Statistics by test group
  declare -A group_stats

  # Read from each test group directory
  for group_dir in "$artifacts_dir"/*; do
    [ -d "$group_dir" ] || continue

    local group=$(basename "$group_dir")
    # Skip if it's not a test group directory
    [[ "$group" =~ ^(smoke|hcatalog|hcfs|hdfs|hive|gpdb|sanity|hbase|profile|jdbc|proxy|unused|s3|features|load|performance|pxfExtensionVersion2|pxfExtensionVersion2_1|pxfFdwExtensionVersion1|pxfFdwExtensionVersion2)$ ]] || continue

    echo "Processing $group test reports from $group_dir"

    local group_tests=0
    local group_failures=0
    local group_errors=0
    local group_skipped=0

    for xml in "$group_dir"/TEST-*.xml; do
      [ -f "$xml" ] || continue

      # Extract statistics from XML
      local tests=$(grep -oP 'tests="\K\d+' "$xml" | head -1 || echo "0")
      local failures=$(grep -oP 'failures="\K\d+' "$xml" | head -1 || echo "0")
      local errors=$(grep -oP 'errors="\K\d+' "$xml" | head -1 || echo "0")
      local skipped=$(grep -oP 'skipped="\K\d+' "$xml" | head -1 || echo "0")

      # Accumulate group statistics
      group_tests=$((group_tests + tests))
      group_failures=$((group_failures + failures))
      group_errors=$((group_errors + errors))
      group_skipped=$((group_skipped + skipped))
    done

    # Store group statistics
    group_stats[$group]="$group_tests,$group_failures,$group_errors,$group_skipped"

    # Accumulate totals
    total_tests=$((total_tests + group_tests))
    total_failures=$((total_failures + group_failures))
    total_errors=$((total_errors + group_errors))
    total_skipped=$((total_skipped + group_skipped))
  done

  local total_failed_cases=$((total_failures + total_errors))
  local total_passed=$((total_tests - total_failed_cases - total_skipped))

  # Generate JSON report
  echo "{" > "$summary_file"
  echo "  \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"," >> "$summary_file"
  echo "  \"overall\": {" >> "$summary_file"
  echo "    \"total\": $total_tests," >> "$summary_file"
  echo "    \"passed\": $total_passed," >> "$summary_file"
  echo "    \"failed\": $total_failed_cases," >> "$summary_file"
  echo "    \"skipped\": $total_skipped" >> "$summary_file"
  echo "  }," >> "$summary_file"
  echo "  \"groups\": {" >> "$summary_file"

  local first=true
  for group in "${!group_stats[@]}"; do
    IFS=',' read -r g_tests g_failures g_errors g_skipped <<< "${group_stats[$group]}"
    local g_failed=$((g_failures + g_errors))
    local g_passed=$((g_tests - g_failed - g_skipped))

    if [ "$first" = false ]; then
      echo "," >> "$summary_file"
    fi

    echo "    \"$group\": {" >> "$summary_file"
    echo "      \"total\": $g_tests," >> "$summary_file"
    echo "      \"passed\": $g_passed," >> "$summary_file"
    echo "      \"failed\": $g_failed," >> "$summary_file"
    echo "      \"skipped\": $g_skipped" >> "$summary_file"
    echo -n "    }" >> "$summary_file"
    first=false
  done

  echo "" >> "$summary_file"
  echo "  }" >> "$summary_file"
  echo "}" >> "$summary_file"

  # Print summary to console
  echo
  echo "=========================================="
  echo "PXF Automation Test Summary"
  echo "=========================================="
  echo "Total Tests: $total_tests"
  echo "Passed: $total_passed"
  echo "Failed: $total_failed_cases"
  echo "Skipped: $total_skipped"
  echo

  if [ ${#group_stats[@]} -gt 0 ]; then
    echo "Results by Group:"
    echo "----------------------------------------"
    printf "%-12s %6s %6s %6s %6s\n" "Group" "Total" "Pass" "Fail" "Skip"
    echo "----------------------------------------"

    for group in $(printf '%s\n' "${!group_stats[@]}" | sort); do
      IFS=',' read -r g_tests g_failures g_errors g_skipped <<< "${group_stats[$group]}"
      local g_failed=$((g_failures + g_errors))
      local g_passed=$((g_tests - g_failed - g_skipped))
      printf "%-12s %6d %6d %6d %6d\n" "$group" "$g_tests" "$g_passed" "$g_failed" "$g_skipped"
    done
    echo "----------------------------------------"
  fi

  echo "Test summary saved to: $summary_file"
  echo "=========================================="

  # Return 1 if any tests failed, 0 if all passed
  if [ $total_failed_cases -gt 0 ]; then
    echo "Found $total_failed_cases failed test cases"
    return 1
  else
    echo "All tests passed"
    return 0
  fi
}

run_single_group() {
  local group="$1"
  echo "[run_tests] Running single test group: $group"
  
  # Run health check first
  health_check_with_retry
  
  ensure_testuser_pg_hba
  export PGHOST=127.0.0.1
  export PATH="${GPHOME}/bin:${PATH}"
  
  case "$group" in
    cli)
      cd "${REPO_ROOT}/cli"
      make test
      ;;
    external-table)
      [ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
      cd "${REPO_ROOT}/external-table"
      make installcheck
      ;;
    server)
      cd "${REPO_ROOT}/server"
      ./gradlew test
      ;;
    hive)
      cleanup_hive_state
      ensure_hive_tez_settings
      ensure_yarn_vmem_settings
      export PROTOCOL=
      make GROUP="hive"
      save_test_reports "hive"
      ;;
    hbase)
      cleanup_hbase_state
      export PROTOCOL=
      make GROUP="hbase"
      save_test_reports "hbase"
      ;;
    s3)
      ensure_minio_bucket
      ensure_hadoop_s3a_config
      configure_pxf_s3_server
      configure_pxf_default_s3_server
      export PROTOCOL=s3
      export HADOOP_OPTIONAL_TOOLS=hadoop-aws
      make GROUP="s3"
      save_test_reports "s3"
      ;;
    features)
      feature_test
      ;;
    gpdb)
      gpdb_test
      ;;
    pxf_extension)
      pxf_extension_test
      ;;
    load)
      bench_prepare_env
      load_test
      ;;
    performance)
      bench_prepare_env
      performance_test
      ;;
    proxy)
      export PROTOCOL=
      make GROUP="proxy"
      save_test_reports "proxy"
      ;;
    sanity|smoke|hdfs|hcatalog|hcfs|profile|jdbc|unused)
      export PROTOCOL=
      make GROUP="$group"
      save_test_reports "$group"
      ;;
    *)
      echo "Unknown test group: $group"
      echo "Available groups: cli, external-table, server, sanity, smoke, hdfs, hcatalog, hcfs, hive, hbase, profile, jdbc, proxy, unused, s3, features, gpdb, load, performance, bench, pxf_extension"
      exit 1
      ;;
  esac
  
  echo "[run_tests] Test group $group completed"
}

main() {
  local group="${1:-}"
  
  if [ -n "$group" ]; then
    # Run single test group
    run_single_group "$group"
  else
    # Run all test groups (original behavior)
    echo "[run_tests] Running all test groups..."

    # Run health check first
    health_check_with_retry

    # Run base tests (includes smoke, hdfs, hcatalog, hcfs, hive, etc.)
    base_test

    # Run feature tests (includes features, gpdb)
    feature_test

    # Run bench tests (includes load, performance)
    bench_test

    echo "[run_tests] All test groups completed, generating summary..."

    # Generate test summary and return appropriate exit code
    generate_test_summary
  fi
}

main "$@"
