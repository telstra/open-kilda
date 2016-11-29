FROM ubuntu:yakkety

# Ensure the ability to build containers exists, in case that _is_ the build artifact

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update
RUN apt-get install -y --no-install-recommends apt-utils
RUN apt-get -y install \
  docker.io \
  python python-pip curl
RUN pip install --upgrade pip
RUN pip install docker-compose
