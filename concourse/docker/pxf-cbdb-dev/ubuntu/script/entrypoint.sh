#!/bin/bash
set -e
set -x

sudo apt-get update && \
    sudo apt-get install -y wget lsb-release locales openjdk-11-jre-headless openjdk-8-jre-headless iproute2 sudo  && \
    sudo locale-gen en_US.UTF-8 && \
    sudo locale-gen ru_RU.CP1251 && \
    sudo locale-gen ru_RU.UTF-8 && \
    sudo update-locale LANG=en_US.UTF-8

export LANG=en_US.UTF-8
export LANGUAGE=en_US:en
export LC_ALL=en_US.UTF-8

sudo apt-get install -y maven unzip openssh-server

sudo localedef -c -i ru_RU -f CP1251 ru_RU.CP1251

sudo ssh-keygen -A && \
sudo bash -c 'echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config' && \
sudo mkdir -p /etc/ssh/sshd_config.d && \
sudo touch /etc/ssh/sshd_config.d/pxf-automation.conf && \
sudo bash -c 'echo "KexAlgorithms +diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1" >> /etc/ssh/sshd_config.d/pxf-automation.conf' && \
sudo bash -c 'echo "HostKeyAlgorithms +ssh-rsa,ssh-dss" >> /etc/ssh/sshd_config.d/pxf-automation.conf' && \
sudo bash -c 'echo "PubkeyAcceptedAlgorithms +ssh-rsa,ssh-dss" >> /etc/ssh/sshd_config.d/pxf-automation.conf'

sudo usermod -a -G sudo gpadmin && \
echo "gpadmin:cbdb@123" | sudo chpasswd && \
echo "gpadmin        ALL=(ALL)       NOPASSWD: ALL" | sudo tee -a /etc/sudoers && \
echo "root           ALL=(ALL)       NOPASSWD: ALL" | sudo tee -a /etc/sudoers


mkdir -p /home/gpadmin/.ssh && \
sudo chown -R gpadmin:gpadmin /home/gpadmin/.ssh && \
sudo -u gpadmin ssh-keygen -t rsa -b 4096 -m PEM -C gpadmin -f /home/gpadmin/.ssh/id_rsa -P "" && \
sudo -u gpadmin bash -c 'cat /home/gpadmin/.ssh/id_rsa.pub >> /home/gpadmin/.ssh/authorized_keys' && \
sudo -u gpadmin chmod 0600 /home/gpadmin/.ssh/authorized_keys

# ----------------------------------------------------------------------
# Start SSH daemon and setup for SSH access
# ----------------------------------------------------------------------
# The SSH daemon is started to allow remote access to the container via
# SSH. This is useful for development and debugging purposes. If the SSH
# daemon fails to start, the script exits with an error.
# ----------------------------------------------------------------------
if [ ! -d /var/run/sshd ]; then
   sudo mkdir /var/run/sshd
   sudo chmod 0755 /var/run/sshd
fi
if ! sudo /usr/sbin/sshd; then
    echo "Failed to start SSH daemon"
    exit 1
fi

# ----------------------------------------------------------------------
# Remove /run/nologin to allow logins for all users via SSH
# ----------------------------------------------------------------------
sudo rm -rf /run/nologin

# ----------------------------------------------------------------------
# Configure /home/gpadmin
# ----------------------------------------------------------------------
mkdir -p /home/gpadmin/.ssh/
ssh-keyscan -t rsa cdw > /home/gpadmin/.ssh/known_hosts
chown -R gpadmin:gpadmin /home/gpadmin/.ssh/

# ----------------------------------------------------------------------
# Build Cloudberry
# ----------------------------------------------------------------------
sudo chown -R gpadmin:gpadmin /home/gpadmin/workspace/
./script/build_cloudberrry.sh


# ----------------------------------------------------------------------
# Build pxf
# ----------------------------------------------------------------------
./script/build_pxf.sh


# ----------------------------------------------------------------------
# Source pxf env
# ----------------------------------------------------------------------
source ./script/pxf-env.sh

# ----------------------------------------------------------------------
# Prepare PXF
# ----------------------------------------------------------------------
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH="$PXF_HOME/bin:$PATH"
export PXF_JVM_OPTS="-Xmx512m -Xms256m"
export PXF_HOST=localhost # 0.0.0.0  # listen on all interfaces

# Prepare a new $PXF_BASE directory on each Greenplum Database host.
# - create directory structure in $PXF_BASE
# - copy configuration files from $PXF_HOME/conf to $PXF_BASE/conf
#/usr/local/pxf/bin/pxf cluster prepare

# Use Java 11:
echo "JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64" >> $PXF_BASE/conf/pxf-env.sh
# Configure PXF to listen on all interfaces
sed -i 's/# server.address=localhost/server.address=0.0.0.0/' $PXF_BASE/conf/pxf-application.properties
# add property to allow dynamic test: profiles that are used when testing against FDW
echo -e "\npxf.profile.dynamic.regex=test:.*" >> $PXF_BASE/conf/pxf-application.properties
# set up pxf configs from templates
cp -v $PXF_HOME/templates/{hdfs,mapred,yarn,core,hbase,hive}-site.xml $PXF_BASE/servers/default

