#!/bin/bash
# Kerberos entrypoint: enable singlecluster + PXF secure setup in one go.
set -euo pipefail

log() { echo "[kerberos][$(date '+%F %T')] $*"; }
die() { log "$*"; exit 1; }

# Ensure KDC tools are present (idempotent)
if [ ! -x /usr/sbin/kadmin.local ]; then
  log "installing Kerberos server packages (krb5-kdc, krb5-admin-server, krb5-user)"
  sudo -n apt-get update >/dev/null
  sudo -n DEBIAN_FRONTEND=noninteractive apt-get install -y krb5-kdc krb5-admin-server krb5-user >/dev/null
fi

REALM=${REALM:-PXF.LOCAL}
HOST_FQDN=${HOST_FQDN:-$(hostname -f)}
GPHD_ROOT=${GPHD_ROOT:-/home/gpadmin/workspace/singlecluster}
REPO_ROOT=${REPO_ROOT:-/home/gpadmin/workspace/cloudberry-pxf}
PXF_SCRIPTS=${PXF_SCRIPTS:-${REPO_ROOT}/concourse/docker/pxf-cbdb-dev/ubuntu/script}
PXF_FS_BASE_PATH=${PXF_FS_BASE_PATH:-/pxf_automation_data}
HADOOP_HOME=${HADOOP_HOME:-${GPHD_ROOT}/hadoop}
HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-${HADOOP_HOME}/etc/hadoop}
YARN_CONF_DIR=${YARN_CONF_DIR:-${HADOOP_CONF_DIR}}
HIVE_HOME=${HIVE_HOME:-${GPHD_ROOT}/hive}
HIVE_CONF_DIR=${HIVE_CONF_DIR:-${HIVE_HOME}/conf}
HBASE_HOME=${HBASE_HOME:-${GPHD_ROOT}/hbase}
HBASE_CONF_DIR=${HBASE_CONF_DIR:-${HBASE_HOME}/conf}
PG_HBA=${PG_HBA:-/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1/pg_hba.conf}
KEYTAB_DIR=${KEYTAB_DIR:-/home/gpadmin/.keytabs}
PXF_KEYTAB=${PXF_KEYTAB:-/usr/local/pxf/conf/pxf.service.keytab}
SSL_KEYSTORE=${SSL_KEYSTORE:-${HADOOP_CONF_DIR}/keystore.jks}
SSL_TRUSTSTORE=${SSL_TRUSTSTORE:-${HADOOP_CONF_DIR}/truststore.jks}
SSL_STOREPASS=${SSL_STOREPASS:-changeit}
ADMIN_PASS=${ADMIN_PASS:-AdminPass@123}
PXF_BASE=${PXF_BASE:-/home/gpadmin/pxf-base}
GPHOME=${GPHOME:-/usr/local/cloudberry-db}
# GPDB demo master path is required by pg_hba reloads; define a default up front.
MASTER_DATA_DIRECTORY=${MASTER_DATA_DIRECTORY:-/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1}

# Java locations vary by arch; prefer Java 8 for Hadoop runtime and Java 11 for builds if needed.
JAVA_11_ARM=/usr/lib/jvm/java-11-openjdk-arm64
JAVA_11_AMD=/usr/lib/jvm/java-11-openjdk-amd64
JAVA_8_ARM=/usr/lib/jvm/java-8-openjdk-arm64
JAVA_8_AMD=/usr/lib/jvm/java-8-openjdk-amd64

detect_java_paths() {
  case "$(uname -m)" in
    aarch64|arm64) JAVA_BUILD=${JAVA_BUILD:-${JAVA_11_ARM}}; JAVA_HADOOP=${JAVA_HADOOP:-${JAVA_8_ARM}} ;;
    x86_64|amd64)  JAVA_BUILD=${JAVA_BUILD:-${JAVA_11_AMD}}; JAVA_HADOOP=${JAVA_HADOOP:-${JAVA_8_AMD}} ;;
    *)             JAVA_BUILD=${JAVA_BUILD:-${JAVA_11_ARM}}; JAVA_HADOOP=${JAVA_HADOOP:-${JAVA_8_ARM}} ;;
  esac
  export JAVA_BUILD JAVA_HADOOP
}
detect_java_paths
JAVA_HOME=${JAVA_HOME:-${JAVA_HADOOP}}

PATH="$JAVA_HOME/bin:$PATH"
export JAVA_HOME PATH GPHD_ROOT HADOOP_HOME HADOOP_CONF_DIR YARN_CONF_DIR HIVE_HOME HIVE_CONF_DIR HBASE_HOME HBASE_CONF_DIR PXF_BASE
# Define STORAGE_ROOT early to avoid hbase-daemon.sh creating //storage paths
export STORAGE_ROOT=${STORAGE_ROOT:-${GPHD_ROOT}/storage}
export HIVE_KRB_PRINCIPAL=${HIVE_KRB_PRINCIPAL:-hive/${HOST_FQDN}@${REALM}}
export HIVE_KRB_KEYTAB=${HIVE_KRB_KEYTAB:-${KEYTAB_DIR}/hive.keytab}
export GPHD_ROOT

# Ensure config directories are writable (new containers default to root ownership)
ensure_conf_dirs() {
  sudo mkdir -p "${HADOOP_CONF_DIR}" "${HIVE_CONF_DIR}" "${HBASE_CONF_DIR}" "${STORAGE_ROOT}"
  sudo mkdir -p "${GPHD_ROOT}/zookeeper" "${GPHD_ROOT}/storage"
  sudo chown -R gpadmin:gpadmin "${HADOOP_CONF_DIR}" "${HIVE_CONF_DIR}" "${HBASE_CONF_DIR}" "${STORAGE_ROOT}" "${GPHD_ROOT}/zookeeper" "${GPHD_ROOT}/storage"
  sudo mkdir -p "${STORAGE_ROOT}/zookeeper" "${STORAGE_ROOT}/logs" "${STORAGE_ROOT}/pids"
  sudo chown -R gpadmin:gpadmin "${STORAGE_ROOT}/zookeeper" "${STORAGE_ROOT}/logs" "${STORAGE_ROOT}/pids"
}

# Ensure OS users/groups exist so HDFS superuser checks succeed for proxy tests.
ensure_os_users() {
  sudo getent group supergroup >/dev/null 2>&1 || sudo groupadd supergroup
  local ensure_users=("testuser" "porter" "pxf")
  for u in "${ensure_users[@]}"; do
    if ! id "${u}" >/dev/null 2>&1; then
      sudo useradd -m "${u}"
    fi
  done
  # Remove test users from supergroup to avoid superuser privileges breaking permission tests.
  for u in "${ensure_users[@]}"; do
    sudo gpasswd -d "${u}" supergroup >/dev/null 2>&1 || true
  done
  # Make sure service users are part of supergroup as well.
  for svc in gpadmin hive hdfs pxf hbase yarn; do
    if id "${svc}" >/dev/null 2>&1; then
      sudo usermod -a -G supergroup "${svc}" || true
    fi
  done
}

ensure_ssh_compatibility() {
  # Allow older clients (ganymed SSH) used by automation to negotiate with sshd.
  sudo sed -i '/^KexAlgorithms/d' /etc/ssh/sshd_config
  sudo sed -i '/^HostkeyAlgorithms/d' /etc/ssh/sshd_config
  sudo sed -i '/^PubkeyAcceptedAlgorithms/d' /etc/ssh/sshd_config
  sudo sed -i '/^PasswordAuthentication/d' /etc/ssh/sshd_config
  echo "KexAlgorithms +diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521" | sudo tee -a /etc/ssh/sshd_config >/dev/null
  echo "HostkeyAlgorithms +ssh-rsa" | sudo tee -a /etc/ssh/sshd_config >/dev/null
  echo "PubkeyAcceptedAlgorithms +ssh-rsa" | sudo tee -a /etc/ssh/sshd_config >/dev/null
  echo "PasswordAuthentication yes" | sudo tee -a /etc/ssh/sshd_config >/dev/null
  sudo service ssh restart >/dev/null 2>&1 || true
}

ensure_gpadmin_ssh() {
  sudo mkdir -p /home/gpadmin/.ssh
  sudo chown -R gpadmin:gpadmin /home/gpadmin/.ssh
  sudo chmod 700 /home/gpadmin/.ssh
  echo "gpadmin:gpadmin" | sudo chpasswd || true
  # Recreate key in PEM format so ganymed SSH library can parse it.
  sudo -u gpadmin rm -f /home/gpadmin/.ssh/id_rsa /home/gpadmin/.ssh/id_rsa.pub
  sudo -u gpadmin ssh-keygen -t rsa -m PEM -N "" -f /home/gpadmin/.ssh/id_rsa >/dev/null 2>&1 || true
  sudo -u gpadmin sh -c 'cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys'
  sudo chmod 600 /home/gpadmin/.ssh/authorized_keys
}

if [ ! -x /bin/gphd-env.sh ] && [ -f "${GPHD_ROOT}/bin/gphd-env.sh" ]; then
  sudo ln -sf "${GPHD_ROOT}/bin/gphd-env.sh" /bin/gphd-env.sh
fi

# Some scripts expect gphd-conf.sh; generate a minimal config and add a global symlink.
ensure_gphd_conf() {
  local conf_path="${GPHD_ROOT}/conf/gphd-conf.sh"
  sudo mkdir -p "${GPHD_ROOT}/conf"
  sudo chown -R gpadmin:gpadmin "${GPHD_ROOT}/conf"
  cp -p "${conf_path}" "${conf_path}.bak" 2>/dev/null || true
  cat > "${conf_path}" <<EOF
# Minimal gphd-conf for singlecluster Kerberos (override)
export GPHD_ROOT=${GPHD_ROOT}
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-arm64
export HADOOP_HOME=${GPHD_ROOT}/hadoop
export HBASE_HOME=${GPHD_ROOT}/hbase
export HIVE_HOME=${GPHD_ROOT}/hive
export STORAGE_ROOT=${STORAGE_ROOT:-${GPHD_ROOT}/storage}
export SLAVES=${SLAVES:-1}
export HBASE_STORAGE_ROOT=${HBASE_STORAGE_ROOT:-${STORAGE_ROOT}/hbase}
export HBASE_LOG_DIR=${HBASE_LOG_DIR:-${STORAGE_ROOT}/logs}
export HBASE_PID_DIR=${HBASE_PID_DIR:-${STORAGE_ROOT}/pids}
export ZOOKEEPER_DATA_DIR=${ZOOKEEPER_DATA_DIR:-${STORAGE_ROOT}/zookeeper}
export ZOOKEEPER_LOG_DIR=${ZOOKEEPER_LOG_DIR:-${STORAGE_ROOT}/logs}
export ZOOKEEPER_STORAGE_ROOT=${ZOOKEEPER_STORAGE_ROOT:-${STORAGE_ROOT}/zookeeper}
export HADOOP_STORAGE_ROOT=${HADOOP_STORAGE_ROOT:-${STORAGE_ROOT}/hadoop}
EOF
  if [ ! -x /bin/gphd-conf.sh ]; then
    sudo ln -sf "${conf_path}" /bin/gphd-conf.sh
  fi
  if [ ! -f /conf/gphd-conf.sh ]; then
    sudo mkdir -p /conf
    sudo ln -sf "${conf_path}" /conf/gphd-conf.sh
  fi
}

