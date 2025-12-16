# paths
# Prefer JAVA_HADOOP (from pxf-env); otherwise fall back to a default JDK8 path.
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -n "${JAVA_HADOOP:-}" ]; then
    export JAVA_HOME="${JAVA_HADOOP}"
  else
    # Auto-detect Java 8 path for different architectures
    if [ -d "/usr/lib/jvm/java-8-openjdk-$(dpkg --print-architecture)" ]; then
      export JAVA_HOME="/usr/lib/jvm/java-8-openjdk-$(dpkg --print-architecture)"
    elif [ -d "/usr/lib/jvm/java-8-openjdk" ]; then
      export JAVA_HOME="/usr/lib/jvm/java-8-openjdk"
    else
      export JAVA_HOME=$(readlink -f /usr/bin/java | sed 's:/bin/java::')
    fi
  fi
fi
export STORAGE_ROOT=$GPHD_ROOT/storage
export HADOOP_STORAGE_ROOT=$STORAGE_ROOT/hadoop
export ZOOKEEPER_STORAGE_ROOT=$STORAGE_ROOT/zookeeper
export HBASE_STORAGE_ROOT=$STORAGE_ROOT/hbase
export HIVE_STORAGE_ROOT=$STORAGE_ROOT/hive
export PXF_STORAGE_ROOT=$STORAGE_ROOT/pxf
export RANGER_STORAGE_ROOT=$STORAGE_ROOT/ranger

# settings
export SLAVES=${SLAVES:-1}

# Automatically start HBase during GPHD startup
export START_HBASE=true

# Automatically start Stargate during HBase startup
export START_STARGATE=false

# HBase REST service (Stargate) port
export STARGATE_PORT=60009

# Automatically start MapReduce
export START_YARN=true

# Automatically start MapReduce History Server
export START_YARN_HISTORY_SERVER=false

# Automatically start Hive Metastore server
export START_HIVEMETASTORE=false

# Automatically start PXF service
export START_PXF=true

# Don't automatically start Ranger service
export START_RANGER=false

# These settings go into all HBase's, Hadoop's JVMs
export COMMON_JAVA_OPTS=${COMMON_JAVA_OPTS}

# This classpath is automatically added to HBase's and Hadoop's classpaths
# remember to use ':' as separator
export COMMON_CLASSPATH=

# PXF Debug mode
#export PXFDEBUG=true

# PXF Standalone mode
#export PXFDEMO=true
