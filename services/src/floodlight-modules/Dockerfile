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

ADD src/main/resources/floodlightkilda.properties /app
ADD src/main/resources/logback.xml /app
ADD run-deps/floodlight.jar /app/floodlight.jar
ADD target/floodlight-modules.jar /app/floodlight-modules.jar

CMD ["java", "-Dlogback.configurationFile=/app/logback.xml", "-cp", \
     "/app/floodlight.jar:/app/floodlight-modules.jar", \
     "net.floodlightcontroller.core.Main", "-cf", "/app/floodlightkilda.properties"]

