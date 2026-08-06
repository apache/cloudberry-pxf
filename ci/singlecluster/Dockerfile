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
        curl ca-certificates \
        openjdk-8-jdk-headless \
        openjdk-11-jdk-headless; \
    elif command -v dnf >/dev/null 2>&1; then \
      sudo dnf install -y --allowerasing --nobest curl ca-certificates && \
      if [ -f /etc/os-release ] && . /etc/os-release && [ "$ID" = "rocky" ] && [ "${VERSION_ID%%.*}" -ge 10 ]; then \
        echo "Rocky 10+: JDK 8/11 not in distro repos, downloading from Adoptium"; \
        sudo dnf install -y --allowerasing --nobest java-21-openjdk-devel; \
        ADOPTIUM_BASE="https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u442-b06/OpenJDK8U-jdk_x64_linux_hotspot_8u442b06"; \
        curl -fsSL "$ADOPTIUM_BASE.tar.gz" -o /tmp/jdk8.tar.gz && \
        sudo mkdir -p /usr/lib/jvm && sudo tar xzf /tmp/jdk8.tar.gz -C /usr/lib/jvm && \
        sudo ln -sf /usr/lib/jvm/jdk8u442-b06 /usr/lib/jvm/java-1.8.0-openjdk && \
        sudo alternatives --install /usr/bin/java java /usr/lib/jvm/java-1.8.0-openjdk/bin/java 1 && \
        rm /tmp/jdk8.tar.gz && \
        ADOPTIUM_BASE11="https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.26%2B4/OpenJDK11U-jdk_x64_linux_hotspot_11.0.26_4"; \
        curl -fsSL "$ADOPTIUM_BASE11.tar.gz" -o /tmp/jdk11.tar.gz && \
        sudo mkdir -p /usr/lib/jvm && sudo tar xzf /tmp/jdk11.tar.gz -C /usr/lib/jvm && \
        sudo ln -sf /usr/lib/jvm/jdk-11.0.26+4 /usr/lib/jvm/java-11-openjdk && \
        sudo alternatives --install /usr/bin/java java /usr/lib/jvm/java-11-openjdk/bin/java 2 && \
        rm /tmp/jdk11.tar.gz; \
      else \
        sudo dnf install -y --allowerasing --nobest \
          java-1.8.0-openjdk-devel \
          java-11-openjdk-devel; \
      fi && \
      sudo dnf clean all; \
    fi

# TODO: update hive to support java 11+
ENV    HADOOP_VERSION=3.1.2
ENV      HIVE_VERSION=3.1.3
ENV     HBASE_VERSION=2.5.15
ENV       TEZ_VERSION=0.9.2
ENV        GO_VERSION=1.25.0

# checksums from archive.apache.org
ENV    HADOOP_SHA512="0e0ee817c89b3c4eb761eca7f16640742a83b0e99b6fda26c1bee2baabedad93aab86e252bf5f1e2381c6d464bc4003d10c7cc0f61b2062f4c59732ca24d1bd9"
ENV      HIVE_SHA256="0c9b6a6359a7341b6029cc9347435ee7b379f93846f779d710b13f795b54bb16"
ENV     HBASE_SHA512="60a07d97ec68203c3494637f4abcb6fdc2fcc75c4c1b88b80d5d8616640e2a57a5193067849f677d441cae558ede69ee4e0ce489816cbcb9117db268fc316ae5"
ENV      TEZ_SHA512="a2d94bd9fa778d42a8bac9d9da8e263e469ddfef93968b06434716554995f490231de5607541ac236e770aa0158b64250c38bc1cd57dbfa629fea705f2ffa2f5"

# faster mirror:
ENV APACHE_MIRROR="repo.huaweicloud.com/apache"
#ENV APACHE_MIRROR="archive.apache.org/dist/"
#ENV APACHE_MIRROR="mirror.yandex.ru/mirrors/apache/"

