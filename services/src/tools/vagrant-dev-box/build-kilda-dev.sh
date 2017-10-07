#!/bin/bash
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


# ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# INTRODUCTION:
# - This will call packer with default settings for a standard kilda dev VM to be
#   used for Kilda development and testing.
# - The options can be easily modified to suit your needs.
# - The output is a vagrant box, which can be added locally.
# - The Kilda project uses this to create the box that is pushed to atlas,
#   and is used when calling "vagrant box add openkilda/kildadev"
#
# NB:
# - If you're experimenting with building boxes, download the ubuntu iso and
#   place it in either this folder or define "iso_path" before calling this
#   script. "iso_path" will cause packer to look for the iso in that folder.
#   If there is no ISO, it'll just grab it from the internet (650MB+).
# - vagrant, packer, and virtualbox need to be pre-installed
# ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==

root=${PWD}
iso_path=${iso_path:-$root}
custom_script=${custom_script:-$root/install_scripts/all-dev.sh}
packer_path=${packer_path:-$root/packer}
# The packer_path should point to the location of https://github.com/kilda/ubuntu
# A more robust approach could be to download it locally if it doesn't exist (and
# add to .gitignore)

cd =${packer_path} && packer build -only=virtualbox-iso -var-file=ubuntu1704.json \
    -var "version=17.04.02" \
    -var "iso_path=${root}" \
    -var "hostname=kilda" \
    -var "ssh_username=kilda" \
    -var "ssh_password=kilda" \
    -var "vagrantfile_template=${root}/vagrantfile.template" \
    -var "cpus=4" \
    -var "memory=10240" \
    -var "desktop=off" \
    -var "headless=true" \
    -var "custom_script=${custom_script}" \
    ubuntu.json