mkdir -p "${KEYTAB_DIR}"
sudo mkdir -p /usr/local/pxf/conf
sudo chown -R gpadmin:gpadmin /usr/local/pxf

backup_file() {
  local file=$1
  if [ -f "${file}" ] && [ ! -f "${file}.bak" ]; then
    cp -p "${file}" "${file}.bak"
  fi
}

wait_for_port() {
  local host=$1 port=$2 retries=${3:-20} delay=${4:-3}
  for i in $(seq 1 "${retries}"); do
    if bash -c "</dev/tcp/${host}/${port}" >/dev/null 2>&1; then
      return 0
    fi
    sleep "${delay}"
  done
  return 1
}

hdfs_cmd() {
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ${GPHD_ROOT}/hadoop/bin/hdfs "$@"
}

hdfs_dfs() {
  hdfs_cmd dfs "$@"
}

hdfs_dfsadmin() {
  hdfs_cmd dfsadmin "$@"
}

ensure_principal() {
  local principal=$1 keytab=$2
  sudo -n /usr/sbin/kadmin.local -q "addprinc -randkey ${principal}@${REALM}" >/dev/null
  sudo -n /usr/sbin/kadmin.local -q "ktadd -k ${keytab} ${principal}@${REALM}" >/dev/null
}

# Reuse existing build scripts so Kerberos builds Cloudberry and PXF from clean sources.
build_cloudberry() {
  log "build Cloudberry (kerberos)"
  log "cleanup stale gpdemo data and PG locks"
  rm -rf /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs
  rm -f /tmp/.s.PGSQL.700*
  sudo pkill -9 postgres || true
  find "${REPO_ROOT}/.." -maxdepth 1 -not -path '*/.git/*' -exec sudo chown gpadmin:gpadmin {} + 2>/dev/null || true
  "${PXF_SCRIPTS}/build_cloudberrry.sh"
}

build_pxf() {
  log "build PXF (kerberos)"
  "${PXF_SCRIPTS}/build_pxf.sh"
}

prepare_kdc() {
  log "configuring krb5.conf and kdc.conf"
  sudo -n tee /etc/krb5.conf >/dev/null <<EOF
[libdefaults]
 default_realm = ${REALM}
 dns_lookup_realm = false
 dns_lookup_kdc = false

[realms]
 ${REALM} = {
  kdc = ${HOST_FQDN}
  admin_server = ${HOST_FQDN}
 }

[domain_realm]
 .${HOST_FQDN#*.} = ${REALM}
 ${HOST_FQDN} = ${REALM}
EOF

  sudo -n tee /etc/krb5kdc/kdc.conf >/dev/null <<EOF
[kdcdefaults]
 kdc_ports = 88
 kdc_tcp_ports = 88

[realms]
 ${REALM} = {
  database_name = /var/lib/krb5kdc/principal
  admin_keytab = /etc/krb5kdc/kadm5.keytab
  acl_file = /etc/krb5kdc/kadm5.acl
  key_stash_file = /var/lib/krb5kdc/.k5.${REALM}
  max_life = 10h 0m 0s
  max_renewable_life = 7d 0h 0m 0s
  master_key_type = aes256-cts
  supported_enctypes = aes256-cts:normal aes128-cts:normal
 }
EOF

  echo "*/admin *" | sudo -n tee /etc/krb5kdc/kadm5.acl >/dev/null

  if ! sudo -n test -f /var/lib/krb5kdc/principal; then
    log "initializing KDC database"
    sudo -n /usr/sbin/kdb5_util create -s -P "${ADMIN_PASS}" >/dev/null
  else
    log "KDC database already present, skip init"
  fi

  sudo -n pkill krb5kdc || true
  sudo -n pkill kadmind || true
  sudo -n /usr/sbin/krb5kdc
  sudo -n /usr/sbin/kadmind
}

create_principals() {
  log "creating service principals/keytabs"
  ensure_principal "pxf/${HOST_FQDN}" "${PXF_KEYTAB}"
  ensure_principal "hdfs/${HOST_FQDN}" "${KEYTAB_DIR}/hdfs.keytab"
  ensure_principal "hive/${HOST_FQDN}" "${KEYTAB_DIR}/hive.keytab"
  ensure_principal "HTTP/${HOST_FQDN}" "${KEYTAB_DIR}/http.keytab"
  ensure_principal "yarn/${HOST_FQDN}" "${KEYTAB_DIR}/yarn.keytab"
  ensure_principal "hbase/${HOST_FQDN}" "${KEYTAB_DIR}/hbase.keytab"
  ensure_principal "postgres/${HOST_FQDN}" "${KEYTAB_DIR}/postgres.keytab"
  ensure_principal "gpadmin" "${KEYTAB_DIR}/gpadmin.keytab"
  ensure_principal "testuser" "${KEYTAB_DIR}/testuser.keytab"
  ensure_principal "porter" "${KEYTAB_DIR}/porter.keytab"
  sudo chown -R gpadmin:gpadmin "${KEYTAB_DIR}" "${PXF_KEYTAB}"
  sudo chmod 600 "${KEYTAB_DIR}"/*.keytab "${PXF_KEYTAB}"
}

setup_ssl_material() {
  log "ensuring SSL keystore/truststore"
  if [ ! -f "${SSL_KEYSTORE}" ]; then
    keytool -genkeypair -alias hadoop -keyalg RSA -keystore "${SSL_KEYSTORE}" \
      -storepass "${SSL_STOREPASS}" -keypass "${SSL_STOREPASS}" \
      -dname "CN=${HOST_FQDN},OU=PXF,O=PXF,L=PXF,ST=PXF,C=US" -validity 3650 >/dev/null 2>&1
  fi
  if [ ! -f "${SSL_TRUSTSTORE}" ]; then
    keytool -exportcert -alias hadoop -keystore "${SSL_KEYSTORE}" -storepass "${SSL_STOREPASS}" -rfc \
      | keytool -importcert -alias hadoop -keystore "${SSL_TRUSTSTORE}" -storepass "${SSL_STOREPASS}" -noprompt >/dev/null 2>&1
  fi
  sudo chown gpadmin:gpadmin "${SSL_KEYSTORE}" "${SSL_TRUSTSTORE}"
}

deploy_minio() {
  log "deploying MinIO (for S3 tests)"
  bash "${REPO_ROOT}/dev/start_minio.bash"
}

configure_pxf_s3() {
  log "configuring S3 server definitions for PXF"
  local servers_base=${PXF_BASE:-/home/gpadmin/pxf-base}
  local pxf_conf=/usr/local/pxf/conf
  local s3_sites=(
    "${servers_base}/servers/s3/s3-site.xml"
    "${servers_base}/servers/default/s3-site.xml"
    "${pxf_conf}/servers/s3/s3-site.xml"
  )
  for s3_site in "${s3_sites[@]}"; do
    mkdir -p "$(dirname "${s3_site}")"
    cat > "${s3_site}" <<'EOF'
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

prepare_sut() {
  # Generate SUT pointing to container FQDN and overwrite build outputs to avoid localhost.
  local host_fqdn_local=$1
  local sut_template=/home/gpadmin/workspace/cloudberry-pxf/automation/src/test/resources/sut/default.xml
  local sut_generated=/home/gpadmin/workspace/cloudberry-pxf/automation/temp_sut_security.xml
  if [ -f "${sut_template}" ]; then
    sed "s/localhost/${host_fqdn_local}/g" "${sut_template}" > "${sut_generated}"
    # Normalize workingDirectory to a top-level pxf_automation_data path to avoid sticky-bit issues under /tmp.
    python3 - "$sut_generated" <<'PY'
import sys, re
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
text = re.sub(r"<workingDirectory>tmp/pxf_automation_data</workingDirectory>",
              "<workingDirectory>pxf_automation_data</workingDirectory>", text)
open(path, "w", encoding="utf-8").write(text)
PY
    # Add Hive Kerberos principal if missing.
    if ! grep -q "<kerberosPrincipal>" "${sut_generated}"; then
      python3 - "$sut_generated" "$host_fqdn_local" "$REALM" <<'PY'
import sys, re
path, host, realm = sys.argv[1:]
text = open(path, encoding="utf-8").read()
def repl(match):
    block = match.group(0)
    if "<kerberosPrincipal>" in block:
        return block
    return block.replace("</port>", "</port>\n        <kerberosPrincipal>hive/%s@%s</kerberosPrincipal>" % (host, realm), 1)
out = re.sub(r"<hive>.*?</hive>", repl, text, flags=re.S)
open(path, "w", encoding="utf-8").write(out)
PY
    fi
    # Ensure HBase clients load the secure cluster config instead of using defaults.
    python3 - "$sut_generated" "${GPHD_ROOT:-/home/gpadmin/workspace/singlecluster}" <<'PY'
import sys, xml.etree.ElementTree as ET
path, gphd_root = sys.argv[1:]
tree = ET.parse(path); root = tree.getroot()
for h in root.findall("hbase"):
    if h.find("hbaseRoot") is None:
        el = ET.SubElement(h, "hbaseRoot")
        el.text = f"{gphd_root}/hbase"
tree.write(path)
PY
    mkdir -p /home/gpadmin/workspace/cloudberry-pxf/automation/target/test-classes/sut
    mkdir -p /home/gpadmin/workspace/cloudberry-pxf/automation/target/classes/sut
    cp "${sut_generated}" /home/gpadmin/workspace/cloudberry-pxf/automation/target/test-classes/sut/default.xml
    cp "${sut_generated}" /home/gpadmin/workspace/cloudberry-pxf/automation/target/classes/sut/default.xml
    # If IPA configs are missing, add local hdfsIpa/hiveIpa entries to avoid IPA group NPE.
    if ! grep -q "<hdfsIpa>" "${sut_generated}"; then
      python3 - "$sut_generated" "$host_fqdn_local" "$REALM" <<'PY'
import sys, xml.etree.ElementTree as ET
path, host, realm = sys.argv[1:]
tree = ET.parse(path); root = tree.getroot()
def add_block(tag, text_map):
    el = ET.SubElement(root, tag)
    for k,v in text_map.items():
        c = ET.SubElement(el, k); c.text = v
add_block("hdfsIpa", {
    "class":"org.greenplum.pxf.automation.components.hdfs.Hdfs",
    "host":host, "port":"8020",
    "workingDirectory":"pxf_automation_data/__UUID__",
    "hadoopRoot":f"{'/home/gpadmin/pxf-base'}/servers/hdfs-ipa",
    "scheme":"hdfs",
    "haNameservice":"",
    "testKerberosPrincipal":f"hdfs/{host}@{realm}",
    "testKerberosKeytab":f"/etc/security/keytabs/hdfs/{host}.headless.keytab"
})
add_block("hiveIpa", {
    "class":"org.greenplum.pxf.automation.components.hive.Hive",
    "host":host, "port":"10000",
    "kerberosPrincipal":f"hive/{host}@{realm}",
    "saslQop":"auth"
})
tree.write(path)
PY
      cp "${sut_generated}" /home/gpadmin/workspace/cloudberry-pxf/automation/target/test-classes/sut/default.xml
      cp "${sut_generated}" /home/gpadmin/workspace/cloudberry-pxf/automation/target/classes/sut/default.xml
    fi
    export SUT_FILE="${sut_generated}"
  else
    export SUT_FILE=${SUT_FILE:-default.xml}
  fi
}

prepare_hadoop_conf_for_tests() {
  local conf_dir=${HADOOP_CONF_DIR:-/home/gpadmin/workspace/singlecluster/hadoop/etc/hadoop}
  local target_base=${REPO_ROOT}/automation/target
  mkdir -p "${target_base}/test-classes" "${target_base}/classes"
  for f in core-site.xml hdfs-site.xml mapred-site.xml yarn-site.xml ssl-client.xml ssl-server.xml s3-site.xml; do
    if [ -f "${conf_dir}/${f}" ]; then
      cp "${conf_dir}/${f}" "${target_base}/test-classes/${f}"
      cp "${conf_dir}/${f}" "${target_base}/classes/${f}"
    fi
  done
  # Also place HBase config on classpath so HBase clients pick up Kerberos settings.
  local hbase_site="${conf_dir}/../hbase/conf/hbase-site.xml"
  if [ -f "${hbase_site}" ]; then
    cp "${hbase_site}" "${target_base}/test-classes/hbase-site.xml"
    cp "${hbase_site}" "${target_base}/classes/hbase-site.xml"
  fi
  # Add PXF server S3 configs to the classpath for automation tests.
  local pxf_s3="${PXF_BASE:-/home/gpadmin/pxf-base}/servers/s3/s3-site.xml"
  if [ -f "${pxf_s3}" ]; then
    cp "${pxf_s3}" "${target_base}/test-classes/s3-site.xml"
    cp "${pxf_s3}" "${target_base}/classes/s3-site.xml"
  fi
}

configure_hadoop() {
  log "writing core-site.xml / hdfs-site.xml"
  backup_file "${HADOOP_CONF_DIR}/core-site.xml"
  cat > "${HADOOP_CONF_DIR}/core-site.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>fs.defaultFS</name><value>hdfs://${HOST_FQDN}:8020</value></property>
  <property><name>hadoop.security.authentication</name><value>kerberos</value></property>
  <property><name>hadoop.security.authorization</name><value>true</value></property>
  <property><name>hadoop.rpc.protection</name><value>privacy</value></property>
  <property><name>hadoop.user.group.static.mapping.overrides</name><value>hdfs=supergroup;gpadmin=supergroup;hive=supergroup;hbase=supergroup;yarn=supergroup;pxf=supergroup</value></property>
  <property><name>fs.permissions.umask-mode</name><value>000</value></property>
  <property>
    <name>hadoop.security.auth_to_local</name>
    <value>
      RULE:[2:\$1@\$0](pxf/.*@${REALM})s/.*/pxf/
      RULE:[2:\$1@\$0](gpadmin/.*@${REALM})s/.*/gpadmin/
      RULE:[2:\$1@\$0](hdfs/.*@${REALM})s/.*/hdfs/
      RULE:[2:\$1@\$0](hive/.*@${REALM})s/.*/hive/
      RULE:[2:\$1@\$0](yarn/.*@${REALM})s/.*/yarn/
      RULE:[2:\$1@\$0](HTTP/.*@${REALM})s/.*/HTTP/
      RULE:[2:\$1@\$0](hbase/.*@${REALM})s/.*/hbase/
      DEFAULT
    </value>
  </property>
  <property><name>hadoop.proxyuser.pxf.hosts</name><value>*</value></property>
  <property><name>hadoop.proxyuser.pxf.groups</name><value>*</value></property>
  <property><name>hadoop.proxyuser.gpadmin.hosts</name><value>*</value></property>
  <property><name>hadoop.proxyuser.gpadmin.groups</name><value>*</value></property>
  <property><name>hadoop.proxyuser.porter.hosts</name><value>*</value></property>
  <property><name>hadoop.proxyuser.porter.groups</name><value>*</value></property>
  <property><name>fs.s3a.endpoint</name><value>http://localhost:9000</value></property>
  <property><name>fs.s3a.path.style.access</name><value>true</value></property>
  <property><name>fs.s3a.connection.ssl.enabled</name><value>false</value></property>
  <property><name>fs.s3a.access.key</name><value>${AWS_ACCESS_KEY_ID:-admin}</value></property>
  <property><name>fs.s3a.secret.key</name><value>${AWS_SECRET_ACCESS_KEY:-password}</value></property>
  <property><name>fs.s3a.aws.credentials.provider</name><value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value></property>
</configuration>
EOF

  backup_file "${HADOOP_CONF_DIR}/hdfs-site.xml"
  cat > "${HADOOP_CONF_DIR}/hdfs-site.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>dfs.permissions</name><value>true</value></property>
  <property><name>dfs.permissions.enabled</name><value>true</value></property>
  <property><name>dfs.permissions.superusergroup</name><value>supergroup</value></property>
  <property><name>dfs.support.append</name><value>true</value></property>
  <property><name>dfs.block.local-path-access.user</name><value>\${user.name}</value></property>
  <property><name>dfs.replication</name><value>1</value></property>
  <property><name>dfs.webhdfs.enabled</name><value>true</value></property>
  <property><name>dfs.namenode.kerberos.principal</name><value>hdfs/${HOST_FQDN}@${REALM}</value></property>
  <property><name>dfs.namenode.keytab.file</name><value>${KEYTAB_DIR}/hdfs.keytab</value></property>
  <property><name>dfs.datanode.kerberos.principal</name><value>hdfs/${HOST_FQDN}@${REALM}</value></property>
  <property><name>dfs.datanode.keytab.file</name><value>${KEYTAB_DIR}/hdfs.keytab</value></property>
  <property><name>dfs.web.authentication.kerberos.principal</name><value>HTTP/${HOST_FQDN}@${REALM}</value></property>
  <property><name>dfs.web.authentication.kerberos.keytab</name><value>${KEYTAB_DIR}/http.keytab</value></property>
  <property><name>dfs.block.access.token.enable</name><value>true</value></property>
  <property><name>dfs.data.transfer.protection</name><value>authentication,privacy</value></property>
  <property><name>dfs.encrypt.data.transfer</name><value>true</value></property>
  <property><name>dfs.datanode.address</name><value>0.0.0.0:1004</value></property>
  <property><name>dfs.datanode.http.address</name><value>0.0.0.0:1006</value></property>
  <property><name>dfs.datanode.https.address</name><value>0.0.0.0:1008</value></property>
  <property><name>dfs.datanode.ipc.address</name><value>0.0.0.0:1009</value></property>
  <property><name>dfs.http.policy</name><value>HTTPS_ONLY</value></property>
  <property><name>dfs.namenode.https-address</name><value>0.0.0.0:50470</value></property>
  <property><name>dfs.namenode.http-address</name><value>0.0.0.0:9870</value></property>
</configuration>
EOF

  backup_file "${HADOOP_CONF_DIR}/ssl-server.xml"
  cat > "${HADOOP_CONF_DIR}/ssl-server.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>ssl.server.keystore.location</name><value>${SSL_KEYSTORE}</value></property>
  <property><name>ssl.server.keystore.password</name><value>${SSL_STOREPASS}</value></property>
  <property><name>ssl.server.key.password</name><value>${SSL_STOREPASS}</value></property>
  <property><name>ssl.server.truststore.location</name><value>${SSL_TRUSTSTORE}</value></property>
  <property><name>ssl.server.truststore.password</name><value>${SSL_STOREPASS}</value></property>
</configuration>
EOF

  backup_file "${HADOOP_CONF_DIR}/ssl-client.xml"
  cat > "${HADOOP_CONF_DIR}/ssl-client.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>ssl.client.truststore.location</name><value>${SSL_TRUSTSTORE}</value></property>
  <property><name>ssl.client.truststore.password</name><value>${SSL_STOREPASS}</value></property>
  <property><name>ssl.client.keystore.location</name><value>${SSL_KEYSTORE}</value></property>
  <property><name>ssl.client.keystore.password</name><value>${SSL_STOREPASS}</value></property>
  <property><name>ssl.client.keystore.keypassword</name><value>${SSL_STOREPASS}</value></property>
</configuration>
EOF
}

configure_yarn() {
  log "writing yarn-site.xml"
  backup_file "${YARN_CONF_DIR}/yarn-site.xml"
  cat > "${YARN_CONF_DIR}/yarn-site.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>yarn.resourcemanager.principal</name><value>yarn/${HOST_FQDN}@${REALM}</value></property>
  <property><name>yarn.resourcemanager.keytab</name><value>${KEYTAB_DIR}/yarn.keytab</value></property>
  <property><name>yarn.nodemanager.principal</name><value>yarn/${HOST_FQDN}@${REALM}</value></property>
  <property><name>yarn.nodemanager.keytab</name><value>${KEYTAB_DIR}/yarn.keytab</value></property>
  <property><name>yarn.nodemanager.container-executor.class</name><value>org.apache.hadoop.yarn.server.nodemanager.DefaultContainerExecutor</value></property>
  <property><name>yarn.nodemanager.container-manager.thread-count</name><value>20</value></property>
</configuration>
EOF
}

configure_hive() {
  log "writing hive-site.xml"
  backup_file "${HIVE_CONF_DIR}/hive-site.xml"
  cat > "${HIVE_CONF_DIR}/hive-site.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>javax.jdo.option.ConnectionURL</name><value>jdbc:derby:;databaseName=${GPHD_ROOT}/storage/hive/metastore_db;create=true</value></property>
  <property><name>javax.jdo.option.ConnectionDriverName</name><value>org.apache.derby.jdbc.EmbeddedDriver</value></property>
  <property><name>javax.jdo.PersistenceManagerFactoryClass</name><value>org.datanucleus.api.jdo.JDOPersistenceManagerFactory</value></property>
  <property><name>datanucleus.fixedDatastore</name><value>false</value></property>
  <property><name>datanucleus.autoCreateSchema</name><value>true</value></property>
  <property><name>datanucleus.autoCreateTables</name><value>true</value></property>
  <property><name>hive.metastore.schema.verification</name><value>false</value></property>
  <property><name>hive.metastore.schema.verification.record.version</name><value>false</value></property>
  <property><name>hive.metastore.warehouse.dir</name><value>hdfs://${HOST_FQDN}:8020/hive/warehouse</value></property>
  <property><name>hive.metastore.uris</name><value>thrift://${HOST_FQDN}:9083</value></property>
  <property><name>hive.metastore.sasl.enabled</name><value>false</value></property>
  <property><name>hive.metastore.execute.setugi</name><value>false</value></property>
  <property><name>hive.metastore.kerberos.principal</name><value>hive/${HOST_FQDN}@${REALM}</value></property>
  <property><name>hive.metastore.kerberos.keytab.file</name><value>${KEYTAB_DIR}/hive.keytab</value></property>
  <property><name>hive.server2.authentication</name><value>KERBEROS</value></property>
  <property><name>hive.server2.authentication.kerberos.principal</name><value>hive/${HOST_FQDN}@${REALM}</value></property>
  <property><name>hive.server2.authentication.kerberos.keytab</name><value>${KEYTAB_DIR}/hive.keytab</value></property>
  <property><name>hive.server2.authentication.spnego.principal</name><value>HTTP/${HOST_FQDN}@${REALM}</value></property>
  <property><name>hive.server2.authentication.spnego.keytab</name><value>${KEYTAB_DIR}/http.keytab</value></property>
  <property><name>hive.server2.thrift.sasl.qop</name><value>auth</value></property>
  <property><name>hive.server2.thrift.bind.host</name><value>0.0.0.0</value></property>
  <property><name>hive.server2.thrift.port</name><value>10000</value></property>
  <property><name>hive.server2.enable.doAs</name><value>false</value></property>
  <property><name>hive.server2.transport.mode</name><value>binary</value></property>
  <property><name>hive.metastore.event.db.notification.api.auth</name><value>false</value></property>
  <property><name>hive.metastore.notification.api.enabled</name><value>false</value></property>
  <property><name>hive.metastore.notifications.add.state</name><value>false</value></property>
</configuration>
EOF

  log "writing hive-env.sh"
  cat > "${HIVE_CONF_DIR}/hive-env.sh" <<EOF
# load singlecluster environment
. ${GPHD_ROOT}/bin/gphd-env.sh

export HADOOP_HOME=${HADOOP_HOME}
export HADOOP_CONF_DIR=${HADOOP_CONF_DIR}
export HIVE_HOME=${HIVE_HOME}
export HIVE_CONF_DIR=${HIVE_CONF_DIR}
export HIVE_OPTS="-hiveconf derby.stream.error.file=${GPHD_ROOT}/storage/logs/derby.log -hiveconf javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=${GPHD_ROOT}/storage/hive/metastore_db;create=true"
export HIVE_SERVER_OPTS="-hiveconf derby.stream.error.file=${GPHD_ROOT}/storage/logs/derby.log -hiveconf javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=${GPHD_ROOT}/storage/hive/metastore_db;create=true"
export HADOOP_CLASSPATH="\$TEZ_CONF:\$TEZ_JARS:\$HADOOP_CLASSPATH"
EOF
}

configure_hbase() {
  log "writing hbase-site.xml"
  backup_file "${HBASE_CONF_DIR}/hbase-site.xml"
  cat > "${HBASE_CONF_DIR}/hbase-site.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>hbase.rootdir</name><value>hdfs://${HOST_FQDN}:8020/hbase</value></property>
  <property><name>hbase.cluster.distributed</name><value>true</value></property>
  <property><name>hbase.zookeeper.quorum</name><value>${HOST_FQDN}</value></property>
  <property><name>hbase.zookeeper.property.clientPort</name><value>2181</value></property>
  <property><name>zookeeper.znode.parent</name><value>/hbase</value></property>
  <property><name>hbase.security.authentication</name><value>kerberos</value></property>
  <property><name>hbase.security.authorization</name><value>false</value></property>
  <property><name>hbase.superuser</name><value>hbase,hdfs,gpadmin,pxf</value></property>
  <property><name>hbase.master.kerberos.principal</name><value>hbase/_HOST@${REALM}</value></property>
  <property><name>hbase.master.keytab.file</name><value>${KEYTAB_DIR}/hbase.keytab</value></property>
  <property><name>hbase.regionserver.kerberos.principal</name><value>hbase/_HOST@${REALM}</value></property>
  <property><name>hbase.regionserver.keytab.file</name><value>${KEYTAB_DIR}/hbase.keytab</value></property>
  <property><name>hbase.procedure.store.wal.use.hsync</name><value>false</value></property>
  <property><name>hbase.procedure.store.wal.sync.failure.fatal</name><value>false</value></property>
  <property><name>hbase.unsafe.stream.capability.enforce</name><value>false</value></property>
  <property><name>hbase.procedure.store.type</name><value>wal</value></property>
  <property><name>hbase.wal.dir</name><value>hdfs://${HOST_FQDN}:8020/walroot</value></property>
  <property><name>hbase.procedure.store.wal.dir</name><value>hdfs://${HOST_FQDN}:8020/walroot</value></property>
  <property><name>hbase.wal.provider</name><value>filesystem</value></property>
</configuration>
EOF
  # Ensure HBase picks up Hadoop security configs
  cp -f "${HADOOP_CONF_DIR}/core-site.xml" "${HBASE_CONF_DIR}/core-site.xml"
  cp -f "${HADOOP_CONF_DIR}/hdfs-site.xml" "${HBASE_CONF_DIR}/hdfs-site.xml"
  # Relax WAL requirements further via env to be safe on single-node dev FS.
  if ! grep -q "hbase.procedure.store.wal.use.hsync" "${HBASE_CONF_DIR}/hbase-env.sh"; then
    cat >> "${HBASE_CONF_DIR}/hbase-env.sh" <<'EOF'
# Prefer async WAL but allow fallback without fatal errors (dev-only).
export HBASE_OPTS="$HBASE_OPTS -Dhbase.procedure.store.wal.use.hsync=false -Dhbase.procedure.store.wal.sync.failure.fatal=false"
EOF
  fi
}

configure_pxf() {
  log "writing pxf-site.xml"
  backup_file "/usr/local/pxf/conf/pxf-site.xml"
  cat > /usr/local/pxf/conf/pxf-site.xml <<EOF
<?xml version="1.0"?>
<configuration>
  <property><name>pxf.service.kerberos.principal</name><value>pxf/${HOST_FQDN}@${REALM}</value></property>
  <property><name>pxf.service.kerberos.keytab</name><value>${PXF_KEYTAB}</value></property>
  <property><name>pxf.fs.basePath</name><value>${PXF_FS_BASE_PATH}</value></property>
</configuration>
EOF

  # Make PXF listen on all interfaces so health checks can reach the actuator.
  if grep -q "^# server.address" /usr/local/pxf/conf/pxf-application.properties; then
    sed -i 's/^# server.address.*/server.address=0.0.0.0/' /usr/local/pxf/conf/pxf-application.properties
  elif ! grep -q "^server.address" /usr/local/pxf/conf/pxf-application.properties; then
    echo "server.address=0.0.0.0" >> /usr/local/pxf/conf/pxf-application.properties
  fi

  # Ensure JAVA_HOME is set for PXF CLI/runtime.
  if grep -q "^# export JAVA_HOME" /usr/local/pxf/conf/pxf-env.sh; then
    sed -i "s|^# export JAVA_HOME.*|export JAVA_HOME=${JAVA_HOME}|" /usr/local/pxf/conf/pxf-env.sh
  elif ! grep -q "^export JAVA_HOME=" /usr/local/pxf/conf/pxf-env.sh; then
    echo "export JAVA_HOME=${JAVA_HOME}" >> /usr/local/pxf/conf/pxf-env.sh
  fi
  # Force principal/keytab at JVM level to survive any config reload quirks.
  local jvm_override="-Dpxf.service.kerberos.principal=pxf/${HOST_FQDN}@${REALM} -Dpxf.service.kerberos.keytab=${PXF_KEYTAB} -Dpxf.fs.basePath=${PXF_FS_BASE_PATH} -Ddfs.namenode.kerberos.principal=hdfs/${HOST_FQDN}@${REALM}"
  if grep -q "^export PXF_JVM_OPTS=" /usr/local/pxf/conf/pxf-env.sh; then
    sed -i "s|^export PXF_JVM_OPTS=.*|export PXF_JVM_OPTS=\"${jvm_override} ${PXF_JVM_OPTS:-}\"|" /usr/local/pxf/conf/pxf-env.sh
  else
    echo "export PXF_JVM_OPTS=\"${jvm_override} ${PXF_JVM_OPTS:-}\"" >> /usr/local/pxf/conf/pxf-env.sh
  fi
  # Ensure basePath also available as env override for older code paths.
  if ! grep -q "^export PXF_FS_BASE_PATH=" /usr/local/pxf/conf/pxf-env.sh; then
    echo "export PXF_FS_BASE_PATH=${PXF_FS_BASE_PATH}" >> /usr/local/pxf/conf/pxf-env.sh
  fi
  if ! grep -q "^export PXF_PRINCIPAL=" /usr/local/pxf/conf/pxf-env.sh; then
    echo "export PXF_PRINCIPAL=pxf/${HOST_FQDN}@${REALM}" >> /usr/local/pxf/conf/pxf-env.sh
  fi
  if ! grep -q "^export PXF_KEYTAB=" /usr/local/pxf/conf/pxf-env.sh; then
    echo "export PXF_KEYTAB=${PXF_KEYTAB}" >> /usr/local/pxf/conf/pxf-env.sh
  fi
  if ! grep -q "^export PXF_USER=" /usr/local/pxf/conf/pxf-env.sh; then
    echo "export PXF_USER=gpadmin" >> /usr/local/pxf/conf/pxf-env.sh
  fi
  # Force fs.defaultFS and basePath at JVM level to avoid blank configs causing skips.
  if ! grep -q "PXF_JVM_OPTS" /usr/local/pxf/conf/pxf-env.sh; then
    echo "export PXF_JVM_OPTS=\"-Dfs.defaultFS=hdfs://${HOST_FQDN}:8020 -Dpxf.fs.basePath=/tmp/pxf_automation_data -Djava.security.krb5.conf=/etc/krb5.conf\"" >> /usr/local/pxf/conf/pxf-env.sh
  elif ! grep -q "pxf.fs.basePath" /usr/local/pxf/conf/pxf-env.sh; then
    echo "export PXF_JVM_OPTS=\"\${PXF_JVM_OPTS} -Dfs.defaultFS=hdfs://${HOST_FQDN}:8020 -Dpxf.fs.basePath=/tmp/pxf_automation_data -Djava.security.krb5.conf=/etc/krb5.conf\"" >> /usr/local/pxf/conf/pxf-env.sh
  fi
  # Copy Hadoop client configs into the global PXF conf so defaultFS is not file://
  if [ -f "${HADOOP_CONF_DIR}/core-site.xml" ]; then
    sudo cp -f "${HADOOP_CONF_DIR}/core-site.xml" /usr/local/pxf/conf/core-site.xml
  fi
  if [ -f "${HADOOP_CONF_DIR}/hdfs-site.xml" ]; then
    sudo cp -f "${HADOOP_CONF_DIR}/hdfs-site.xml" /usr/local/pxf/conf/hdfs-site.xml
  fi
}

# Copy/generate PXF server configs to avoid proxy tests failing for missing servers.
configure_pxf_servers() {
  local servers_base=${PXF_BASE:-/home/gpadmin/pxf-base}
  local pxf_home=/usr/local/pxf
  local src_conf=${HADOOP_CONF_DIR:-/home/gpadmin/workspace/singlecluster/hadoop/etc/hadoop}
  local hive_conf=${HIVE_CONF_DIR:-${src_conf}}
  local host_fqdn=${HOST_FQDN:-$(hostname -f)}
  local hdfs_uri=${HDFS_URI:-"hdfs://${host_fqdn}:8020"}
  # Use absolute path (no scheme) so older plugins don't reject basePath.
  local pxf_base_path=${PXF_FS_BASE_PATH:-/tmp/pxf_automation_data}
  local extra_servers=("hdfs-ipa" "hdfs-ipa-no-impersonation" "hdfs-ipa-no-impersonation-no-svcuser")
  for base in "${servers_base}" "${pxf_home}/conf"; do
    mkdir -p "${base}/servers/default" "${base}/servers/default-no-impersonation"
    for s in "${extra_servers[@]}"; do
      mkdir -p "${base}/servers/${s}"
    done
    # Prefer real cluster Kerberos configs so PXF talks to HDFS/Hive securely.
    for f in core-site.xml hdfs-site.xml mapred-site.xml yarn-site.xml hbase-site.xml hive-site.xml; do
      local src_file="${src_conf}/${f}"
      # hive-site lives under the Hive conf directory; handle separately.
      if [ "${f}" = "hive-site.xml" ] && [ -f "${hive_conf}/${f}" ]; then
        src_file="${hive_conf}/${f}"
      fi
      if [ -f "${src_file}" ]; then
        for s in default default-no-impersonation "${extra_servers[@]}"; do
          cp -f "${src_file}" "${base}/servers/${s}/${f}"
        done
      elif [ -f "${pxf_home}/templates/${f}" ]; then
        for s in default default-no-impersonation "${extra_servers[@]}"; do
          cp -f "${pxf_home}/templates/${f}" "${base}/servers/${s}/${f}"
        done
      fi
    done
    # Ensure pxf-site.xml exists.
    for server_dir in "${base}/servers/default" "${base}/servers/default-no-impersonation" "${base}/servers/hdfs-ipa" "${base}/servers/hdfs-ipa-no-impersonation" "${base}/servers/hdfs-ipa-no-impersonation-no-svcuser"; do
      if [ ! -f "${server_dir}/pxf-site.xml" ]; then
        cat > "${server_dir}/pxf-site.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
</configuration>
XML
      fi
      # Set service principal per server type (porter for IPA).
      local principal_value="pxf/${HOST_FQDN}@${REALM}"
      local keytab_value="${PXF_KEYTAB}"
      case "${server_dir}" in
        *hdfs-ipa*)
          principal_value="porter@${REALM}"
          keytab_value="${KEYTAB_DIR}/porter.keytab"
          ;;
      esac
      python3 - "${server_dir}/pxf-site.xml" "${principal_value}" "${keytab_value}" "${pxf_base_path}" <<'PY'
import sys, xml.etree.ElementTree as ET
path, principal, keytab, base_path = sys.argv[1:]
tree = ET.parse(path)
root = tree.getroot()
def set_prop(name, value):
    for prop in root.findall("property"):
        name_el = prop.find("name")
        if name_el is not None and name_el.text == name:
            val_el = prop.find("value")
            if val_el is None:
                val_el = ET.SubElement(prop, "value")
            val_el.text = value
            return
    prop = ET.SubElement(root, "property")
    ET.SubElement(prop, "name").text = name
    ET.SubElement(prop, "value").text = value
for name, value in (
    ("pxf.service.kerberos.principal", principal),
    ("pxf.service.kerberos.keytab", keytab),
    ("pxf.fs.basePath", base_path),
):
    set_prop(name, value)
tree.write(path)
PY
    done
    # Configure service user for no-impersonation servers.
    if ! grep -q "pxf.service.user.impersonation" "${base}/servers/default-no-impersonation/pxf-site.xml"; then
      sed -i 's#</configuration>#  <property>\n    <name>pxf.service.user.name</name>\n    <value>foobar</value>\n  </property>\n  <property>\n    <name>pxf.service.user.impersonation</name>\n    <value>false</value>\n  </property>\n</configuration>#' "${base}/servers/default-no-impersonation/pxf-site.xml"
    fi
    for server_dir in "${base}/servers/hdfs-ipa-no-impersonation"; do
      if ! grep -q "pxf.service.user.impersonation" "${server_dir}/pxf-site.xml"; then
        sed -i 's#</configuration>#  <property>\n    <name>pxf.service.user.name</name>\n    <value>foobar</value>\n  </property>\n  <property>\n    <name>pxf.service.user.impersonation</name>\n    <value>false</value>\n  </property>\n</configuration>#' "${server_dir}/pxf-site.xml"
      fi
    done
    # no-svcuser server relies on the Kerberos principal only; omit service user.
    for server_dir in "${base}/servers/hdfs-ipa-no-impersonation-no-svcuser"; do
      python3 - "${server_dir}/pxf-site.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
path = sys.argv[1]
tree = ET.parse(path); root = tree.getroot()
names_to_drop = {"pxf.service.user.name", "pxf.service.user.impersonation"}
for prop in list(root.findall("property")):
    name_el = prop.find("name")
    if name_el is not None and name_el.text in names_to_drop:
        root.remove(prop)
# Keep non-impersonation mode without specifying service user.
prop = ET.SubElement(root, "property")
ET.SubElement(prop, "name").text = "pxf.service.user.impersonation"
ET.SubElement(prop, "value").text = "false"
tree.write(path)
PY
    done
  done
}

configure_pg_hba() {
  log "updating pg_hba for GSS"
  backup_file "${PG_HBA}"
  if [ ! -f "${PG_HBA}" ]; then
    mkdir -p "$(dirname "${PG_HBA}")"
    touch "${PG_HBA}"
  fi
  # Force test trust rules to the top to avoid GSS failures.
  local tmp_pg_hba
  tmp_pg_hba=$(mktemp)
  {
    echo "host all all 127.0.0.1/32 gss include_realm=0 krb_realm=${REALM}"
    echo "host all all 0.0.0.0/0 trust"
    echo "host all all ::/0 trust"
    echo "host all all 172.18.0.0/16 trust"
    grep -v "mdw/32 trust" "${PG_HBA}" || true
  } | awk '!seen[$0]++' | sudo tee "${tmp_pg_hba}" >/dev/null
  sudo mv "${tmp_pg_hba}" "${PG_HBA}"
  # Reload cluster so new HBA rules take effect immediately for test users.
  if [ -n "${MASTER_DATA_DIRECTORY}" ] && [ -x "${GPHOME}/bin/pg_ctl" ]; then
    sudo -u gpadmin env MASTER_DATA_DIRECTORY=${MASTER_DATA_DIRECTORY} GPHOME=${GPHOME} "${GPHOME}/bin/pg_ctl" reload -D "${MASTER_DATA_DIRECTORY}" >/dev/null 2>&1 || true
  fi
}

