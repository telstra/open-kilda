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

ARG base_image=python:3.6-slim
FROM ${base_image}

RUN pip install flask paramiko docker
ADD lockkeeper.py /lockkeeper.py
ADD cli/* /cli/
ENV FLASK_APP=lockkeeper.py
CMD ["flask", "run", "--host=0.0.0.0", "--port=5001"]
