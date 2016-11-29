#!/usr/bin/env bash

# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# | This script holds most / all of the verbs related to this project.
# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==

FROM_NAME=kilda/builder-java
FROM_VER=0.0.2
COMPILER_NAME=kilda/builder-maven
COMPILER_VER=latest
COMPILER=${COMPILER_NAME}:${COMPILER_VER}
GROUP=kilda
PROJECT=controller
IMAGE=${GROUP}/${PROJECT}
ORG=org.bitbucket.kilda
VER=0.0.1
SOCK=/var/run/docker.sock:/var/run/docker.sock

# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# Ensure the compiler exists.  Otherwise error out.
# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
verify_image() {
  doit="docker images | grep '${1}' | grep '${2}' &>/dev/null"
  if ! `eval ${doit}` ; then
    echo "The docker image '${1}:${2}', doesn't exist. Please create it."
    exit 1
  fi
}

verify_compiler() {
  verify_image ${COMPILER_NAME} ${COMPILER_VER}
}

verify_from() {
  verify_image ${FROM_NAME} ${FROM_VER}
}

# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# This is really only ran the first time ever to create the root of the code.
# Keeping here for reference wrt how the Hello World was generated.
# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
create() {
  verify_compiler
  if [ ! -d src ] && [ ! -d ${PROJECT} ]
  then
    docker volume create --name m2 >/dev/null
    docker run -it --rm -v m2:/root/.m2 -v $(PWD):/app -w /app ${COMPILER} \
      mvn archetype:generate -DgroupId=${ORG} -DartifactId=${PROJECT} \
      -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
    mv ${PROJECT}/pom.xml .
    mv ${PROJECT}/src .
    rm -fr ${PROJECT}
  else
    echo "The project has already been created."
  fi
}

build() {
  verify_compiler
  verify_from
  if [ -d src ]
  then
    docker volume create --name m2 >/dev/null
    docker run -it --rm -v m2:/root/.m2 -v $(PWD):/app -w /app ${COMPILER} \
      mvn package
  else
    echo "The project source directory 'src' doesn't exist. Run create first"
  fi
}

image() {
  if [ -d src ]
  then
    docker build -t ${IMAGE}:${VER} -t ${IMAGE}:latest .
  else
    echo "The project directory '${PROJECT}' doesn't exist. Run with create && build"
  fi
}

run() {
  echo ""
	echo "==> RUNNING:"
  docker run ${IMAGE}:${VER}
}

main() {
  case "$1" in
    verify) verify_compiler ; verify_from ;;
    create) create ;;
    build)  build ;;
    image)  image ;;
    run)    run ;;
    all)    build ; image ; run ;;
    *)      echo $"Usage: $0 {verify|create|build|image|run|all}" && exit 1
  esac
}

main $@
