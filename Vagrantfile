# -*- mode: ruby -*-
# vi: set ft=ruby :

# All Vagrant configuration is done below. The "2" in Vagrant.configure
# configures the configuration version (we support older styles for
# backwards compatibility). Please don't change it unless you know what
# you're doing.
Vagrant.configure("2") do |config|
  # The most common configuration options are documented and commented below.
  # For a complete reference, please see the online documentation at
  # https://docs.vagrantup.com.

  # Every Vagrant development environment requires a box. You can search for
  # boxes at https://atlas.hashicorp.com/search.
  # config.vm.box = "ubuntu/xenial64"
  config.vm.box = "envimation/ubuntu-xenial-docker"
  #config.vm.box_url = "https://atlas.hashicorp.com/envimation/boxes/ubuntu-xenial-docker"

  # Disable automatic box update checking. If you disable this, then
  # boxes will only be checked for updates when the user runs
  # `vagrant box outdated`. This is not recommended.
  # config.vm.box_check_update = false

  # Create a forwarded port mapping which allows access to a specific port
  # within the machine from a port on the host machine. In the example below,
  # accessing "localhost:8080" will access port 80 on the guest machine.
  config.vm.network "forwarded_port", guest: 7474, host: 7474  # neo4j
  config.vm.network "forwarded_port", guest: 80,   host: 80    # TPE REST
  config.vm.network "forwarded_port", guest: 2181, host: 2181  # Zookeeper
  config.vm.network "forwarded_port", guest: 9092, host: 9002  # Kafka
  config.vm.network "forwarded_port", guest: 8888, host: 8888  # Storm UI
  config.vm.network "forwarded_port", guest: 8081, host: 8081  # floodlight
  config.vm.network "forwarded_port", guest: 8088, host: 8088  # northbound
  config.vm.network "forwarded_port", guest: 4242, host: 4242  # opentsdb

  # Create a private network, which allows host-only access to the machine
  # using a specific IP.
  # config.vm.network "private_network", ip: "192.168.8.8"

  # Create a public network, which generally matched to bridged network.
  # Bridged networks make the machine appear as another physical device on
  # your network.
  # config.vm.network "public_network"

  # Share an additional folder to the guest VM. The first argument is
  # the path on the host to the actual folder. The second argument is
  # the path on the guest to mount the folder. And the optional third
  # argument is a set of non-required options.
  # config.vm.synced_folder "../data", "/vagrant_data"

  # Provider-specific configuration so you can fine-tune various
  # backing providers for Vagrant. These expose provider-specific options.
  # Example for VirtualBox:
  #
    config.vm.provider "virtualbox" do |vb|
       # Display the VirtualBox GUI when booting the machine
       #vb.gui = true

       # Customize the amount of memory on the VM:
       vb.memory = "10240"
       vb.cpus = "4"
    end

  # View the documentation for the provider you are using for more
  # information on available options.

  # Define a Vagrant Push strategy for pushing to Atlas. Other push strategies
  # such as FTP and Heroku are also available. See the documentation at
  # https://docs.vagrantup.com/v2/push/atlas.html for more information.
  # config.push.define "atlas" do |push|
  #   push.app = "YOUR_ATLAS_USERNAME/YOUR_APPLICATION_NAME"
  # end

  # Enable provisioning with a shell script. Additional provisioners such as
  # Puppet, Chef, Ansible, Salt, and Docker are also available. Please see the
  # documentation for more information about their specific syntax and use.
  # config.vm.provision "shell", inline: <<-SHELL
  #   apt-get update
  #   apt-get install -y apache2
  # SHELL
end

# mininet ports:
#      - "38080:38080"
#      - "17191:17191"

# neo4j ports:
#      - "7474:7474"
#      - "7687:7687"

# topology-engine-rest ports:
#      - "80:80"


# zookeeper ports:
#      - "2181:2181"

# kafka ports:
#      - "9092:9092"

# hbase ports:
#      - "60000:60000"
#      - "60010:60010"
#      - "60020:60020"
#      - "60030:60030"
#      - "8070:8070"
#      - "8090:8090"
#      - "9070:9070"
#      - "9080:9080"
#      - "9090:9090"

# storm_nimbus ports:
#      - "6627:6627"
#      - "3772:3772"
#      - "3773:3773"
#      - "8000:8000"

# storm_ui ports:
#- "8888:8080"

# storm_supervisor ports:
#- "6700:6700"
#- "6701:6701"
#- "6702:6702"
##- "6703:6703"
#- "6704:6704"
#- "6705:6705"
#- "6706:6706"
#- "6707:6707"
#- "6708:6708"
#- "6709:6709"
#- "6710:6710"
#- "6711:6711"
#- "6712:6712"
#- "6713:6713"
#- "6714:6714"
#- "8001:8000"

# floodlight ports:
#- "6653:6653"
#- "8180:8080"
#- "6655:6655"
#- "6642:6642"
#- "8081:8080"


#- "8088:8080"  # northbound:
#- "4242:4242"  # opentsdb:
