FROM apache/incubator-cloudberry:cbdb-build-ubuntu22.04-latest

ENV DEBIAN_FRONTEND=noninteractive

# bake in java 11 into image
RUN sudo apt-get update && \
    sudo apt-get install -y --no-install-recommends \
      openjdk-11-jdk-headless \
      curl ca-certificates git unzip maven \
      wget lsb-release locales openssh-server iproute2 sudo && \
    sudo rm -rf /var/lib/apt/lists/*

# Install Gradle and warm wrapper cache (it will download GRADLE_VERSION again)
# (version must match server/gradle/wrapper/gradle-wrapper.properties)
ARG GRADLE_VERSION=6.8.2
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
      -o /tmp/gradle.zip && \
    sudo unzip -q /tmp/gradle.zip -d /opt && \
    sudo ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle && \
    rm /tmp/gradle.zip
RUN cd /tmp && gradle init && gradle wrapper --gradle-version ${GRADLE_VERSION} && ./gradlew javaToolchains
RUN rm -rf /tmp/gradle /tmp/gradlew /tmp/gradlew.bat /tmp/.gradle
ENV GRADLE_HOME="/opt/gradle-${GRADLE_VERSION}"

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
COPY script/build_cloudberrry.sh /tmp/build_cloudberrry.sh
RUN bash /tmp/build_cloudberrry.sh