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

PROJECT_NAME = 'kilda'
COMPONENT_NAME = 'topology-engine'
COMPONENT_NAME_ABBREVIATION = 'TE'

LOG_FORMAT_KEY = 'context'

LOG_ATTR_CORRELATION_ID = 'correlation_id'
LOG_ATTR_JSON_PAYLOAD = 'json_payload'

LOG_ALL_FORMAT_ATTR = {
    LOG_ATTR_CORRELATION_ID}
LOG_ALL_TAIL_ATTR = {
    LOG_ATTR_JSON_PAYLOAD}
