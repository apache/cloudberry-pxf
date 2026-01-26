# load singlecluster environment
. $GPHD_ROOT/bin/gphd-env.sh

export HADOOP_CLASSPATH=\
$HADOOP_CLASSPATH:\
$COMMON_CLASSPATH:\

# Extra Java runtime options.  Empty by default.
export HADOOP_OPTS="$HADOOP_OPTS $COMMON_JAVA_OPTS"

export COMMON_MASTER_OPTS="-Dhadoop.tmp.dir=/home/gpadmin/workspace/singlecluster/storage/hadoop"

# Command specific options appended to HADOOP_OPTS when specified
export HDFS_NAMENODE_OPTS="$COMMON_MASTER_OPTS"
export HADOOP_SECONDARYNAMENODE_OPTS="$COMMON_MASTER_OPTS"

# Where log files are stored.  $HADOOP_HOME/logs by default.
export HADOOP_LOG_DIR=$LOGS_ROOT

# The directory where pid files are stored. /tmp by default.
export HADOOP_PID_DIR=$PIDS_ROOT

# Rely on JAVA_HOME provided by gphd-env.sh (which already auto-detects arch/JDK).
if [ -z "${JAVA_HOME:-}" ]; then
  echo "Error: JAVA_HOME is not set (expected from gphd-env.sh)."
  exit 1
fi
