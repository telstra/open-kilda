#!/usr/bin/env bash

echo 'deb http://archive.ubuntu.com/ubuntu/ bionic-proposed main' > /etc/apt/sources.list.d/bionic-proposed.list

apt-get update -y && \
apt-get install -y libpcap-dev gcc g++ libnuma-dev make python3.7 python3.7-dev wget linux-headers-$(uname -r) patch sudo iproute2 && \
update-alternatives --install /usr/bin/python python /usr/bin/python3.7 1000 && \
mkdir -p bin && \
mkdir -p tools/cmake && \
cd tools/cmake && \
wget -nc https://github.com/Kitware/CMake/releases/download/v3.15.3/cmake-3.15.3-Linux-x86_64.tar.gz && \
tar -xzvf cmake-3.15.3-Linux-x86_64.tar.gz && \
cd - && \
ln -sf tools/cmake/cmake-3.15.3-Linux-x86_64/bin/cmake .