ensure_gpdb_databases() {
  local host=${1:-${PGHOST:-mdw}}
  local port=${2:-${PGPORT:-7000}}
  local gphome=${3:-${GPHOME:-/usr/local/cloudberry-db}}
  local mdd=$4
  local psql_bin="${gphome}/bin/psql"
  local createdb_bin="${gphome}/bin/createdb"
  local env_path="PATH=${gphome}/bin:${PATH}"
  local conn_flags=(-h "${host}" -p "${port}" -U gpadmin)

  if [ ! -x "${psql_bin}" ] || [ ! -x "${createdb_bin}" ]; then
    log "psql/createdb not found under ${gphome}, skip DB bootstrap"
    return 0
  fi

  log "ensuring gpdb databases pxfautomation & pxfautomation_encoding"
  if ! sudo -u gpadmin env ${env_path} "${psql_bin}" "${conn_flags[@]}" -d postgres -tAc "select 1 from pg_database where datname='pxfautomation'" >/dev/null 2>&1; then
    sudo -u gpadmin env ${env_path} "${createdb_bin}" "${conn_flags[@]}" pxfautomation >/dev/null 2>&1 || true
  fi

  if ! sudo -u gpadmin env ${env_path} "${psql_bin}" "${conn_flags[@]}" -d postgres -tAc "select 1 from pg_database where datname='pxfautomation_encoding'" >/dev/null 2>&1; then
    # Prefer WIN1251 with template0 and C locale (locale may be absent in container).
    sudo -u gpadmin env ${env_path} "${createdb_bin}" "${conn_flags[@]}" -T template0 -E WIN1251 --lc-collate=C --lc-ctype=C pxfautomation_encoding >/tmp/pxf_createdb.log 2>&1 || \
      sudo -u gpadmin env ${env_path} "${createdb_bin}" "${conn_flags[@]}" -E UTF8 pxfautomation_encoding >/dev/null 2>&1 || true
  fi

  sudo -u gpadmin env MASTER_DATA_DIRECTORY="${mdd}" GPHOME="${gphome}" "${gphome}/bin/pg_ctl" reload -D "${mdd}" >/dev/null 2>&1 || true
}

