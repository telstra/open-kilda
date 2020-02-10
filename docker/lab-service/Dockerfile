# Copyright 2018 Telstra Open Source
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

ARG base_image=kilda/base-lab-service
FROM ${base_image}

ADD kilda_traffexam-*whl /exam/
WORKDIR /exam
RUN for WHEEL_FILE in $(ls -1   *.whl); do pip3 install ${WHEEL_FILE}; done

ADD run.sh /app/

ADD kilda_lab-*whl /app/lab/
ADD log.json /app/lab/
WORKDIR /app/lab
RUN for WHEEL_FILE in $(ls -1   *.whl); do pip3 install ${WHEEL_FILE}; done

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

ENTRYPOINT ["/app/run.sh"]
