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

# Centralized environment for Cloudberry + PXF

# --------------------------------------------------------------------
# Architecture-aware Java selections (auto-detect OS)
# --------------------------------------------------------------------
if [ -d /usr/lib/jvm/java-11-openjdk-amd64 ] || [ -d /usr/lib/jvm/java-11-openjdk-arm64 ]; then
  # Debian/Ubuntu: paths include architecture suffix
  case "$(uname -m)" in
    aarch64|arm64)
      JAVA_BUILD=${JAVA_BUILD:-/usr/lib/jvm/java-11-openjdk-arm64}
      ;;
    *)
      JAVA_BUILD=${JAVA_BUILD:-/usr/lib/jvm/java-11-openjdk-amd64}
      ;;
  esac
else
  # RHEL/Rocky: architecture-independent symlinks
  JAVA_BUILD=${JAVA_BUILD:-/usr/lib/jvm/java-11-openjdk}
fi

# --------------------------------------------------------------------
# Core paths
# --------------------------------------------------------------------
export GPHOME=${GPHOME:-/usr/local/cloudberry-db}
export PXF_HOME=${PXF_HOME:-/usr/local/pxf}
export PXF_BASE=${PXF_BASE:-/home/gpadmin/pxf-base}
export GPHD_ROOT=${GPHD_ROOT:-/home/gpadmin/workspace/singlecluster}
export PATH="$GPHD_ROOT/bin:$JAVA_BUILD/bin:/usr/local/go/bin:$GPHOME/bin:$PXF_HOME/bin:$PATH"
export COMMON_JAVA_OPTS=${COMMON_JAVA_OPTS:-}

# --------------------------------------------------------------------
# Database defaults
# --------------------------------------------------------------------
export PGHOST=${PGHOST:-localhost}
export PGPORT=${PGPORT:-7000}
export COORDINATOR_DATA_DIRECTORY=${COORDINATOR_DATA_DIRECTORY:-/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/datadirs/qddir/demoDataDir-1}
# set cloudberry timezone utc
export PGTZ=UTC

# --------------------------------------------------------------------
# Minio defaults
# --------------------------------------------------------------------
export AWS_ACCESS_KEY_ID=admin
export AWS_SECRET_ACCESS_KEY=password
export PROTOCOL=minio
export ACCESS_KEY_ID=admin
export SECRET_ACCESS_KEY=password

# --------------------------------------------------------------------
# PXF defaults
# --------------------------------------------------------------------
export PXF_JVM_OPTS=${PXF_JVM_OPTS:-"-Xmx512m -Xms256m"}
export PXF_HOST=${PXF_HOST:-localhost}

# Source Cloudberry env and demo cluster if present
[ -f "$GPHOME/cloudberry-env.sh" ] && source "$GPHOME/cloudberry-env.sh"
[ -f "/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh" ] && source /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh

echo "[pxf-env] loaded (JAVA_BUILD=${JAVA_BUILD})"
