FROM mcr.microsoft.com/devcontainers/universal:2-linux

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y tmux \
    && rm -rf /var/lib/apt/lists/*

RUN wget -O /tmp/temurin.tar.gz https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz \
    && mkdir -p /opt/java/temurin \
    && tar -xzf /tmp/temurin.tar.gz -C /opt/java/temurin --strip-components=1 \
    && rm /tmp/temurin.tar.gz

ENV JAVA_HOME=/opt/java/temurin
ENV PATH=$JAVA_HOME/bin:$PATH
