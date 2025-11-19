#!/bin/bash
set -e
set -x

# ----------------------------------------------------------------------
# Start SSH daemon and setup for SSH access
# ----------------------------------------------------------------------
# The SSH daemon is started to allow remote access to the container via
# SSH. This is useful for development and debugging purposes. If the SSH
# daemon fails to start, the script exits with an error.
# ----------------------------------------------------------------------
if [ ! -d /var/run/sshd ]; then
   sudo mkdir -p /var/run/sshd
   sudo chmod 0755 /var/run/sshd
fi
# Start SSH daemon in background (without -D flag, it runs as daemon)
sudo /usr/sbin/sshd
echo "SSH daemon started"
# Wait a moment for SSH daemon to fully start
sleep 2
# Verify SSH daemon is running by checking if port 22 is listening
if ! command -v ss > /dev/null 2>&1; then
    # If ss is not available, just continue - sshd should be running
    echo "Note: Cannot verify SSH daemon (ss command not available), continuing..."
else
    if ! ss -tlnp | grep -q ":22 "; then
        echo "Warning: SSH daemon may not be listening on port 22"
    fi
fi

# ----------------------------------------------------------------------
# Remove /run/nologin to allow logins for all users via SSH
# ----------------------------------------------------------------------
sudo rm -rf /run/nologin

# ----------------------------------------------------------------------
# Prepare files for gpinitsystem
# ----------------------------------------------------------------------
sudo mkdir -p /data0/database/master /data0/database/primary /data0/database/mirror
sudo chown -R gpadmin:gpadmin /data0

echo "mdw" | sudo tee -a /tmp/gpdb-hosts

sudo chown -R gpadmin:gpadmin /tmp/gpinitsystem_singlenode /tmp/gpdb-hosts
echo "export COORDINATOR_DATA_DIRECTORY=/data0/database/master/gpseg-1" | sudo tee -a /etc/profile
echo "export MASTER_DATA_DIRECTORY=/data0/database/master/gpseg-1"      | sudo tee -a /etc/profile
echo "source /usr/local/cloudberry-db/cloudberry-env.sh"                | sudo tee -a /etc/profile

# ----------------------------------------------------------------------
# Configure /home/gpadmin
# ----------------------------------------------------------------------
mkdir -p /home/gpadmin/.ssh/
ssh-keyscan -t rsa mdw > /home/gpadmin/.ssh/known_hosts
chown -R gpadmin:gpadmin /home/gpadmin/.ssh/

echo "export COORDINATOR_DATA_DIRECTORY=/data0/database/master/gpseg-1" >> /home/gpadmin/.bashrc
echo "export MASTER_DATA_DIRECTORY=/data0/database/master/gpseg-1"      >> /home/gpadmin/.bashrc
echo "source /usr/local/cloudberry-db/cloudberry-env.sh"                >> /home/gpadmin/.bashrc

# ----------------------------------------------------------------------
# Run gpinitsystem
# ----------------------------------------------------------------------
# Source Cloudberry environment variables
source /usr/local/cloudberry-db/cloudberry-env.sh
export COORDINATOR_DATA_DIRECTORY=/data0/database/master/gpseg-1
export MASTER_DATA_DIRECTORY=/data0/database/master/gpseg-1

export USER=gpadmin

# Initialize single node Cloudberry cluster
gpinitsystem -a \
             -c /tmp/gpinitsystem_singlenode \
             -h /tmp/gpdb-hosts \
             --max_connections=100 || echo "gpinitsystem finished with exit code $?"

## Allow any host access the Cloudberry Cluster
echo 'host all all 0.0.0.0/0 trust' >> /data0/database/master/gpseg-1/pg_hba.conf
# 'testuser' for proxy-tests
echo 'local all testuser trust' >> /data0/database/master/gpseg-1/pg_hba.conf

# Configure PostgreSQL to listen on all interfaces
echo "listen_addresses = '*'" >> /data0/database/master/gpseg-1/postgresql.conf
echo "port = 5432" >> /data0/database/master/gpseg-1/postgresql.conf

gpstop -u && echo "pg_hba.conf has been reloaded"

psql -d template1 \
     -c "ALTER USER gpadmin PASSWORD 'cbdb@123'"

## Set gpadmin password, display version and cluster configuration
psql -P pager=off -d template1 -c "SELECT VERSION()"
psql -P pager=off -d template1 -c "SELECT * FROM gp_segment_configuration ORDER BY dbid"
psql -P pager=off -d template1 -c "SHOW optimizer"


# ----------------------------------------------------------------------
# Prepare PXF
# ----------------------------------------------------------------------
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
export PATH="$PXF_HOME/bin:$PATH"
export PXF_JVM_OPTS="-Xmx512m -Xms256m"
export PXF_HOST=localhost # 0.0.0.0  # listen on all interfaces

# Prepare a new $PXF_BASE directory on each Cloudberry Database host.
# - create directory structure in $PXF_BASE
# - copy configuration files from $PXF_HOME/conf to $PXF_BASE/conf
/usr/local/pxf-cbdb1/bin/pxf cluster prepare

# Use Java 11:
echo "JAVA_HOME=/usr/lib/jvm/java-11-openjdk" >> $PXF_BASE/conf/pxf-env.sh
# Configure PXF to listen on all interfaces
sed -i 's/# server.address=localhost/server.address=0.0.0.0/' /home/gpadmin/pxf/conf/pxf-application.properties
# add property to allow dynamic test: profiles that are used when testing against FDW
echo -e "\npxf.profile.dynamic.regex=test:.*" >> $PXF_BASE/conf/pxf-application.properties
# set up pxf configs from templates
cp -v $PXF_HOME/templates/{hdfs,mapred,yarn,core,hbase,hive}-site.xml $PXF_BASE/servers/default

# Register PXF extension in Cloudberry
# Note: Temporarily grant write permissions to gpadmin user for GPHOME/lib/postgresql/ and
# GPHOME/share/postgresql/extension/ directories so that 'pxf cluster register' can copy
# extension files. After registration, write permissions are revoked, leaving only root
# with write access to these directories for security.
if [[ -d ${PXF_HOME}/gpextable ]] && [[ -n "${GPHOME}" ]] && [[ -f ${GPHOME}/cloudberry-env.sh ]]; then
    echo "Registering PXF extension in Cloudberry..."
    # Grant temporary write permissions
    sudo chmod -R ugo+w ${GPHOME}/lib/postgresql ${GPHOME}/share/postgresql/extension
    # Register extension using pxf cluster register
    /usr/local/pxf-cbdb1/bin/pxf cluster register
    # Revoke write permissions, leaving only root with write access
    sudo chmod -R go-w ${GPHOME}/lib/postgresql ${GPHOME}/share/postgresql/extension
fi
# Start PXF
/usr/local/pxf-cbdb1/bin/pxf cluster start

# ----------------------------------------------------------------------
# Prepare Hadoop
# ----------------------------------------------------------------------
# FIXME: reuse old scripts
cd /home/gpadmin/workspace/pxf/automation
make symlink_pxf_jars
cp /home/gpadmin/automation_tmp_lib/pxf-hbase.jar $GPHD_ROOT/hbase/lib/

$GPHD_ROOT/bin/init-gphd.sh
$GPHD_ROOT/bin/start-gphd.sh

# --------------------------------------------------------------------
# Run tests
# --------------------------------------------------------------------
# create GOCACHE directory for gpadmin user
sudo mkdir -p /home/gpadmin/.cache/go-build
sudo chown -R gpadmin:gpadmin /home/gpadmin/.cache
sudo chmod -R 755 /home/gpadmin/.cache
# create .m2 cache directory
sudo mkdir -p /home/gpadmin/.m2
sudo chown -R gpadmin:gpadmin /home/gpadmin/.m2
sudo chmod -R 755 /home/gpadmin/.m2

# FIXME: remove when tinc removed
sudo ln -sf ${GPHOME}/cloudberry-env.sh ${GPHOME}/greenplum_path.sh

# make without arguments runs all tests
cd /home/gpadmin/workspace/pxf/automation
make

# Keep container running
#tail -f /dev/null