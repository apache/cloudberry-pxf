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

source /usr/local/cloudberry-db/cloudberry-env.sh

case "$(uname -m)" in
  aarch64|arm64) JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-arm64} ;;
  x86_64|amd64)  JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64} ;;
  *)             JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64} ;;
esac
export GPHOME=/usr/local/cloudberry-db
export PATH=$GPHOME/bin:$JAVA_HOME/bin:$PATH

# Ensure source/build tree is owned by gpadmin (build runs as gpadmin)
sudo chown -R gpadmin:gpadmin /home/gpadmin/workspace/cloudberry-pxf
sudo chown -R gpadmin:gpadmin /usr/local/cloudberry-db

export PXF_HOME=/usr/local/pxf
sudo mkdir -p "$PXF_HOME"
sudo chmod -R a+rwX "$PXF_HOME"

# Build and Install PXF
cd /home/gpadmin/workspace/cloudberry-pxf
make -C external-table install
make -C fdw install
make -C server install-server
make -C server install-jdbc-drivers

# Set up PXF environment
export PXF_BASE=$HOME/pxf-base
export PATH=$PXF_HOME/bin:$PATH
rm -rf "$PXF_BASE"
mkdir -p "$PXF_BASE"

# Initialize PXF
pxf prepare
cp $PXF_HOME/lib/*.jar $PXF_BASE/lib/
pxf start

# Verify PXF is running
pxf status
