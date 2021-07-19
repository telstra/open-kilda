FROM ubuntu:20.04

WORKDIR /root

ENV SERVER42_BUILD_TYPE=Release
ENV RTE_SDK=/root/cmake-build-release/subprojects/Source/dpdk_external
ENV RTE_TARGET=x86_64-native-linuxapp-gcc

COPY ./docker_install_requirements.sh /root/
RUN /root/docker_install_requirements.sh

COPY ./CMakeLists.txt /root/
COPY ./external /root/external

COPY ./build.sh /root/
COPY ./src /root/src

RUN SERVER42_BUILD_TARGETS="boost_external dpdk_external pcapplusplus_external zeromq_external protobuf_external" ./build.sh

CMD ["bash"]