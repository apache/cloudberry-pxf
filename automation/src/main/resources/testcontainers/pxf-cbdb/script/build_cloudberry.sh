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
# Build Cloudberry from source — works on both Ubuntu and Rocky/RHEL

# Install sudo & git
if command -v apt-get >/dev/null 2>&1; then
  sudo apt update && sudo apt install -y sudo git
elif command -v dnf >/dev/null 2>&1; then
  sudo dnf install -y sudo git
fi

# Required configuration
## Add Cloudberry environment setup to .bashrc
echo -e '\n# Add Cloudberry entries
if [ -f /usr/local/cloudberry-db/cloudberry-env.sh ]; then
  source /usr/local/cloudberry-db/cloudberry-env.sh
fi
## US English with UTF-8 character encoding
export LANG=en_US.UTF-8
' >> /home/gpadmin/.bashrc
## Set up SSH for passwordless access
mkdir -p /home/gpadmin/.ssh
if [ ! -f /home/gpadmin/.ssh/id_rsa ]; then
  ssh-keygen -t rsa -b 2048 -C 'apache-cloudberry-dev' -f /home/gpadmin/.ssh/id_rsa -N ""
fi
cat /home/gpadmin/.ssh/id_rsa.pub >> /home/gpadmin/.ssh/authorized_keys
## Set proper SSH directory permissions
chmod 700 /home/gpadmin/.ssh
chmod 600 /home/gpadmin/.ssh/authorized_keys
chmod 644 /home/gpadmin/.ssh/id_rsa.pub

# Configure system settings
sudo tee /etc/security/limits.d/90-db-limits.conf << 'EOF'
## Core dump file size limits for gpadmin
gpadmin soft core unlimited
gpadmin hard core unlimited
## Open file limits for gpadmin
gpadmin soft nofile 524288
gpadmin hard nofile 524288
## Process limits for gpadmin
gpadmin soft nproc 131072
gpadmin hard nproc 131072
EOF

# Verify resource limits
ulimit -a

# Install basic system packages
if command -v apt-get >/dev/null 2>&1; then
  sudo apt update
  sudo apt install -y bison \
    bzip2 \
    cmake \
    curl \
    flex \
    gcc \
    g++ \
    iproute2 \
    iputils-ping \
    language-pack-en \
    locales \
    libapr1-dev \
    libbz2-dev \
    libcurl4-gnutls-dev \
    libevent-dev \
    libkrb5-dev \
    libipc-run-perl \
    libldap2-dev \
    libpam0g-dev \
    libprotobuf-dev \
    libreadline-dev \
    libssl-dev \
    libuv1-dev \
    liblz4-dev \
    libxerces-c-dev \
    libxml2-dev \
    libyaml-dev \
    libzstd-dev \
    libperl-dev \
    make \
    pkg-config \
    protobuf-compiler \
    python3-dev \
    python3-pip \
    python3-setuptools \
    rsync \
    libsnappy-dev
elif command -v dnf >/dev/null 2>&1; then
  sudo dnf install -y \
    bison \
    bzip2 \
    cmake \
    curl \
    flex \
    gcc \
    gcc-c++ \
    iproute \
    iputils \
    glibc-langpack-en \
    glibc-locale-source \
    apr-devel \
    bzip2-devel \
    libcurl-devel \
    libevent-devel \
    krb5-devel \
    perl-IPC-Run \
    openldap-devel \
    pam-devel \
    protobuf-devel \
    readline-devel \
    openssl-devel \
    libuv-devel \
    lz4-devel \
    xerces-c-devel \
    libxml2-devel \
    libyaml-devel \
    libzstd-devel \
    perl-devel \
    make \
    pkgconfig \
    protobuf-compiler \
    python3-devel \
    python3-pip \
    python3-setuptools \
    rsync \
    snappy-devel
fi

# Continue as gpadmin user


# Prepare the build environment for Apache Cloudberry
sudo rm -rf /usr/local/cloudberry-db
sudo chmod a+w /usr/local
mkdir -p /usr/local/cloudberry-db
sudo chown -R gpadmin:gpadmin /usr/local/cloudberry-db

# Set up xerces-c paths:
# - Ubuntu: installed via libxerces-c-dev into /usr/include/xercesc
# - Rocky9: pre-built in the base image at /usr/local/xerces-c/
if command -v apt-get >/dev/null 2>&1; then
  XERCES_INCLUDES=/usr/include/xercesc
else
  XERCES_INCLUDES=/usr/local/xerces-c/include
  # Copy shared libs so the installed cloudberry-db can find them at runtime
  mkdir -p /usr/local/cloudberry-db/lib
  cp -v /usr/local/xerces-c/lib/libxerces-c.so \
        /usr/local/xerces-c/lib/libxerces-c-3.*.so \
        /usr/local/cloudberry-db/lib/
  # Register the lib path so configure test programs can load the .so at runtime
  echo /usr/local/cloudberry-db/lib | sudo tee /etc/ld.so.conf.d/cloudberry-xerces.conf
  sudo ldconfig
  export CPPFLAGS="${CPPFLAGS:-} -I/usr/local/xerces-c/include"
  export LDFLAGS="${LDFLAGS:-} -L/usr/local/cloudberry-db/lib"
fi

# Run configure
cd ~/workspace/cloudberry
./configure --prefix=/usr/local/cloudberry-db \
            --disable-external-fts \
            --enable-debug \
            --enable-cassert \
            --enable-debug-extensions \
            --enable-gpcloud \
            --enable-ic-proxy \
            --enable-mapreduce \
            --enable-orafce \
            --enable-orca \
            --disable-pax \
            --disable-pxf \
            --enable-tap-tests \
            --with-gssapi \
            --with-ldap \
            --with-libxml \
            --with-lz4 \
            --with-pam \
            --with-perl \
            --with-pgport=5432 \
            --with-python \
            --with-pythonsrc-ext \
            --with-ssl=openssl \
            --with-uuid=e2fs \
            --with-includes=/usr/include/xercesc

# Build and install Cloudberry and its contrib modules
make -j$(nproc) -C ~/workspace/cloudberry
make -j$(nproc) -C ~/workspace/cloudberry/contrib
make install -C ~/workspace/cloudberry
make install -C ~/workspace/cloudberry/contrib

# Verify the installation
/usr/local/cloudberry-db/bin/postgres --gp-version
/usr/local/cloudberry-db/bin/postgres --version
ldd /usr/local/cloudberry-db/bin/postgres

# cleanup build tree (reduce image size)
make clean