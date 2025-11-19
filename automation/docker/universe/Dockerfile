FROM pxf/singlecluster:3

# copy hadoop files from this image later...

FROM rockylinux/rockylinux:9

# These packages are needed for cloudberry-pxf
RUN  dnf install -y \
  maven \
  java-1.8.0-openjdk \
  java-1.8.0-openjdk-devel \
  java-11-openjdk \
  java-11-openjdk-devel \
  glibc-langpack-ru \
  glibc-locale-source \
  wget \
  sudo \
  diffutils

# Install Go
RUN wget -O - "https://go.dev/dl/go1.22.12.linux-amd64.tar.gz" | tar -C /usr/local -xz

ENV PATH=$PATH:/usr/local/go/bin

# --------------------------------------------------------------------
# Install Cloudberry DB and PXF from RPM packages
# --------------------------------------------------------------------

# Generate locales
RUN localedef -c -i en_US -f UTF-8 en_US.UTF-8

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

# Install Cloudberry DB from RPM
# RPM files are expected to be in downloads/ directory (relative to build context)
# Copy only main package, exclude debuginfo and other debug packages
# Pattern requires version number immediately after package name (excludes -debuginfo-)
USER root
COPY downloads/apache-cloudberry-db-incubating-*.el9.x86_64.rpm /tmp/
RUN if ls /tmp/apache-cloudberry-db-incubating-*.rpm 1> /dev/null 2>&1; then \
        echo "Installing Cloudberry DB from RPM..." && \
        dnf install -y /tmp/apache-cloudberry-db-incubating-*.rpm && \
        rm -f /tmp/apache-cloudberry-db-incubating-*.rpm; \
    else \
        echo "ERROR: Cloudberry DB RPM not found in downloads/ directory"; \
        exit 1; \
    fi

# Install PXF from RPM
# Copy only main package, exclude debuginfo and other debug packages
# Pattern requires version number immediately after package name (excludes -debuginfo-)
COPY downloads/pxf-cbdb1-*.el9.x86_64.rpm /tmp/
RUN if ls /tmp/pxf-cbdb1-*.rpm 1> /dev/null 2>&1; then \
        echo "Installing PXF from RPM..." && \
        dnf install -y /tmp/pxf-cbdb1-*.rpm && \
        rm -f /tmp/pxf-cbdb1-*.rpm; \
    else \
        echo "ERROR: PXF RPM not found in downloads/ directory"; \
        exit 1; \
    fi

COPY automation/docker/universe/gpinitsystem_singlenode /tmp/

# --------------------------------------------------------------------
# Install automation dependencies
# --------------------------------------------------------------------

# automation has tests with CP1251 encoding
RUN localedef -c -i ru_RU -f CP1251 ru_RU.CP1251

# configure ssh as described in automation/README.md
RUN ssh-keygen -A && \
    echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config && \
    mkdir -p /etc/ssh/sshd_config.d && \
    touch /etc/ssh/sshd_config.d/pxf-automation.conf && \
    echo "KexAlgorithms +diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1" >> /etc/ssh/sshd_config.d/pxf-automation.conf && \
    echo "HostKeyAlgorithms +ssh-rsa,ssh-dss" >> /etc/ssh/sshd_config.d/pxf-automation.conf && \
    echo "PubkeyAcceptedAlgorithms +ssh-rsa,ssh-dss" >> /etc/ssh/sshd_config.d/pxf-automation.conf && \
    sudo update-crypto-policies --set LEGACY

# --------------------------------------------------------------------
# Create gpadmin user and add to sudoers
# --------------------------------------------------------------------
RUN useradd -m -s /bin/bash gpadmin && \
  usermod -a -G wheel gpadmin && \
  echo "gpadmin:cbdb@123" | chpasswd && \
  echo "gpadmin        ALL=(ALL)       NOPASSWD: ALL" >> /etc/sudoers && \
  echo "root           ALL=(ALL)       NOPASSWD: ALL" >> /etc/sudoers

# Base system setup complete, switch to gpadmin to set up everything else
USER gpadmin

# ----------------------------------------------------------------------
# Configure passwordless SSH access for 'gpadmin' user
# ----------------------------------------------------------------------
# The script sets up SSH key-based authentication for the 'gpadmin' user,
# allowing passwordless SSH access. It generates a new SSH key pair if one
# does not already exist, and configures the necessary permissions.
# ----------------------------------------------------------------------
RUN \
  mkdir /home/gpadmin/.ssh && chown -R gpadmin:gpadmin /home/gpadmin/.ssh && \
  ssh-keygen -t rsa -b 4096 -m PEM -C gpadmin -f /home/gpadmin/.ssh/id_rsa -P "" && \
  cat /home/gpadmin/.ssh/id_rsa.pub >> /home/gpadmin/.ssh/authorized_keys && \
  chmod 0600 /home/gpadmin/.ssh/authorized_keys

# --------------------------------------------------------------------
# Setup the Hadoop singlecluster
# --------------------------------------------------------------------
# Setup the Hadoop singlecluster
ENV GPHD_ROOT=/home/gpadmin/workspace/singlecluster
RUN mkdir -p $GPHD_ROOT && chown gpadmin:gpadmin $GPHD_ROOT

ENV HADOOP_ROOT=$GPHD_ROOT/hadoop
ENV HBASE_ROOT=$GPHD_ROOT/hbase
ENV HIVE_ROOT=$GPHD_ROOT/hive
ENV ZOOKEEPER_ROOT=$GPHD_ROOT/zookeeper
ENV PATH=$PATH:$GPHD_ROOT/bin:$HADOOP_ROOT/bin:$HBASE_ROOT/bin:$HIVE_ROOT/bin:$ZOOKEEPER_ROOT/bin

COPY --from=pxf/singlecluster:3 $GPHD_ROOT $GPHD_ROOT
RUN sudo chown -R gpadmin:gpadmin $GPHD_ROOT

# --------------------------------------------------------------------
# Run tests
# --------------------------------------------------------------------
ENV GPHOME=/usr/local/cloudberry-db
ENV PXF_HOME=/usr/local/pxf-cbdb1
ENV PXF_BASE=/home/gpadmin/pxf

EXPOSE 5432 22

# Copy automation directory into container
RUN mkdir -p /home/gpadmin/workspace/pxf
COPY automation /home/gpadmin/workspace/pxf/automation
RUN sudo chown -R gpadmin:gpadmin /home/gpadmin/workspace/pxf/automation
RUN sudo chmod -R 755 /home/gpadmin/workspace/pxf/automation

COPY automation/docker/universe/entrypoint.sh /entrypoint.sh
RUN sudo chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]