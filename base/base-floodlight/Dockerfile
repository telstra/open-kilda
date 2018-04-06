# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

FROM kilda/base-ubuntu

ENV MAVEN_OPTS="-Xmx4G"
ENV DEBIAN_FRONTEND=noninteractive

# Add Tools
RUN apt-get update && \
  apt-get install -y --no-install-recommends git ant maven rsync && \
  rm -rf /var/lib/apt/lists/*


# NOTE from dbogun:
#
#    patches make possible to apply floodlight and loxigen changes that are
#    not merged in corresponding repositories yet. This feature required to
#    test floodlight and loxigen changes before pushing them.
#
#    After commiting changes into local floodlight(loxigen) repo you should
#    use command like
#
#    git format-patch -o $DEV_ROOT/open-kilda/base/base-floodlight/patches/floodlight origin/STABLE
#
#    to fill corresponding folder with patches.
#
#    After this you must rebuild floodlight docker container. As result you
#    will have a floodlight docker image/container with you local changes in
#    it.
#
ADD patches /app/patches/

RUN git config --global user.email "dummy@dummy.org"
RUN git config --global user.name "John Doe"

# Build Loxigen
RUN git clone --recursive -b STABLE --single-branch https://github.com/kilda/loxigen.git /app/loxigen
WORKDIR /app/loxigen
RUN git am --quiet /app/patches/loxigen/*.patch || echo "===> NO PATCHES <==="
RUN make java
WORKDIR /app/loxigen/loxi_output/openflowj
RUN mvn install -DskipTests -Dmaven.javadoc.skip=true

# Build Floodlight
RUN git clone --recursive -b STABLE --single-branch  https://github.com/kilda/floodlight.git /app/floodlight
WORKDIR /app/floodlight
RUN git am --quiet /app/patches/floodlight/*.patch || echo "===> NO PATCHES <==="
RUN mvn install -DskipTests
RUN mkdir /var/lib/floodlight
RUN chmod 777 /var/lib/floodlight