# Register PXF extension in Greenplum
# - Copy the PXF extension control file from the PXF installation on each host to the Greenplum installation on the host
#/usr/local/pxf/bin/pxf cluster register
# # Start PXF
#/usr/local/pxf/bin/pxf cluster start

# ----------------------------------------------------------------------
# Prepare Hadoop
# ----------------------------------------------------------------------
# FIXME: reuse old scripts
cd /home/gpadmin/workspace/cloudberry-pxf/automation
make symlink_pxf_jars
cp /home/gpadmin/automation_tmp_lib/pxf-hbase.jar $GPHD_ROOT/hbase/lib/

$GPHD_ROOT/bin/init-gphd.sh
$GPHD_ROOT/bin/start-gphd.sh

# --------------------------------------------------------------------
# Run tests independently and collect results
# --------------------------------------------------------------------
# create GOCACHE directory for gpadmin user
sudo mkdir -p /home/gpadmin/.cache/go-build
sudo chown -R gpadmin:gpadmin /home/gpadmin/.cache
sudo chmod -R 755 /home/gpadmin/.cache
# create .m2 cache directory
sudo mkdir -p /home/gpadmin/.m2
sudo chown -R gpadmin:gpadmin /home/gpadmin/.m2
sudo chmod -R 755 /home/gpadmin/.m2

# Output results directly to mounted automation directory
TEST_RESULTS_DIR="/home/gpadmin/workspace/cloudberry-pxf/automation/test_artifacts"
mkdir -p "$TEST_RESULTS_DIR"
echo "Test Component,Status,Duration,Details" > "$TEST_RESULTS_DIR/summary.csv"

# Function to run test and record result
run_test() {
    local component="$1"
    local test_dir="$2"
    local test_cmd="$3"
    local start_time=$(date +%s)
    local log_file="$TEST_RESULTS_DIR/${component}.log"
    
    echo "Running $component tests..."
    cd "$test_dir"
    
    # Run the test and capture both exit code and output
    if eval "$test_cmd" > "$log_file" 2>&1; then
        local exit_code=0
    else
        local exit_code=$?
    fi
    
    # Check for specific failure patterns in the log
    local status="PASS"
    local details="All tests passed"
    
    if [ $exit_code -ne 0 ]; then
        status="FAIL"
        details="Exit code: $exit_code. Check ${component}.log for details"
    elif grep -q "There are test failures\|BUILD FAILURE\|FAILED\|Failures: [1-9]" "$log_file"; then
        status="FAIL"
        details="Test failures detected. Check ${component}.log for details"
    elif grep -q "Tests run:.*Failures: [1-9]" "$log_file"; then
        status="FAIL"
        details="Test failures detected. Check ${component}.log for details"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo "$component,$status,${duration}s,$details" >> "$TEST_RESULTS_DIR/summary.csv"
    echo "$component: $status (${duration}s)"
}

# Run CLI tests
run_test "CLI" "/home/gpadmin/workspace/cloudberry-pxf/cli" "make test"

# Run External Table tests
run_test "External-Table" "/home/gpadmin/workspace/cloudberry-pxf/external-table" "make installcheck"

# Run Server tests
run_test "Server" "/home/gpadmin/workspace/cloudberry-pxf/server" "./gradlew test"

# Run Automation setup
run_test "Automation-Setup" "/home/gpadmin/workspace/cloudberry-pxf/automation" "make"

# Run Smoke tests
run_test "Smoke-Test" "/home/gpadmin/workspace/cloudberry-pxf/automation" "make TEST=HdfsSmokeTest"

# Run GPDB group tests (allow failure)
run_test "GPDB-Group" "/home/gpadmin/workspace/cloudberry-pxf/automation" "make GROUP=gpdb"

# Copy additional test artifacts to mounted directory
echo "Collecting additional test artifacts..."

# Copy PXF logs
mkdir -p "$TEST_RESULTS_DIR/pxf_logs"
cp -r ~/pxf-base/logs/* "$TEST_RESULTS_DIR/pxf_logs/" 2>/dev/null || true

# Copy server test reports
mkdir -p "$TEST_RESULTS_DIR/server_reports"
cp -r ~/workspace/cloudberry-pxf/server/build/reports/tests/test/* "$TEST_RESULTS_DIR/server_reports/" 2>/dev/null || true

# Copy automation surefire reports (if they exist)
if [ -d ~/workspace/cloudberry-pxf/automation/target/surefire-reports ]; then
    cp -r ~/workspace/cloudberry-pxf/automation/target/surefire-reports "$TEST_RESULTS_DIR/"
fi

# Copy automation logs (if they exist)
if [ -d ~/workspace/cloudberry-pxf/automation/automation_logs ]; then
    cp -r ~/workspace/cloudberry-pxf/automation/automation_logs "$TEST_RESULTS_DIR/"
fi

echo "Test execution completed. Results available in $TEST_RESULTS_DIR"
ls -la "$TEST_RESULTS_DIR"


# Keep container running
#tail -f /dev/null