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

##
## NB: The verify_from() function in project.sh is used to verify the FROM exists
##
FROM kilda/builder-java:0.0.2

ENV PROJECT controller
ENV VERSION 1.0-SNAPSHOT
ENV SRC target/${PROJECT}-${VERSION}-jar-with-dependencies.jar
ENV DST /app/${PROJECT}-${VERSION}.jar
ENV MAIN org.bitbucket.kilda.controller.Main

COPY ${SRC} ${DST}
CMD ["sh", "-c", "java ${JAVA_OPTIONS} -cp ${DST} ${MAIN}"]
