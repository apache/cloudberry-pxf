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
ARG BASE_IMAGE=apache/incubator-cloudberry:cbdb-build-ubuntu22.04-latest
FROM ${BASE_IMAGE}

# Install Java 8 & 11: auto-detect OS package manager
RUN if command -v apt-get >/dev/null 2>&1; then \
      export DEBIAN_FRONTEND=noninteractive && \
      sudo apt-get update && \
      sudo apt-get install -y --no-install-recommends \
        curl ca-certificates git unzip maven make \
        locales wget lsb-release openssh-server iproute2 sudo \
        openjdk-11-jdk-headless; \
    elif command -v dnf >/dev/null 2>&1; then \
      sudo dnf install -y --allowerasing \
        curl ca-certificates wget maven unzip openssh-server iproute sudo glibc-langpack-en glibc-locale-source \
        java-11-openjdk-devel && \
      sudo dnf clean all; \
    fi

# Install Gradle and warm wrapper cache (it will download GRADLE_VERSION again)
# (version must match server/gradle/wrapper/gradle-wrapper.properties)
ARG GRADLE_VERSION=8.14.4
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
      -o /tmp/gradle.zip && \
    sudo unzip -q /tmp/gradle.zip -d /opt && \
    sudo ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle && \
    rm /tmp/gradle.zip && \
    mkdir -p /tmp/gradle-wrapper-warm && cd /tmp/gradle-wrapper-warm && \
    gradle init && gradle wrapper --gradle-version ${GRADLE_VERSION} && ./gradlew javaToolchains && \
    cd / && rm -rf /tmp/gradle-wrapper-warm
ENV GRADLE_HOME="/opt/gradle-${GRADLE_VERSION}"

# Go toolchain for building pxf_regress inside the container
ARG GO_VERSION=1.24.0
RUN ARCH=$(uname -m) && \
    case "$ARCH" in \
      x86_64) GARCH=amd64 ;; \
      aarch64|arm64) GARCH=arm64 ;; \
      *) echo "unsupported arch: $ARCH" >&2; exit 1 ;; \
    esac && \
    curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-${GARCH}.tar.gz" -o /tmp/go.tgz && \
    sudo rm -rf /usr/local/go && \
    sudo tar -C /usr/local -xzf /tmp/go.tgz && \
    rm /tmp/go.tgz
ENV PATH="/usr/local/go/bin:${PATH}"

# Env vars that scripts expect
ENV GPHD_ROOT=/home/gpadmin/workspace/singlecluster
ENV GPHOME=/usr/local/cloudberry-db

RUN sudo mkdir -p /home/gpadmin/workspace && \
    sudo chown -R gpadmin:gpadmin /home/gpadmin/workspace

# Clone Cloudberry source (parametrized via build args)
ARG CLOUDBERRY_REPO=https://github.com/apache/cloudberry.git
ARG CLOUDBERRY_BRANCH=main
RUN git clone --depth 1 -b ${CLOUDBERRY_BRANCH} \
      ${CLOUDBERRY_REPO} /home/gpadmin/workspace/cloudberry

# Copy and run the build script (demo cluster is created at runtime)
COPY script/build_cloudberry.sh /tmp/build_cloudberry.sh
RUN bash /tmp/build_cloudberry.sh