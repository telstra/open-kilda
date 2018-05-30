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


cd /app

##
## Add all topologies here .. so that kilda comes up with them.
##
export PATH=${PATH}:/opt/storm/bin

storm list | grep wfm >/dev/null && storm kill wfm && sleep 5
storm list | grep flow >/dev/null && storm kill flow && sleep 5
storm list | grep stats >/dev/null && storm kill stats && sleep 5
storm list | grep cache >/dev/null && storm kill cache && sleep 5
storm list | grep islstats >/dev/null && storm kill islstats && sleep 5
storm list | grep opentsdb >/dev/null && storm kill opentsdb && sleep 5
storm list | grep portstate >/dev/null && storm kill portstate && sleep 5
storm list | grep nbworker >/dev/null && storm kill nbworker && sleep 5

config_file=$1

set -e

make deploy-wfm config=$config_file
make deploy-flow config=$config_file
make deploy-stats config=$config_file
make deploy-cache config=$config_file
make deploy-islstats config=$config_file
make deploy-opentsdb config=$config_file
make deploy-portstate config=$config_file
make deploy-nbworker config=$config_file
