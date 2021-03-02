#!/usr/bin/env bash

if [ "${SERVER42_BUILD_TYPE}" = "Debug" ]; then
  mkdir -p cmake-build-debug
  cd cmake-build-debug
else
  SERVER42_BUILD_TYPE=Release
  mkdir -p cmake-build-release
  cd cmake-build-release
fi

if [ "${SERVER42_BUILD_TARGETS}" = "" ]; then
    SERVER42_BUILD_TARGETS="server42 server42_test"
fi

#boost dpdk pcapplusplus zeromq protobuf

echo "Build type set to ${SERVER42_BUILD_TYPE}"
echo "Build targets set to ${SERVER42_BUILD_TARGETS}"

../cmake -DCMAKE_BUILD_TYPE=${SERVER42_BUILD_TYPE} -G "CodeBlocks - Unix Makefiles" ../
../cmake --build . --target ${SERVER42_BUILD_TARGETS} -- -j4