ENV    HADOOP_URL="https://$APACHE_MIRROR/hadoop/common/hadoop-$HADOOP_VERSION/hadoop-$HADOOP_VERSION.tar.gz"
ENV      HIVE_URL="https://$APACHE_MIRROR/hive/hive-$HIVE_VERSION/apache-hive-$HIVE_VERSION-bin.tar.gz"
ENV     HBASE_URL="https://$APACHE_MIRROR/hbase/$HBASE_VERSION/hbase-$HBASE_VERSION-bin.tar.gz"
ENV       TEZ_URL="https://$APACHE_MIRROR/tez/$TEZ_VERSION/apache-tez-$TEZ_VERSION-bin.tar.gz"

ENV GPHD_ROOT=/home/gpadmin/workspace/singlecluster
ENV HADOOP_ROOT=$GPHD_ROOT/hadoop
ENV HBASE_ROOT=$GPHD_ROOT/hbase
ENV HIVE_ROOT=$GPHD_ROOT/hive
ENV TEZ_ROOT=$GPHD_ROOT/tez

RUN mkdir -p $HADOOP_ROOT && \
    curl -fSL "$HADOOP_URL" -o /tmp/hadoop.tar.gz && \
    echo "$HADOOP_SHA512 /tmp/hadoop.tar.gz" | sha512sum -c && \
    tar xvf /tmp/hadoop.tar.gz -C $HADOOP_ROOT --strip-components 1  --exclude="share/doc/*" --exclude="*-sources.jar" && \
    rm /tmp/hadoop.tar.gz && \
    curl -fSL "https://repo1.maven.org/maven2/javax/activation/javax.activation-api/1.2.0/javax.activation-api-1.2.0.jar" \
      -o $HADOOP_ROOT/share/hadoop/common/lib/javax.activation-api-1.2.0.jar

RUN mkdir -p $HIVE_ROOT && \
    curl -fSL $HIVE_URL -o /tmp/hive.tar.gz && \
    echo "$HIVE_SHA256 /tmp/hive.tar.gz" | sha256sum -c && \
    tar xvf /tmp/hive.tar.gz -C $HIVE_ROOT --strip-components 1 && \
    rm /tmp/hive.tar.gz

RUN mkdir -p $HBASE_ROOT && \
    curl -fSL "$HBASE_URL" -o /tmp/hbase.tar.gz && \
    echo "$HBASE_SHA512 /tmp/hbase.tar.gz" | sha512sum -c && \
    tar xvf /tmp/hbase.tar.gz -C $HBASE_ROOT --strip-components 1 --exclude="docs/*" --exclude="lib/*-tests.jar" --exclude="lib/shaded-clients" && \
    rm /tmp/hbase.tar.gz

RUN mkdir -p $TEZ_ROOT && \
    curl -fSL "$TEZ_URL" -o /tmp/tez.tar.gz && \
    echo "$TEZ_SHA512 /tmp/tez.tar.gz" | sha512sum -c && \
    tar xvf /tmp/tez.tar.gz -C $TEZ_ROOT --strip-components 1 && \
    rm /tmp/tez.tar.gz

# Install Go (required by PXF). Pick archive based on architecture (amd64/arm64).
ARG TARGETARCH
RUN set -e; \
    arch="${TARGETARCH:-$(uname -m)}"; \
    case "$arch" in \
      amd64|x86_64) go_arch="amd64" ;; \
      arm64|aarch64) go_arch="arm64" ;; \
      *) echo "Unsupported architecture: ${arch}"; exit 1 ;; \
    esac; \
    mkdir -p /tmp/pxf_src/ && cd /tmp && \
    wget -O go.tgz -q "https://go.dev/dl/go${GO_VERSION}.linux-${go_arch}.tar.gz" && \
    sudo rm -rf /usr/local/go && sudo tar -C /usr/local -xzf go.tgz && rm go.tgz

COPY ./templates $GPHD_ROOT
COPY ./conf $GPHD_ROOT/conf
COPY ./bin $GPHD_ROOT/bin