FROM kilda/server42dpdk-base:latest

WORKDIR /root

COPY ./src /root/src

COPY ./CMakeLists.txt /root/

RUN ./build.sh

CMD ["bash"]