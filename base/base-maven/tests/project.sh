#!/usr/bin/env bash

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
