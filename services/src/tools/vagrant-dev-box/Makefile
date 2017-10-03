# This should use the location of the box built by packer.
# It assumes that is located locally ... should use a env setting
PACKER_PATH ?= "packer/ubuntu"

build-dev:
	./build-kilda-dev.sh

add-dev:
	vagrant box add --force --clean openkilda/kildadev \
	 	$(PACKER_PATH)/box/virtualbox/ubuntu1704-17.04.02.box

test-dev:
	@rm -fr tmp 2>&1 >> /dev/null
	@mkdir tmp
	cd tmp && vagrant init openkilda/kildadev && vagrant up && vagrant ssh

destroy-dev:
	cd tmp && vagrant destroy --force 2>&1 >> /dev/null

.PHONY: build-dev add-dev test-dev destroy-dev
