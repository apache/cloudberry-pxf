FROM apache/incubator-cloudberry:cbdb-build-ubuntu22.04-latest

ENV DEBIAN_FRONTEND noninteractive

RUN sudo apt-get update &&  \
    sudo apt-get install -y --no-install-recommends \
      curl ca-certificates

# TODO: update hive to support java 11+
ENV    HADOOP_VERSION=3.1.2
ENV      HIVE_VERSION=3.1.3
ENV ZOOKEEPER_VERSION=3.5.9
ENV     HBASE_VERSION=2.0.6
ENV       TEZ_VERSION=0.9.2

# checksums from archive.apache.org
ENV    HADOOP_SHA512="0e0ee817c89b3c4eb761eca7f16640742a83b0e99b6fda26c1bee2baabedad93aab86e252bf5f1e2381c6d464bc4003d10c7cc0f61b2062f4c59732ca24d1bd9"
ENV      HIVE_SHA256="0c9b6a6359a7341b6029cc9347435ee7b379f93846f779d710b13f795b54bb16"
ENV ZOOKEEPER_SHA512="0e5a64713abc6f36d961dd61a06f681868171a9d9228366e512a01324806d263e05508029c94d8e18307811867cdc39d848e736c252bf56c461273ef74c66a45"
ENV     HBASE_SHA512="a0e10904ecf7f059b77bc0ce704254046a978126db720cc7e55dc53b87097715da64b8391fe3cc94348bc432871ad8f29891dc8df1ea052eb628da0fdca97c93"
ENV      TEZ_SHA512="a2d94bd9fa778d42a8bac9d9da8e263e469ddfef93968b06434716554995f490231de5607541ac236e770aa0158b64250c38bc1cd57dbfa629fea705f2ffa2f5"

# faster mirror:
ENV APACHE_MIRROR="repo.huaweicloud.com/apache"
#ENV APACHE_MIRROR="archive.apache.org/dist/"
#ENV APACHE_MIRROR="mirror.yandex.ru/mirrors/apache/"

ENV    HADOOP_URL="https://$APACHE_MIRROR/hadoop/common/hadoop-$HADOOP_VERSION/hadoop-$HADOOP_VERSION.tar.gz"
ENV      HIVE_URL="https://$APACHE_MIRROR/hive/hive-$HIVE_VERSION/apache-hive-$HIVE_VERSION-bin.tar.gz"
ENV ZOOKEEPER_URL="https://$APACHE_MIRROR/zookeeper/zookeeper-$ZOOKEEPER_VERSION/apache-zookeeper-$ZOOKEEPER_VERSION-bin.tar.gz"
ENV     HBASE_URL="https://$APACHE_MIRROR/hbase/$HBASE_VERSION/hbase-$HBASE_VERSION-bin.tar.gz"
ENV       TEZ_URL="https://$APACHE_MIRROR/tez/$TEZ_VERSION/apache-tez-$TEZ_VERSION-bin.tar.gz"

ENV GPHD_ROOT=/home/gpadmin/workspace/singlecluster
ENV HADOOP_ROOT=$GPHD_ROOT/hadoop
ENV HBASE_ROOT=$GPHD_ROOT/hbase
ENV HIVE_ROOT=$GPHD_ROOT/hive
ENV ZOOKEEPER_ROOT=$GPHD_ROOT/zookeeper
ENV TEZ_ROOT=$GPHD_ROOT/tez

RUN mkdir -p $HADOOP_ROOT && \
    curl -fSL "$HADOOP_URL" -o hadoop.tar.gz && \
    echo "$HADOOP_SHA512 hadoop.tar.gz" | sha512sum -c && \
    tar xvf hadoop.tar.gz -C $HADOOP_ROOT --strip-components 1  --exclude="share/doc/*" --exclude="*-sources.jar" && \
    rm hadoop.tar.gz

RUN mkdir -p $HIVE_ROOT && \
    curl -fSL $HIVE_URL -o hive.tar.gz && \
    echo "$HIVE_SHA256 hive.tar.gz" | sha256sum -c && \
    tar xvf hive.tar.gz -C $HIVE_ROOT --strip-components 1 && \
    rm hive.tar.gz

RUN mkdir -p $ZOOKEEPER_ROOT && \
    curl -fSL $ZOOKEEPER_URL -o zookeeper.tar.gz && \
    echo "$ZOOKEEPER_SHA512 zookeeper.tar.gz" | sha512sum -c && \
    tar xvf zookeeper.tar.gz -C $ZOOKEEPER_ROOT --strip-components 1 --exclude="docs/*" && \
    rm zookeeper.tar.gz

RUN mkdir -p $HBASE_ROOT && \
    curl -fSL "$HBASE_URL" -o hbase.tar.gz && \
    echo "$HBASE_SHA512 hbase.tar.gz" | sha512sum -c && \
    tar xvf hbase.tar.gz -C $HBASE_ROOT --strip-components 1 --exclude="docs/*" && \
    rm hbase.tar.gz

RUN mkdir -p $TEZ_ROOT && \
    curl -fSL "$TEZ_URL" -o tez.tar.gz && \
    echo "$TEZ_SHA512 tez.tar.gz" | sha512sum -c && \
    tar xvf tez.tar.gz -C $TEZ_ROOT --strip-components 1 && \
    rm tez.tar.gz

# Install Go (required by PXF).
RUN mkdir -p /tmp/pxf_src/ && cd /tmp \
    && wget -O go.tgz -q https://go.dev/dl/go1.23.3.linux-amd64.tar.gz \
    && sudo rm -rf /usr/local/go && sudo tar -C /usr/local -xzf go.tgz && rm go.tgz


COPY ./templates $GPHD_ROOT
COPY ./conf $GPHD_ROOT/conf
COPY ./bin $GPHD_ROOT/bin