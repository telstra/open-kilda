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
CONFIG_OVERRIDES_DIR=${PWD}/config
CONFIG_OVERRIDES_FILE=${CONFIG_OVERRIDES_DIR}/kilda.yml

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

# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# | verify_compiler - verifies the compiler image exists and ensures the maven data
# |   volume exists too.
# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
verify_compiler() {
  verify_image ${COMPILER_NAME} ${COMPILER_VER}
  docker volume create --name m2 >/dev/null
}

verify_from() {
  verify_image ${FROM_NAME} ${FROM_VER}
}

verify_source_exists() {
  if ! [ -d src ] ; then
    echo "The project source directory 'src' doesn't exist. Something must be wrong."
    exit 1
  fi
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
  verify_source_exists

  docker run -it --rm -v m2:/root/.m2 -v ${PWD}:/app -w /app ${COMPILER} \
    mvn package
}

# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# | run - Run "locally" - ie not its own image
# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
run() {
  verify_compiler
  verify_source_exists

  echo ""
  echo "==> RUNNING 'LOCALLY':"
  # NB: this leverages the built-in definitions / jar.
  docker run -it --rm --network=host -v m2:/root/.m2 -v $(PWD):/app -w /app ${COMPILER} mvn exec:java

# NB: here is another mechanism, where you can specify the main class
#  local main_class="org.bitbucket.kilda.controller.Main"
#  docker run -it --rm -v m2:/root/.m2 -v $(PWD):/app -w /app ${COMPILER} \
#    mvn exec:java -Dexec.mainClass=${main_class} -Dexec.args="%classpath"
}

image() {
  verify_from
  verify_source_exists

  docker build -t ${IMAGE}:${VER} -t ${IMAGE}:latest .
}

# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# | drun - Docker Run .. run the docker image
# +=•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
drun() {
  echo ""
  echo -n "==> RUNNING in DOCKER"
  if [ -f "${CONFIG_OVERRIDES_FILE}" ]; then
    echo " with default config plus overrides from ${CONFIG_OVERRIDES_FILE} YAML file"
    DOCKER_OPTIONS="-v ${CONFIG_OVERRIDES_DIR}:/app/config"
    JAVA_OPTIONS="-Dcontroller.config.overrides.file=/app/config/kilda.yml"
  else
    echo " with default config (create ${CONFIG_OVERRIDES_FILE} YAML file to override any defaults)"
  fi

  docker run --network=host -e "JAVA_OPTIONS=${JAVA_OPTIONS}" ${DOCKER_OPTIONS} ${IMAGE}:${VER}
}

main() {
  case "$1" in
    verify) verify_compiler ; verify_from ;;
    create) create ;;
    build)  build ;;
    run)    run ;;
    image)  image ;;
    drun)   drun ;;
    all)    build ; image ; drun ;;
    *)      echo $"Usage: $0 {verify | create | build | run | image | drun | all}" && exit 1
  esac
}

main $@
