#!/bin/bash
# Centralized environment for Cloudberry + PXF + Hadoop stack

# --------------------------------------------------------------------
# Architecture-aware Java selections
# --------------------------------------------------------------------
case "$(uname -m)" in
  aarch64|arm64)
    JAVA_BUILD=${JAVA_BUILD:-/usr/lib/jvm/java-11-openjdk-arm64}
    JAVA_HADOOP=${JAVA_HADOOP:-/usr/lib/jvm/java-8-openjdk-arm64}
    ;;
  x86_64|amd64)
    JAVA_BUILD=${JAVA_BUILD:-/usr/lib/jvm/java-11-openjdk-amd64}
    JAVA_HADOOP=${JAVA_HADOOP:-/usr/lib/jvm/java-8-openjdk-amd64}
    ;;
  *)
    JAVA_BUILD=${JAVA_BUILD:-/usr/lib/jvm/java-11-openjdk}
    JAVA_HADOOP=${JAVA_HADOOP:-/usr/lib/jvm/java-8-openjdk}
    ;;
esac

# --------------------------------------------------------------------
# Core paths
# --------------------------------------------------------------------
export GPHOME=${GPHOME:-/usr/local/cloudberry-db}
export PXF_HOME=${PXF_HOME:-/usr/local/pxf}
export PXF_BASE=${PXF_BASE:-/home/gpadmin/pxf-base}
export GPHD_ROOT=${GPHD_ROOT:-/home/gpadmin/workspace/singlecluster}
export GOPATH=${GOPATH:-/home/gpadmin/go}
export PATH="$GPHD_ROOT/bin:$GPHD_ROOT/hadoop/bin:$GPHD_ROOT/hive/bin:$GPHD_ROOT/hbase/bin:$GPHD_ROOT/zookeeper/bin:$JAVA_BUILD/bin:/usr/local/go/bin:$GOPATH/bin:$GPHOME/bin:$PXF_HOME/bin:$PATH"
export COMMON_JAVA_OPTS=${COMMON_JAVA_OPTS:-}

# --------------------------------------------------------------------
# Database defaults
# --------------------------------------------------------------------
export PGHOST=${PGHOST:-localhost}
export PGPORT=${PGPORT:-7000}
export MASTER_DATA_DIRECTORY=${MASTER_DATA_DIRECTORY:-/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1}

# --------------------------------------------------------------------
# PXF defaults
# --------------------------------------------------------------------
export PXF_JVM_OPTS=${PXF_JVM_OPTS:-"-Xmx512m -Xms256m"}
export PXF_HOST=${PXF_HOST:-localhost}

# Source Cloudberry env and demo cluster if present
[ -f "$GPHOME/cloudberry-env.sh" ] && source "$GPHOME/cloudberry-env.sh"
[ -f "/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh" ] && source /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh

echo "[pxf-env] loaded (JAVA_BUILD=${JAVA_BUILD}, JAVA_HADOOP=${JAVA_HADOOP})"
