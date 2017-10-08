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


FLOODLIGHT_DIR=floodlight
LOXIGEN_DIR=loxigen

PROJECTFLOODLIGHT_MAVEN=~/.m2/repository/org/projectfloodlight
FLOODLIGHT_MAVEN=${PROJECTFLOODLIGHT_MAVEN}/floodlight
OPENFLOWJ_MAVEN=${PROJECTFLOODLIGHT_MAVEN}/openflowj
FLOODLIGHT_JAR=${FLOODLIGHT_MAVEN}/1.2-SNAPSHOT/floodlight-1.2-SNAPSHOT.jar
OPENFLOWJ_JAR=${OPENFLOWJ_MAVEN}/3.2.0-SNAPSHOT/openflowj-3.2.0-SNAPSHOT.jar

GENERATE_OPENFLOWJ=${LOXIGEN_DIR}/loxi_output/openflowj
GENERATE_WIRESHARK=${LOXIGEN_DIR}/loxi_output/wireshark


clean() {
    # OpenFlow
	rm -rf ${OPENFLOWJ_MAVEN}
	rm -f openflow.lua

	#$(MAKE) -C ${LOXIGEN_DIR} clean
	rm -rf ${LOXIGEN_DIR}/loxi_output # only delete generated files in the default directory
	rm -f ${LOXIGEN_DIR}/loxigen.log
	rm -f ${LOXIGEN_DIR}/loxigen-test.log
	rm -f ${LOXIGEN_DIR}/.loxi_ts.*

    # Floodlight
	rm -rf ${FLOODLIGHT_MAVEN}
	mvn -f ${FLOODLIGHT_DIR}/pom.xml clean
}

build_openflow() {
	[ -d ${LOXIGEN_DIR} ] || git clone https://github.com/floodlight/loxigen.git ${LOXIGEN_DIR}
	# [ -d ${LOXIGEN_DIR} ] && (cd ${LOXIGEN_DIR} && git pull && git checkout `git ls-files -m`)
	(cd ${LOXIGEN_DIR} \
	    && git checkout bec5ec03459ae60c8da0ef6a4791e9ceeb7c1939 \
	    && git checkout `git ls-files -m` \
	    && patch -p1 -fs < ../../../../base/base-floodlight/app/loxigen.diff \
	    )
    find . -name "*.rej" | xargs rm
    find . -name "*.orig" | xargs rm

	#$(MAKE) -C ${LOXIGEN_DIR} java
    LOXI_OUTPUT_DIR=loxi_output
	(cd ${LOXIGEN_DIR} \
	    && ./loxigen.py --install-dir=${LOXI_OUTPUT_DIR} --lang=java \
	    && rsync -rt java_gen/pre-written/ ${LOXI_OUTPUT_DIR}/openflowj/ \
	    )

	(cd ${GENERATE_OPENFLOWJ} \
	    && patch -p1 < ../../../../../../base/base-floodlight/app/openflowj.diff \
	    )
	mvn -f ${GENERATE_OPENFLOWJ}/pom.xml clean install
}

build_floodlight() {
	[ -d ${FLOODLIGHT_DIR} ] || git clone https://github.com/kilda/floodlight.git ${FLOODLIGHT_DIR}
	[ -d ${FLOODLIGHT_DIR} ] && (cd ${FLOODLIGHT_DIR} && git pull && git checkout `git ls-files -m`)

	(cd ${FLOODLIGHT_DIR} \
	    && patch -p1 -fs < ../../../../base/base-floodlight/app/floodlight.diff \
	    )
	mvn -f ${FLOODLIGHT_DIR}/pom.xml clean install -DskipTests
}

main() {
  case "$1" in
    clean)      clean ;;
    openflow)   build_openflow ;;
    floodlight) build_floodlight ;;
    build_all)  build_openflow ; build_floodlight ;;
    *)      echo $"Usage: $0 {clean | openflow | floodlight | build_all}" && exit 1
  esac

}

main $@