verify_security_mode() {
  log "verifying Kerberos configs are active"
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
    ${GPHD_ROOT}/hadoop/bin/hdfs getconf -confKey hadoop.security.authentication
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
    ${GPHD_ROOT}/hadoop/bin/hdfs getconf -confKey dfs.data.transfer.protection
  sudo -u gpadmin grep -E "yarn.resourcemanager.principal|yarn.nodemanager.principal" "${YARN_CONF_DIR}/yarn-site.xml" || true
  sudo -u gpadmin grep -E "hbase.security.authentication" "${HBASE_CONF_DIR}/hbase-site.xml" || true
}

start_hdfs_secure() {
  log "start HDFS (kerberos) + prepare dirs"
  pushd "${GPHD_ROOT}" >/dev/null
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./bin/stop-hdfs.sh >/dev/null 2>&1 || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./bin/stop-yarn.sh >/dev/null 2>&1 || true
  sudo rm -rf "${GPHD_ROOT}/storage/pids" "${GPHD_ROOT}/storage/logs"/*/hadoop-*.pid || true
  sudo rm -rf "${GPHD_ROOT}/storage/zookeeper" || true
  # Clean datanode data to avoid clusterID mismatch blocking datanode.
  sudo rm -rf "${GPHD_ROOT}/storage/hadoop" || true
  if [ ! -f "${GPHD_ROOT}/storage/hadoop/current/VERSION" ]; then
    sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} ./bin/init-gphd.sh >/dev/null 2>&1 || true
    sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs namenode -format -force -nonInteractive >/dev/null 2>&1 || true
  fi
  set +e
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./bin/start-hdfs.sh
  rc_hdfs=$?
  set -e
  log "start-hdfs.sh exited with ${rc_hdfs} (ignored if non-zero); continuing to set permissions"
  for i in {1..20}; do
    if sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfsadmin -safemode leave >/dev/null 2>&1; then
      break
    fi
    sleep 3
  done
  sudo -u gpadmin kinit -kt "${KEYTAB_DIR}/hdfs.keytab" "hdfs/${HOST_FQDN}@${REALM}" || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -mkdir -p /tmp /hbase /tmp/hive /tmp/hive/_resultscache_ /user/hive/warehouse || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -chmod 1777 /tmp /tmp/hive /tmp/hive/_resultscache_ || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -chown -R hive:hive /tmp/hive /user/hive || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -chown -R hbase:hbase /hbase || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -mkdir -p /pxf_automation_data /pxf_automation_data/proxy/gpadmin /pxf_automation_data/proxy/testuser /pxf_automation_data/proxy/OTHER_USER || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -chmod -R 777 /pxf_automation_data || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -setfacl -R -m user:hbase:rwx /tmp/hive /tmp/hive/_resultscache_ >/dev/null 2>&1 || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -mkdir -p /apps/tez || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -chown -R hive:hive /apps || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./hadoop/bin/hdfs dfs -chmod -R 755 /apps || true
  popd >/dev/null
}

start_hive_secure() {
  log "start Hive metastore/HS2 (kerberos)"
  # Kill leftover metastore / HS2 to avoid Derby locks blocking new instances.
  sudo pkill -f HiveMetaStore || true
  sudo pkill -f HiveServer2 || true
  sudo -u gpadmin kinit -kt "${KEYTAB_DIR}/hive.keytab" "hive/${HOST_FQDN}@${REALM}" || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_HOME=${HADOOP_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
    "${HIVE_HOME}/bin/schematool" -dbType derby -initSchema -verbose >/tmp/hive_schematool.log 2>&1 || true
  pushd "${GPHD_ROOT}" >/dev/null
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} ./bin/start-hive.sh || true
  popd >/dev/null
}

start_hbase_secure() {
  log "start HBase (kerberos)"
  # use hdfs superuser to prepare WAL dirs
  sudo -u gpadmin kinit -kt "${KEYTAB_DIR}/hdfs.keytab" "hdfs/${HOST_FQDN}@${REALM}" || true
  # Clean stale procedure/WAL files to avoid invalid trailer versions and ensure HDFS-backed WALs.
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ${GPHD_ROOT}/hadoop/bin/hdfs dfs -rm -r -f /walroot /hbase/oldWALs >/dev/null 2>&1 || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ${GPHD_ROOT}/hadoop/bin/hdfs dfs -mkdir -p /hbase /walroot || true
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ${GPHD_ROOT}/hadoop/bin/hdfs dfs -chown -R hbase:hbase /hbase /walroot || true
  sudo -u gpadmin kinit -kt "${KEYTAB_DIR}/hbase.keytab" "hbase/${HOST_FQDN}@${REALM}" || true
  # Clean stray ZK/HBase processes and pids to avoid "Master not running".
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} "${GPHD_ROOT}/bin/stop-zookeeper.sh" >/dev/null 2>&1 || true
  sudo pkill -f HMaster || true
  sudo pkill -f HRegionServer || true
  sudo pkill -f QuorumPeerMain || true
  sudo rm -f "${GPHD_ROOT}/zookeeper/zookeeper_server.pid" "${STORAGE_ROOT}/pids/zookeeper_server.pid" "${GPHD_ROOT}/storage/pids/zookeeper_server.pid" || true
  sudo rm -rf "${STORAGE_ROOT}/zookeeper/version-2" || true
  # Try starting ZK multiple times to ensure port 2181 is reachable.
  for i in {1..3}; do
    sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} "${GPHD_ROOT}/bin/start-zookeeper.sh" || true
    if wait_for_port "127.0.0.1" 2181 15 2; then
      break
    fi
    sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} "${GPHD_ROOT}/bin/stop-zookeeper.sh" >/dev/null 2>&1 || true
    sudo rm -f "${GPHD_ROOT}/zookeeper/zookeeper_server.pid" "${STORAGE_ROOT}/pids/zookeeper_server.pid" "${GPHD_ROOT}/storage/pids/zookeeper_server.pid" || true
    sudo rm -rf "${STORAGE_ROOT}/zookeeper/version-2" || true
  done
  wait_for_port "127.0.0.1" 2181 30 2 || log "WARN: zookeeper on 2181 not reachable, HBase may fail"
  pushd "${GPHD_ROOT}" >/dev/null
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} ./bin/start-hbase.sh || true
  # If the built-in start script didn't bring up services, try again explicitly.
  if ! wait_for_port "${HOST_FQDN}" 16000 20 2; then
    sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HBASE_HOME=${GPHD_ROOT}/hbase HBASE_CONF_DIR=${HBASE_CONF_DIR} HADOOP_HOME=${GPHD_ROOT}/hadoop HADOOP_CONF_DIR=${HADOOP_CONF_DIR} GPHD_ROOT=${GPHD_ROOT} STORAGE_ROOT=${GPHD_ROOT}/storage ${GPHD_ROOT}/hbase/bin/hbase-daemon.sh --config ${HBASE_CONF_DIR} start master || true
  fi
  if ! wait_for_port "${HOST_FQDN}" 16020 20 2; then
    sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HBASE_HOME=${GPHD_ROOT}/hbase HBASE_CONF_DIR=${HBASE_CONF_DIR} HADOOP_HOME=${GPHD_ROOT}/hadoop HADOOP_CONF_DIR=${HADOOP_CONF_DIR} GPHD_ROOT=${GPHD_ROOT} STORAGE_ROOT=${GPHD_ROOT}/storage ${GPHD_ROOT}/hbase/bin/hbase-daemon.sh --config ${HBASE_CONF_DIR} start regionserver || true
  fi
  wait_for_port "${HOST_FQDN}" 16000 40 2 || log "WARN: HMaster port 16000 not up yet"
  wait_for_port "${HOST_FQDN}" 16020 40 2 || log "WARN: RegionServer port 16020 not up yet"
  # Wait a bit so master fully comes up, avoiding later ConnectionClosingException.
  sleep 15
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HBASE_HOME=${GPHD_ROOT}/hbase HBASE_CONF_DIR=${HBASE_CONF_DIR} HADOOP_HOME=${GPHD_ROOT}/hadoop HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ${GPHD_ROOT}/hbase/bin/hbase shell -n -e "status 'simple'" >/tmp/hbase_status.log 2>&1 || true
  # Ensure ACL table exists so AccessController grant/revoke calls succeed.
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HBASE_HOME=${GPHD_ROOT}/hbase HBASE_CONF_DIR=${HBASE_CONF_DIR} HADOOP_HOME=${GPHD_ROOT}/hadoop HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ${GPHD_ROOT}/hbase/bin/hbase shell -n -e "create 'hbase:acl','l'" >/tmp/hbase_acl_create.log 2>&1 || true
  popd >/dev/null
}

start_yarn_secure() {
  log "start YARN (kerberos)"
  pushd "${GPHD_ROOT}" >/dev/null
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} ./bin/start-yarn.sh || true
  popd >/dev/null
}

start_pxf_secure() {
  log "start PXF (kerberos)"
  # Stop any stale PXF instance to free the actuator port.
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} PGPORT=${PGPORT:-7000} PGHOST=${HOST_FQDN} PGDATABASE=${PGDATABASE:-postgres}     PXF_BASE=${PXF_BASE} GPHOME=${GPHOME} /usr/local/pxf/bin/pxf cluster stop >/dev/null 2>&1 || true
  sudo pkill -f pxf-app || true
  sudo rm -f /home/gpadmin/pxf-base/run/pxf-service.pid || true
  sudo -u gpadmin rm -rf "${PXF_BASE}"
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} PGPORT=${PGPORT:-7000} PGHOST=${HOST_FQDN} PGDATABASE=${PGDATABASE:-postgres}     PXF_BASE=${PXF_BASE} GPHOME=${GPHOME} /usr/local/pxf/bin/pxf cluster prepare
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} PGPORT=${PGPORT:-7000} PGHOST=${HOST_FQDN} PGDATABASE=${PGDATABASE:-postgres}     PXF_BASE=${PXF_BASE} GPHOME=${GPHOME} /usr/local/pxf/bin/pxf cluster init
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} PGPORT=${PGPORT:-7000} PGHOST=${HOST_FQDN} PGDATABASE=${PGDATABASE:-postgres}     PXF_BASE=${PXF_BASE} GPHOME=${GPHOME} /usr/local/pxf/bin/pxf cluster start
}

security_health_check() {
  log "verifying Kerberos configs and service health"
  # Refresh PXF client configs and tickets to avoid login failures.
  if [ -f "${HADOOP_CONF_DIR}/core-site.xml" ]; then
    sudo cp -f "${HADOOP_CONF_DIR}/core-site.xml" /usr/local/pxf/conf/core-site.xml
  fi
  if [ -f "${HADOOP_CONF_DIR}/hdfs-site.xml" ]; then
    sudo cp -f "${HADOOP_CONF_DIR}/hdfs-site.xml" /usr/local/pxf/conf/hdfs-site.xml
  fi
  if [ -f "${PXF_KEYTAB}" ]; then
    kinit -kt "${PXF_KEYTAB}" "pxf/${HOST_FQDN}@${REALM}" || true
  fi
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} PGPORT=${PGPORT:-7000} PGHOST=${HOST_FQDN} PGDATABASE=${PGDATABASE:-postgres} PXF_BASE=${PXF_BASE} GPHOME=${GPHOME} /usr/local/pxf/bin/pxf cluster restart || true

  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
    ${GPHD_ROOT}/hadoop/bin/hdfs getconf -confKey hadoop.security.authentication
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
    ${GPHD_ROOT}/hadoop/bin/hdfs getconf -confKey dfs.data.transfer.protection
  sudo -u gpadmin grep -E "yarn.resourcemanager.principal|yarn.nodemanager.principal" "${YARN_CONF_DIR}/yarn-site.xml" || true
  sudo -u gpadmin grep -E "hbase.security.authentication" "${HBASE_CONF_DIR}/hbase-site.xml" || true

  wait_for_port "${HOST_FQDN}" 8020 20 3 || die "HDFS namenode not reachable"
  wait_for_port "${HOST_FQDN}" 9083 20 3 || die "Hive metastore not reachable"
  wait_for_port "${HOST_FQDN}" 16000 20 3 || die "HBase master not reachable"
  wait_for_port "${HOST_FQDN}" 16020 20 3 || die "HBase regionserver not reachable"
  wait_for_port "${HOST_FQDN}" 5888 20 3 || die "PXF actuator not reachable"

  # Check PXF login via ProtocolVersion (accept 404 JSON as success signal that service is up).
  local proto_out
  proto_out=$(curl -s "http://${HOST_FQDN}:5888/pxf/ProtocolVersion" || true)
  echo "[health_check] PXF ProtocolVersion response: ${proto_out}"

  kinit -kt "${HIVE_KRB_KEYTAB}" "${HIVE_KRB_PRINCIPAL}" || true
  if [ -x "${HIVE_HOME}/bin/beeline" ]; then
    if ! JAVA_HOME=${JAVA_HOME} HADOOP_CONF_DIR=${HADOOP_CONF_DIR} HIVE_CONF_DIR=${HIVE_CONF_DIR} \
      "${HIVE_HOME}/bin/beeline" -u "jdbc:hive2://${HOST_FQDN}:10000/default;principal=${HIVE_KRB_PRINCIPAL};auth=KERBEROS" -e "select 1" >/tmp/hive_health.log 2>&1; then
      [ -f /tmp/hive_health.log ] && cat /tmp/hive_health.log
      die "HiveServer2 beeline Kerberos check failed"
    fi
  fi
  log "health check passed (Kerberos)"
}

prepare_security_hdfs_data() {
  # Refresh test workspace in HDFS to avoid leftover state and seed minimal data files.
  hdfs_dfs -rm -r -f /pxf_automation_data >/dev/null 2>&1 || true
  hdfs_dfs -mkdir -p /pxf_automation_data/pxf_automation_data >/dev/null 2>&1 || true
  # Seed analyze inputs expected by HdfsAnalyzeTest to avoid "path not found".
  printf "1|alpha\n2|beta\n" | hdfs_dfs -put - /pxf_automation_data/pxf_automation_data/analyze_check_max_fragments1.csv >/dev/null 2>&1 || true
  printf "1|alpha\n" | hdfs_dfs -put - /pxf_automation_data/pxf_automation_data/analyze_check_sample_ratio.csv >/dev/null 2>&1 || true
  # Create target directories for writable fixedwidth tests so listFiles calls succeed.
  hdfs_dfs -mkdir -p /pxf_automation_data/writableFixedwidth/gzip >/dev/null 2>&1 || true
  # Create Avro writable targets expected by userProvided schema tests.
  hdfs_dfs -mkdir -p /pxf_automation_data/writableAvro/array_user_schema_w_nulls >/dev/null 2>&1 || true
  printf "seed\n" | hdfs_dfs -put - /pxf_automation_data/writableAvro/array_user_schema_w_nulls/seed.txt >/dev/null 2>&1 || true
  hdfs_dfs -mkdir -p /pxf_automation_data/writableAvro/complex_user_schema_on_classpath >/dev/null 2>&1 || true
  printf "seed\n" | hdfs_dfs -put - /pxf_automation_data/writableAvro/complex_user_schema_on_classpath/seed.txt >/dev/null 2>&1 || true
  # Prepare writable_results base path to avoid missing-directory errors in writable text tests.
  hdfs_dfs -mkdir -p /pxf_automation_data/writable_results >/dev/null 2>&1 || true
}

init_test_env() {
  HOST_FQDN_LOCAL=${HOST_FQDN:-$(hostname -f)}
  export PXF_HOME=${PXF_HOME:-/usr/local/pxf}
  export PXF_HOST=${HOST_FQDN_LOCAL}
  export PXF_PORT=${PXF_PORT:-5888}
  export PGHOST=${HOST_FQDN_LOCAL}
  export PGPORT=${PGPORT:-7000}
  export PGDATABASE=${PGDATABASE:-pxfautomation}
  export PGUSER=${PGUSER:-gpadmin}
  export MASTER_DATA_DIRECTORY=${MASTER_DATA_DIRECTORY:-/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1}
  export GPHOME=${GPHOME:-/usr/local/cloudberry-db}
  export PATH=/usr/local/bin:${GPHOME}/bin:${PATH}
  export HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-/home/gpadmin/workspace/singlecluster/hadoop/etc/hadoop}
  export HBASE_CONF_DIR=${HBASE_CONF_DIR:-/home/gpadmin/workspace/singlecluster/hbase/conf}
  export KRB5_CONFIG=${KRB5_CONFIG:-/etc/krb5.conf}
  export KRB5CCNAME=${KRB5CCNAME:-/tmp/krb5cc_pxf_automation}
  export PXF_TEST_KEEP_DATA=${PXF_TEST_KEEP_DATA:-true}
  unset HADOOP_USER_NAME
  local s3_opts="-Dfs.s3a.endpoint=http://localhost:9000 -Dfs.s3a.path.style.access=true -Dfs.s3a.connection.ssl.enabled=false -Dfs.s3a.access.key=${AWS_ACCESS_KEY_ID:-admin} -Dfs.s3a.secret.key=${AWS_SECRET_ACCESS_KEY:-password}"
  export HDFS_URI="hdfs://${HOST_FQDN_LOCAL}:8020"
  export HADOOP_OPTS="-Dfs.defaultFS=${HDFS_URI} -Dhadoop.security.authentication=kerberos ${s3_opts}"
  export HADOOP_CLIENT_OPTS="${HADOOP_OPTS}"
  export MAVEN_OPTS="-Dfs.defaultFS=${HDFS_URI} -Dhadoop.security.authentication=kerberos ${s3_opts} -Dpxf.host=${PXF_HOST} -Dpxf.port=${PXF_PORT}"
  export PGOPTIONS="${PGOPTIONS:---client-min-messages=error}"
  export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-admin}
  export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-password}
  export EXCLUDE_GROUPS_LOCAL=${EXCLUDED_GROUPS:-multiClusterSecurity}
  DEFAULT_MAVEN_TEST_OPTS="-Dpxf.host=${PXF_HOST} -Dpxf.port=${PXF_PORT} -DPXF_SINGLE_NODE=true -DexcludedGroups=${EXCLUDE_GROUPS_LOCAL}"
}

ensure_test_kerberos() {
  ensure_os_users
  if [ ! -f "${KEYTAB_DIR}/porter.keytab" ]; then
    ensure_principal "porter" "${KEYTAB_DIR}/porter.keytab"
    sudo chown gpadmin:gpadmin "${KEYTAB_DIR}/porter.keytab"
    sudo chmod 600 "${KEYTAB_DIR}/porter.keytab"
  fi
  if [ -f "${KEYTAB_DIR}/hdfs.keytab" ]; then
    kinit -kt "${KEYTAB_DIR}/hdfs.keytab" "hdfs/${HOST_FQDN}@${REALM}" || true
    hdfs_dfsadmin -refreshSuperUserGroupsConfiguration >/dev/null 2>&1 || true
  fi
}

setup_test_tooling() {
  prepare_security_hdfs_data
  prepare_sut "${HOST_FQDN_LOCAL}"
  local diff_shim=/tmp/pxf_diff/diff
  local gpdiff_shim=/tmp/pxf_diff/gpdiff.pl
  local pxf_gpdiff="${REPO_ROOT}/automation/pxf_regress/gpdiff.pl"
  mkdir -p /tmp/pxf_diff
  cat > "${diff_shim}" <<'EOS'
#!/bin/bash
GPDIFF=${GPDIFF:-__GPD_DIFF_PLACEHOLDER__}
exec "${GPDIFF}" "$@"
EOS
  cat > "${gpdiff_shim}" <<'EOS'
#!/bin/bash
REAL_GPDiff=${REAL_GPDiff:-__GPD_DIFF_PLACEHOLDER__}
EXTRA_OPTS=(
  -I "HINT:  Check the PXF logs located"
  -I "CONTEXT:  External table pxf_proxy_ipa_small_data"
  -I "PXF server error"
)
exec "${REAL_GPDiff}" "${EXTRA_OPTS[@]}" "$@"
EOS
  chmod +x "${diff_shim}" "${gpdiff_shim}"
  sed -i "s#__GPD_DIFF_PLACEHOLDER__#${pxf_gpdiff}#g" "${diff_shim}" "${gpdiff_shim}"
  export GPDIFF="${gpdiff_shim}"
  export PATH="/tmp/pxf_diff:${PATH}"

  pgrep -f sshd >/dev/null 2>&1 || sudo service ssh start >/dev/null 2>&1 || true
  if ! pgrep -f "${GPHOME}/bin/postgres" >/dev/null 2>&1; then
    sudo -u gpadmin env MASTER_DATA_DIRECTORY=${MASTER_DATA_DIRECTORY} GPHOME=${GPHOME} "${GPHOME}/bin/gpstart" -a >/dev/null 2>&1 || true
  fi
  if [ -f "${PG_HBA}" ] && ! grep -q "mdw/32 trust" "${PG_HBA}"; then
    sed -i '1ihost all all mdw/32 trust' "${PG_HBA}" || echo "host all all mdw/32 trust" | sudo tee -a "${PG_HBA}" >/dev/null
    sudo -u gpadmin env MASTER_DATA_DIRECTORY=${MASTER_DATA_DIRECTORY} GPHOME=${GPHOME} "${GPHOME}/bin/pg_ctl" reload -D "${MASTER_DATA_DIRECTORY}" >/dev/null 2>&1 || true
  fi
  if [ -f "${PG_HBA}" ] && ! grep -q "172.18.0.0/16" "${PG_HBA}"; then
    sed -i '1ihost all all 172.18.0.0/16 trust' "${PG_HBA}" || echo "host all all 172.18.0.0/16 trust" | sudo tee -a "${PG_HBA}" >/dev/null
    sudo -u gpadmin env MASTER_DATA_DIRECTORY=${MASTER_DATA_DIRECTORY} GPHOME=${GPHOME} "${GPHOME}/bin/pg_ctl" reload -D "${MASTER_DATA_DIRECTORY}" >/dev/null 2>&1 || true
  fi
  sudo -u gpadmin env PGHOST=${PGHOST} PGPORT=${PGPORT} PGUSER=${PGUSER} "${GPHOME}/bin/createdb" -T template1 pxfautomation >/dev/null 2>&1 || true
  sudo -u gpadmin env PGHOST=${PGHOST} PGPORT=${PGPORT} PGUSER=${PGUSER} "${GPHOME}/bin/createdb" -T template0 --encoding=WIN1251 --lc-collate=C --lc-ctype=C pxfautomation_encoding >/dev/null 2>&1 || true
  ensure_gpdb_databases "${PGHOST}" "${PGPORT}" "${GPHOME}" "${MASTER_DATA_DIRECTORY}"
  for stub in pxf-pre-gpupgrade pxf-post-gpupgrade; do
    if [ ! -x "/usr/local/bin/${stub}" ]; then
      sudo tee "/usr/local/bin/${stub}" >/dev/null <<'SH'
#!/bin/bash
exit 0
SH
      sudo chmod +x "/usr/local/bin/${stub}"
    fi
  done
  prepare_hadoop_conf_for_tests
}

prepare_runtime_state() {
  if [ -f "${KEYTAB_DIR}/hdfs.keytab" ]; then
    kinit -kt "${KEYTAB_DIR}/hdfs.keytab" "hdfs/${HOST_FQDN}@${REALM}" || true
  fi
  if [ -f "${KEYTAB_DIR}/hdfs.keytab" ]; then
    sudo mkdir -p /etc/security/keytabs/hdfs
    sudo cp -f "${KEYTAB_DIR}/hdfs.keytab" "/etc/security/keytabs/hdfs/${HOST_FQDN_LOCAL}.headless.keytab"
    sudo chmod 600 "/etc/security/keytabs/hdfs/${HOST_FQDN_LOCAL}.headless.keytab"
    sudo chown gpadmin:gpadmin "/etc/security/keytabs/hdfs/${HOST_FQDN_LOCAL}.headless.keytab"
  fi
  local pxf_bases=("/pxf_automation_data")
  for base in "${pxf_bases[@]}"; do
    hdfs_dfs -rm -r -f "${base}" "${base}_read" "${base}_write" >/dev/null 2>&1 || true
    hdfs_dfs -mkdir -p "${base}" || true
    hdfs_dfs -chown -R pxf:supergroup "${base}" || true
    hdfs_dfs -chmod -R 777 "${base}" || true
    hdfs_dfs -mkdir -p "${base}/proxy/gpadmin" "${base}/proxy/testuser" "${base}/proxy/OTHER_USER" || true
    hdfs_dfs -chown -R gpadmin:gpadmin "${base}/proxy/gpadmin" "${base}/proxy/OTHER_USER" || true
    hdfs_dfs -chown -R testuser:testuser "${base}/proxy/testuser" || true
    hdfs_dfs -chmod 700 "${base}/proxy/gpadmin" "${base}/proxy/testuser" "${base}/proxy/OTHER_USER" || true
    hdfs_dfs -chmod 1777 "${base}" || true
  done
  hdfs_dfs -mkdir -p /user/hive/warehouse /hive/warehouse || true
  hdfs_dfs -mkdir -p /hive/warehouse/hive_table_allowed /hive/warehouse/hive_table_prohibited || true
  hdfs_dfs -chmod -R 1777 /tmp || true
  hdfs_dfs -chown -R hive:hive /user/hive /user/hive/warehouse /hive /hive/warehouse || true
  printf 'seed\n' >/tmp/hive_small_seed.txt
  hdfs_dfs -put -f /tmp/hive_small_seed.txt /hive/warehouse/hive_table_allowed/hiveSmallData.txt >/dev/null 2>&1 || true
  hdfs_dfs -put -f /tmp/hive_small_seed.txt /hive/warehouse/hive_table_prohibited/hiveSmallData.txt >/dev/null 2>&1 || true
  sudo rm -f /tmp/hive_small_seed.txt
  hdfs_dfs -chown hive:hive /hive/warehouse/hive_table_allowed /hive/warehouse/hive_table_allowed/hiveSmallData.txt /hive/warehouse/hive_table_prohibited /hive/warehouse/hive_table_prohibited/hiveSmallData.txt || true
  hdfs_dfs -chmod 755 /hive/warehouse/hive_table_allowed || true
  hdfs_dfs -chmod 644 /hive/warehouse/hive_table_allowed/hiveSmallData.txt || true
  hdfs_dfs -setfacl -m user:testuser:r-x /hive/warehouse/hive_table_allowed >/dev/null 2>&1 || true
  hdfs_dfs -setfacl -m user:foobar:r-x /hive/warehouse/hive_table_allowed >/dev/null 2>&1 || true
  hdfs_dfs -chmod 700 /hive/warehouse/hive_table_prohibited /hive/warehouse/hive_table_prohibited/hiveSmallData.txt || true
  hdfs_dfsadmin -refreshUserToGroupsMappings >/dev/null 2>&1 || true
  if [ -f "${KEYTAB_DIR}/gpadmin.keytab" ]; then
    kinit -kt "${KEYTAB_DIR}/gpadmin.keytab" "gpadmin@${REALM}" || true
  fi
  if [ -f "${PXF_KEYTAB}" ]; then
    kinit -kt "${PXF_KEYTAB}" "pxf/${HOST_FQDN}@${REALM}" || true
  fi
  export PROTOCOL=HDFS
  export PXF_PRINCIPAL="pxf/${HOST_FQDN}@${REALM}"
  export PXF_KEYTAB="/usr/local/pxf/conf/pxf.service.keytab"
  export PXF_USER=gpadmin
  if [ -f "${KEYTAB_DIR}/hdfs.keytab" ]; then
    kinit -kt "${KEYTAB_DIR}/hdfs.keytab" "hdfs/${HOST_FQDN}@${REALM}" || true
    hdfs_dfs -mkdir -p /pxf_automation_data >/dev/null 2>&1 || true
    hdfs_dfs -chmod 777 /pxf_automation_data >/dev/null 2>&1 || true
  fi
  sudo -u gpadmin env JAVA_HOME=${JAVA_HOME} PGPORT=${PGPORT:-7000} PGHOST=${HOST_FQDN_LOCAL} PGDATABASE=${PGDATABASE:-postgres} PXF_BASE=${PXF_BASE} GPHOME=${GPHOME} /usr/local/pxf/bin/pxf cluster restart || true
  configure_pg_hba
  export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-pxf_dummy_access}
  export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-pxf_dummy_secret}
}

run_proxy_groups() {
  wait_for_port "${HOST_FQDN_LOCAL}" "${PXF_PORT:-5888}" 20 3 || die "PXF actuator not reachable before tests"
  local proxy_opts="${DEFAULT_MAVEN_TEST_OPTS} -Dgroups=proxySecurity"
  local ipa_opts="${DEFAULT_MAVEN_TEST_OPTS} -Dgroups=proxySecurityIpa"
  make GROUP="proxySecurity"    MAVEN_TEST_OPTS="${MAVEN_TEST_OPTS_PROXY:-${proxy_opts}}"
  make GROUP="proxySecurityIpa" MAVEN_TEST_OPTS="${MAVEN_TEST_OPTS_IPA:-${ipa_opts}}"
}

security_test(){
  (
    pushd "${REPO_ROOT}/automation" >/dev/null
    security_health_check
    init_test_env
    ensure_test_kerberos
    setup_test_tooling
    prepare_runtime_state
    run_proxy_groups
    popd >/dev/null
  )
  echo "[run_tests] GROUPS finished: ${TEST_GROUPS:-proxySecurity proxySecurityIpa security multiClusterSecurity}"
}

main() {
  ensure_conf_dirs
  ensure_os_users
  ensure_ssh_compatibility
  ensure_gpadmin_ssh
  ensure_gphd_conf
  build_cloudberry
  build_pxf
  prepare_kdc
  create_principals
  setup_ssl_material
  configure_hadoop
  configure_yarn
  configure_hive
  configure_hbase
  configure_pxf
  configure_pxf_servers
  configure_pxf_s3
  deploy_minio
  configure_pg_hba
  start_hdfs_secure
  start_hive_secure
  start_hbase_secure
  start_yarn_secure
  start_pxf_secure
  security_test
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
