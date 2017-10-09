#!/usr/bin/env bash
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


# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# | This script holds most / all of the verbs related to this project.
# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==

GROUP=kilda 
IMAGE=${GROUP}/builder-maven:latest
PROJECT=java-hello-world
ORG=org.bitbucket.kilda
VER=0.0.1
SOCK=/var/run/docker.sock:/var/run/docker.sock

create() {
  if [ ! -d ${PROJECT} ]
  then
    docker volume create --name m2 >/dev/null
    docker run -it --rm -v m2:/root/.m2 -v $(PWD):/app -w /app ${IMAGE} \
      mvn archetype:generate -DgroupId=${ORG} -DartifactId=${PROJECT} \
      -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
  fi
}

build() {
  if [ -d ${PROJECT} ]
  then
    docker volume create --name m2 >/dev/null
    docker run -it --rm -v m2:/root/.m2 -v $(PWD):/app -w /app ${IMAGE} \
      mvn package -f ${PROJECT}
  else
    echo "The project directory '${PROJECT}' doesn't exist. Run with create"
  fi
}

image() {
  if [ -d ${PROJECT} ]
  then
    docker build -t ${GROUP}/${PROJECT}:${VER} -t ${GROUP}/${PROJECT}:latest .
  else
    echo "The project directory '${PROJECT}' doesn't exist. Run with create && build"
  fi
}

run() {
  echo ""
	echo "==> RUNNING:"
  docker run ${GROUP}/${PROJECT}:${VER}
}

main() {
  case "$1" in
    create) create ;;
    build)  build ;;
    image)  image ;;
    run)    run ;;
    all)    create ; build ; image ; run ;;
    *)      echo $"Usage: $0 {create|build|image|run|all}" && exit 1
  esac
}

main $@
