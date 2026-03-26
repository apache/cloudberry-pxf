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

# Cloudberry RPM Package Build Script for Rocky 9
CLOUDBERRY_VERSION="${CLOUDBERRY_VERSION:-99.0.0}"
CLOUDBERRY_BUILD="${CLOUDBERRY_BUILD:-1}"
INSTALL_PREFIX="${INSTALL_PREFIX:-/usr/local/cloudberry-db}"
WORKSPACE="${WORKSPACE:-$HOME/workspace}"
CLOUDBERRY_SRC="${WORKSPACE}/cloudberry"

echo "=== Cloudberry RPM Package Build ==="
echo "Version: ${CLOUDBERRY_VERSION}"
echo "Build: ${CLOUDBERRY_BUILD}"
echo "Install Prefix: ${INSTALL_PREFIX}"
echo "Source: ${CLOUDBERRY_SRC}"

# Clean previous installation
rm -rf "${INSTALL_PREFIX}"
mkdir -p "${INSTALL_PREFIX}"

# Copy xerces-c shared libraries required by ORCA
mkdir -p "${INSTALL_PREFIX}/lib"
cp -v /usr/local/xerces-c/lib/libxerces-c.so \
      /usr/local/xerces-c/lib/libxerces-c-3.*.so \
      "${INSTALL_PREFIX}/lib/"

# Build Cloudberry using official build scripts
export SRC_DIR="${CLOUDBERRY_SRC}"
export CPPFLAGS="${CPPFLAGS:-} -I/usr/local/xerces-c/include"
export LDFLAGS="${LDFLAGS:-} -L${INSTALL_PREFIX}/lib"
mkdir -p "${SRC_DIR}/build-logs"
cd "${CLOUDBERRY_SRC}"
./devops/build/automation/cloudberry/scripts/configure-cloudberry.sh
./devops/build/automation/cloudberry/scripts/build-cloudberry.sh

# Copy LICENSE
cp LICENSE "${INSTALL_PREFIX}/"

# Create RPM build structure
RPM_BUILD_DIR="${WORKSPACE}/cloudberry-rpm"
mkdir -p "${RPM_BUILD_DIR}"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
RPM_INSTALL_ROOT="${RPM_BUILD_DIR}/BUILDROOT/apache-cloudberry-db-${CLOUDBERRY_VERSION}-${CLOUDBERRY_BUILD}.x86_64"
mkdir -p "${RPM_INSTALL_ROOT}${INSTALL_PREFIX}"

# Copy installed files
cp -a "${INSTALL_PREFIX}"/* "${RPM_INSTALL_ROOT}${INSTALL_PREFIX}/"

# Create spec file
cat > "${RPM_BUILD_DIR}/SPECS/cloudberry-db.spec" << EOF
Name: apache-cloudberry-db
Version: ${CLOUDBERRY_VERSION}
Release: ${CLOUDBERRY_BUILD}%{?dist}
Summary: Apache Cloudberry Database
License: Apache-2.0
Group: Applications/Databases
AutoReqProv: no

%description
Apache Cloudberry is a massively parallel processing (MPP) database
built on PostgreSQL for analytics and data warehousing.

%install
mkdir -p %{buildroot}${INSTALL_PREFIX}
cp -a ${RPM_INSTALL_ROOT}${INSTALL_PREFIX}/* %{buildroot}${INSTALL_PREFIX}/

%files
${INSTALL_PREFIX}

%post
if ! id -u gpadmin >/dev/null 2>&1; then
    useradd -m -s /bin/bash gpadmin
fi
chown -R gpadmin:gpadmin ${INSTALL_PREFIX}
echo "Apache Cloudberry Database installed successfully"

%clean
rm -rf %{buildroot}
EOF

# Build RPM package
rpmbuild --define "_topdir ${RPM_BUILD_DIR}" -bb "${RPM_BUILD_DIR}/SPECS/cloudberry-db.spec"

RPM_FILE=$(find "${RPM_BUILD_DIR}/RPMS" -name "*.rpm" | head -1)
echo "=== RPM Package Created ==="
ls -lh "${RPM_FILE}"
rpm -qpi "${RPM_FILE}"

# Copy RPM to output directory
cp "${RPM_FILE}" "${RPM_BUILD_DIR}/"

echo "=== Build Complete ==="
