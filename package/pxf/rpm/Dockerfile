# --------------------------------------------------------------------
# Dockerfile for building PXF RPM package
# --------------------------------------------------------------------
# This Dockerfile builds PXF from source and packages it as an RPM.
# The RPM is placed in /build/ directory for extraction.
# --------------------------------------------------------------------

FROM apache/incubator-cloudberry:cbdb-build-rocky9-latest

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Build arguments
ARG PXF_VERSION=6.0.0

# Constants
ENV LICENSE="ASL 2.0"
ENV VENDOR="Open Source"

# Environment variables
ENV LANG=en_US.UTF-8
ENV GPHOME=/usr/local/cloudberry-db
ENV PXF_SRC=/home/gpadmin/pxf_src

# --------------------------------------------------------------------
# Install additional build dependencies
# --------------------------------------------------------------------
USER root
RUN dnf install -y \
        java-11-openjdk \
        java-11-openjdk-devel \
        maven \
        rpm-build \
        rpmdevtools && \
    dnf clean all

# Install Go
RUN wget -O - "https://go.dev/dl/go1.22.12.linux-amd64.tar.gz" | tar -C /usr/local -xz

# Install ginkgo for PXF CLI
RUN PATH="/usr/local/go/bin:$PATH" go install github.com/onsi/ginkgo/ginkgo@v1.16.5

# --------------------------------------------------------------------
# Install Cloudberry DB from RPM
# --------------------------------------------------------------------
# Copy Cloudberry RPM from downloads directory and install it
USER root
COPY --chown=root:root downloads/apache-cloudberry-db-incubating-1.0.0-1.el9.x86_64.rpm /tmp/
RUN echo "Installing Cloudberry DB from RPM..." && \
    dnf install -y /tmp/apache-cloudberry-db-incubating-1.0.0-1.el9.x86_64.rpm && \
    rm -f /tmp/*.rpm

# --------------------------------------------------------------------
# Copy PXF source code from build context (only needed directories)
# --------------------------------------------------------------------
USER gpadmin
RUN mkdir -p ${PXF_SRC}
COPY --chown=gpadmin:gpadmin cli ${PXF_SRC}/cli
COPY --chown=gpadmin:gpadmin external-table ${PXF_SRC}/external-table
COPY --chown=gpadmin:gpadmin fdw ${PXF_SRC}/fdw
COPY --chown=gpadmin:gpadmin package ${PXF_SRC}/package
COPY --chown=gpadmin:gpadmin server ${PXF_SRC}/server

COPY --chown=gpadmin:gpadmin common.mk ${PXF_SRC}/
COPY --chown=gpadmin:gpadmin version ${PXF_SRC}/
COPY --chown=gpadmin:gpadmin api_version ${PXF_SRC}/
WORKDIR ${PXF_SRC}

# --------------------------------------------------------------------
# Build PXF Extensions (external-table)
# --------------------------------------------------------------------
RUN echo 'Building external-table extension...' && \
    source ${GPHOME}/cloudberry-env.sh && \
    make -C external-table stage

# --------------------------------------------------------------------
# Build PXF CLI
# --------------------------------------------------------------------
RUN export PATH="/usr/local/go/bin:$PATH" && \
    export PXF_HOME=/tmp/pxf-install && \
    mkdir -p ${PXF_HOME}/bin && \
    echo 'Installing CLI...' && \
    make -C cli/ install && \
    echo 'Copying CLI to stage...' && \
    mkdir -p cli/build/stage/pxf/bin && \
    cp ${PXF_HOME}/bin/pxf-cli cli/build/stage/pxf/bin/

# --------------------------------------------------------------------
# Build PXF Java Server
# --------------------------------------------------------------------
RUN export JAVA_HOME=/usr/lib/jvm/java-11-openjdk && \
    echo 'Building server...' && \
    make -C server stage-notest

# --------------------------------------------------------------------
# Setup RPM Build Environment
# --------------------------------------------------------------------
USER gpadmin
RUN rpmdev-setuptree

# --------------------------------------------------------------------
# Prepare sources for RPM build
# --------------------------------------------------------------------
# Copy staged files to rpmbuild/SOURCES
RUN PXF_MAIN_VERSION=${PXF_VERSION//-SNAPSHOT/} && \
    if [[ ${PXF_VERSION} == *"-SNAPSHOT" ]]; then PXF_RELEASE=SNAPSHOT; else PXF_RELEASE=1; fi && \
    mkdir -p /home/gpadmin/rpmbuild/SOURCES/gpextable && \
    cp -a external-table/build/stage/gpextable/* /home/gpadmin/rpmbuild/SOURCES/gpextable/ && \
    cp -a cli/build/stage/pxf/* /home/gpadmin/rpmbuild/SOURCES/ && \
    cp -a server/build/stage/* /home/gpadmin/rpmbuild/SOURCES/ && \
    echo "no-git-repo" > /home/gpadmin/rpmbuild/SOURCES/commit.sha && \
    cp package/pxf/rpm/pxf-cbdb1.spec /home/gpadmin/rpmbuild/SPECS/

# --------------------------------------------------------------------
# Build RPM
# --------------------------------------------------------------------
RUN PXF_MAIN_VERSION=${PXF_VERSION//-SNAPSHOT/} && \
    if [[ ${PXF_VERSION} == *"-SNAPSHOT" ]]; then PXF_RELEASE=SNAPSHOT; else PXF_RELEASE=1; fi && \
    cd /home/gpadmin/rpmbuild && \
    rpmbuild -bb SPECS/pxf-cbdb1.spec \
        --define "_topdir /home/gpadmin/rpmbuild" \
        --define "pxf_version ${PXF_MAIN_VERSION}" \
        --define "pxf_release ${PXF_RELEASE}" \
        --define "license ${LICENSE}" \
        --define "vendor ${VENDOR}"

# --------------------------------------------------------------------
# Copy RPM to /build for extraction
# --------------------------------------------------------------------
USER root
RUN mkdir -p /build && \
    find /home/gpadmin/rpmbuild/RPMS -name "*.rpm" -exec cp {} /build/ \; && \
    ls -lh /build/

