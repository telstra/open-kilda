FROM kilda/server42dpdk-base:latest

WORKDIR /root

COPY ./src /root/src

COPY ./CMakeLists.txt /root/

RUN ./build.sh

WORKDIR /root/cmake-build-release/

COPY ./local_loop.sh /root/cmake-build-release/

COPY ./docker_entrypoint.py /root/cmake-build-release/

CMD ["./docker_entrypoint.py"]
