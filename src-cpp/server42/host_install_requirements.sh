#!/usr/bin/env bash

sudo apt-get update -y && \
sudo apt-get install -y libpcap-dev gcc g++ libnuma-dev make python3-dev wget linux-headers-$(uname -r) && \
mkdir -p tools/cmake && \
cd tools/cmake && \
wget -nc https://github.com/Kitware/CMake/releases/download/v3.15.3/cmake-3.15.3-Linux-x86_64.tar.gz && \
tar -xzvf cmake-3.15.3-Linux-x86_64.tar.gz && \
cd - && \
ln -sf tools/cmake/cmake-3.15.3-Linux-x86_64/bin/cmake .
