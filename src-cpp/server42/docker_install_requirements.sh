#!/usr/bin/env bash

echo 'deb http://archive.ubuntu.com/ubuntu/ bionic-proposed main' > /etc/apt/sources.list.d/bionic-proposed.list

apt-get update -y && \
apt-get install --no-install-recommends -y libpcap-dev gcc g++ libnuma-dev make python3 python3-dev python3-pip wget linux-headers-$(uname -r) patch sudo iproute2 git && \
mkdir -p bin && \
mkdir -p tools/cmake && \
cd tools/cmake && \
wget -nc https://github.com/Kitware/CMake/releases/download/v3.15.3/cmake-3.15.3-Linux-x86_64.tar.gz && \
tar -xzvf cmake-3.15.3-Linux-x86_64.tar.gz && \
cd - && \
ln -sf tools/cmake/cmake-3.15.3-Linux-x86_64/bin/cmake . && \
pip3 install "docker==4.4.4" netifaces==0.11.0