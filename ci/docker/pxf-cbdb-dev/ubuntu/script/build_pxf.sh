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
case "$(uname -m)" in
  aarch64|arm64) JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-arm64} ;;
  x86_64|amd64)  JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64} ;;
  *)             JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64} ;;
esac
export PATH=$JAVA_HOME/bin:$PATH
export GPHOME=/usr/local/cloudberry-db
source /usr/local/cloudberry-db/cloudberry-env.sh
export PATH=$GPHOME/bin:$PATH

sudo apt update
sudo apt install -y openjdk-11-jdk maven

cd /home/gpadmin/workspace/cloudberry-pxf

# Ensure gpadmin owns the source directory
sudo chown -R gpadmin:gpadmin /home/gpadmin/workspace/cloudberry-pxf
sudo chown -R gpadmin:gpadmin /usr/local/cloudberry-db

# mirror
# If the download fails, you can uncomment the line to switch to another mirror address.
# Configure Gradle to use Aliyun mirror
# mkdir -p ~/.gradle
# cat > ~/.gradle/init.gradle <<'EOF'
# allprojects {
#     repositories {
#         maven { url 'https://maven.aliyun.com/repository/public/' }
#         maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
#         mavenCentral()
#     }
#     buildscript {
#         repositories {
#             maven { url 'https://maven.aliyun.com/repository/public/' }
#             maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
#             mavenCentral()
#         }
#     }
# }
# EOF

# Set Go environment
export GOPATH=$HOME/go
export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin
# mirror
# If the download fails, you can uncomment the line to switch to another mirror address.
# export GOPROXY=https://goproxy.cn,direct
mkdir -p $GOPATH
export PXF_HOME=/usr/local/pxf
sudo mkdir -p "$PXF_HOME"
sudo chown -R gpadmin:gpadmin "$PXF_HOME"

# Build and Install PXF
make -C external-table install
make -C fdw install
make -C cli install
make -C server install-server

# Set up PXF environment

export PXF_BASE=$HOME/pxf-base
export PATH=$PXF_HOME/bin:$PATH
rm -rf "$PXF_BASE"
mkdir -p "$PXF_BASE"

# Initialize PXF
pxf prepare
pxf start

# Verify PXF is running
pxf status
