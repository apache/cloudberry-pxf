
# Install sudo & git
sudo apt update && sudo apt install -y sudo git

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

# Continue as gpadmin user


# Prepare the build environment for Apache Cloudberry
sudo rm -rf /usr/local/cloudberry-db
sudo chmod a+w /usr/local
mkdir -p /usr/local/cloudberry-db
sudo chown -R gpadmin:gpadmin /usr/local/cloudberry-db

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
            --enable-pxf \
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

# Set up a Cloudberry demo cluster
source /usr/local/cloudberry-db/cloudberry-env.sh
make create-demo-cluster -C ~/workspace/cloudberry
source ~/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
psql -P pager=off template1 -c 'SELECT * from gp_segment_configuration'
psql template1 -c 'SELECT version()'