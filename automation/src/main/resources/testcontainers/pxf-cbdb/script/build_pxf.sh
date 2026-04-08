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
# Build and install PXF — works on both Ubuntu and Rocky/RHEL

# Auto-detect Java 11 path
if [ -d /usr/lib/jvm/java-11-openjdk-amd64 ]; then
  JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64}
elif [ -d /usr/lib/jvm/java-11-openjdk-arm64 ]; then
  JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-arm64}
else
  JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk}
fi
export PATH=$JAVA_HOME/bin:$PATH
export GPHOME=/usr/local/cloudberry-db
source /usr/local/cloudberry-db/cloudberry-env.sh
export PATH=$GPHOME/bin:$PATH

# Install Java 11 JDK and Maven
if command -v apt-get >/dev/null 2>&1; then
  sudo apt update
  sudo apt install -y openjdk-11-jdk-headless maven
elif command -v dnf >/dev/null 2>&1; then
  sudo dnf install -y java-11-openjdk-devel maven
fi

cd /home/gpadmin/workspace/cloudberry-pxf

# Ensure gpadmin owns the source directory
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
ls -l $PXF_BASE/lib/
pxf start

# Verify PXF is running
pxf status
