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


# ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# INTRODUCTION:
# - This script will setup key underpinnings for the whole VM.
# ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==

export DEBIAN_FRONTEND=noninteractive

update() {
  apt-get update
  apt-get upgrade -y
}

networking() {
  # --•--•--•--•--•--•--•--•--
  # NETWORKING TOOLS
  # --•--•--•--•--•--•--•--•--
  apt-get install -y apt-utils git sudo net-tools
  apt-get install -y --no-install-recommends \
    curl \
    iproute2 \
    iputils-ping \
    net-tools \
    tcpdump \
    vim
}

docker(){
  # --•--•--•--•--•--•--•--•--
  # DOCKER INSTALL
  # --•--•--•--•--•--•--•--•--
  apt-get install -y \
      linux-image-extra-$(uname -r) \
      linux-image-extra-virtual

  apt-get install -y apt-transport-https \
                         software-properties-common \
                         ca-certificates

  curl -fsSL https://yum.dockerproject.org/gpg | sudo apt-key add -
  apt-get install -y software-properties-common

  add-apt-repository \
         "deb https://apt.dockerproject.org/repo/ \
         ubuntu-$(lsb_release -cs) \
         main"

  apt-get update
  apt-get -y install docker-engine
  # add the vagrant user to the docker group so `sudo` isn't needed to execute docker
  gpasswd -a ${USER} docker
  newgrp docker
}

mininet() {
  apt-get update
  apt-get install -y apt-utils git sudo net-tools
  apt-get install -y --no-install-recommends \
    mininet \
    vim \
    x11-xserver-utils \
    xterm

  # apt-get -y -q install linux-image-4.8.0-37-generic
  # RUN ln -s /lib/modules/4.8.0-37-generic/ /lib/modules/4.9.6-moby

  cd /root
  git clone git://github.com/mininet/mininet
  mininet/util/install.sh -a

}

containernet() {
  apt-get install -y ansible aptitude
  # Patch config file if necessary
  grep "localhost ansible_connection=local" /etc/ansible/hosts >/dev/null
  if [ $? -ne 0 ]; then
    echo "localhost ansible_connection=local" | tee -a /etc/ansible/hosts
  fi

  cd ~
	#rm -rf containernet &> /dev/null
	#rm -rf oflops &> /dev/null
	#rm -rf oftest &> /dev/null
	#rm -rf openflow &> /dev/null
	#rm -rf pox &> /dev/null
  git clone https://github.com/containernet/containernet
  cd containernet/ansible
  ansible-playbook install.yml
}

maxinet() {
  apt-get install -y screen cmake sysstat python-matplotlib

  # Install Pyro
  apt-get install -y python-pip
  pip install --upgrade pip
  pip install Pyro4

  # Install Metis
  mkdir -p /root/metis
  cd /root/metis
  wget http://glaros.dtc.umn.edu/gkhome/fetch/sw/metis/metis-5.1.0.tar.gz
  tar -xzf metis-5.1.0.tar.gz
  rm metis-5.1.0.tar.gz
  cd metis-5.1.0
  make config
  make
  make install

  # Install maxinet
  cd /root
  rm -rf MaxiNet &> /dev/null
  git clone git://github.com/kilda/MaxiNet.git
  cd MaxiNet && git checkout master && make install
}

setup-maxi() {
  cp /usr/local/share/MaxiNet/config.example /etc/MaxiNet.cfg
  sed -i "s/192.168.123.1/127.0.0.1/g" /etc/MaxiNet.cfg
}

shortcut_scripts() {
  # ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
  # Add some simple scripts for launching tools
  # ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
  echo "screen -d -m -S PoxScr /root/pox/pox.py forwarding.l2_learning" \
    > /root/start.pox.sh && chmod a+x /root/start.pox.sh
  echo "screen -d -m -S MaxiNetFrontend MaxiNetFrontendServer" \
    > /root/start.frontend.sh && chmod a+x /root/start.frontend.sh
  echo "screen -d -m -S MaxiNetWorker MaxiNetWorker" \
    > /root/start.worker.sh && chmod a+x /root/start.worker.sh


}


main() {
  echo "Install ALL software for this OpenKilda maxinet VM"
  sudo -s && \
  update && \
  networking
  docker
  mininet
  #containernet
  maxinet
  setup-maxi
  update
  shortcut_scripts
}

main
