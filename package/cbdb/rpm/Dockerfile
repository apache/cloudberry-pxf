# --------------------------------------------------------------------
# Dockerfile for building Cloudberry RPM package
# --------------------------------------------------------------------
# This Dockerfile builds Apache Cloudberry from source and packages it
# as an RPM. The RPM is placed in /build/ directory for extraction.
# --------------------------------------------------------------------

FROM apache/incubator-cloudberry:cbdb-build-rocky9-latest

# Build arguments
ARG CLOUDBERRY_BRANCH=main
ARG CLOUDBERRY_VERSION=1.0.0
ARG CLOUDBERRY_RELEASE=1

# Environment variables
ENV LANG=en_US.UTF-8
ENV CLOUDBERRY_SRC=/home/gpadmin/cbdb_src
ENV BUILD_DESTINATION=/usr/local/cloudberry-db
ENV GPHOME=/usr/local/cloudberry-db

# --------------------------------------------------------------------
# Install Development Tools and Dependencies
# --------------------------------------------------------------------
RUN sudo mkdir -p $GPHOME/lib && \
    sudo cp -v /usr/local/xerces-c/lib/libxerces-c.so $GPHOME/lib/ && \
    sudo cp -v /usr/local/xerces-c/lib/libxerces-c-3.*.so $GPHOME/lib/ && \
    export LD_LIBRARY_PATH=$GPHOME/lib:$LD_LIBRARY_PATH

# --------------------------------------------------------------------
# Clone and Build Cloudberry
# --------------------------------------------------------------------
RUN mkdir -p ${CLOUDBERRY_SRC} && \
    chown -R gpadmin:gpadmin ${CLOUDBERRY_SRC} && \
    git clone https://github.com/cloudberrydb/cloudberrydb.git ${CLOUDBERRY_SRC} --single-branch --branch ${CLOUDBERRY_BRANCH} --depth 1

# Configure minimalistic Cloudberry
WORKDIR ${CLOUDBERRY_SRC}
RUN export LD_LIBRARY_PATH=${GPHOME}/lib:/usr/local/xerces-c/lib:${LD_LIBRARY_PATH} && \
    export PKG_CONFIG_PATH=/usr/local/xerces-c/lib/pkgconfig:${PKG_CONFIG_PATH} && \
    ./configure --prefix=${GPHOME} \
        --enable-orca \
        --disable-pax \
        --enable-debug \
        --enable-cassert \
        --with-openssl \
        --with-libxml \
        --with-pgport=5432 \
        --with-python \
        --with-pythonsrc-ext \
        --with-perl \
        --disable-mapreduce \
        --disable-gpcloud \
        --with-includes=/usr/local/xerces-c/include \
        --with-libraries=${GPHOME}/lib

# Build Cloudberry
RUN cd ${CLOUDBERRY_SRC} && make -j$(nproc) && \
    cd ${CLOUDBERRY_SRC}/contrib && make -j$(nproc)

# Install Cloudberry (as root to /usr/local/cloudberry-db)
# Note: make install needs root to write to /usr/local/cloudberry-db
USER root
RUN cd ${CLOUDBERRY_SRC} && make install && \
    cd ${CLOUDBERRY_SRC}/contrib && make install

USER gpadmin

RUN ${GPHOME}/bin/postgres --gp-version && \
    ${GPHOME}/bin/postgres --version

# --------------------------------------------------------------------
# Setup RPM Build Environment
# --------------------------------------------------------------------
USER gpadmin
RUN rpmdev-setuptree

# Copy spec file from cloned repository to rpmbuild/SPECS
RUN cp ${CLOUDBERRY_SRC}/devops/build/packaging/rpm/apache-cloudberry-db-incubating.spec /home/gpadmin/rpmbuild/SPECS/ && \
    sudo cp ${CLOUDBERRY_SRC}/LICENSE /usr/local/cloudberry-db

# --------------------------------------------------------------------
# Build RPM
# --------------------------------------------------------------------
RUN cd /home/gpadmin/rpmbuild && \
    rpmbuild -bb SPECS/apache-cloudberry-db-incubating.spec \
        --define "version ${CLOUDBERRY_VERSION}" \
        --define "release ${CLOUDBERRY_RELEASE}"

# --------------------------------------------------------------------
# Copy RPM to /build for extraction
# --------------------------------------------------------------------
USER root
RUN mkdir -p /build && \
    find /home/gpadmin/rpmbuild/RPMS -name "*.rpm" -exec cp {} /build/ \; && \
    ls -lh /build/