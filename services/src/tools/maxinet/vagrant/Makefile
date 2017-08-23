build:
	./build-16.10.sh

add:
	vagrant box add --force openkilda/maxinet \
	 	packer/ubuntu/box/virtualbox/ubuntu1610-0.1.0.box

test:
	@rm -fr tmp 2>&1 >> /dev/null
	@mkdir tmp
	cd tmp && vagrant init openkilda/maxinet && vagrant up && vagrant ssh

destroy:
	cd tmp && vagrant destroy --force 2>&1 >> /dev/null

.PHONY: build add test destroy


build-dev:
	./build-kilda-dev.sh

add-dev:
	vagrant box add --force --clean openkilda/kildadev \
	 	packer/ubuntu/box/virtualbox/ubuntu1704-17.04.02.box

test-dev:
	@rm -fr tmp 2>&1 >> /dev/null
	@mkdir tmp
	cd tmp && vagrant init openkilda/kildadev && vagrant up && vagrant ssh

destroy-dev:
	cd tmp && vagrant destroy --force 2>&1 >> /dev/null

.PHONY: build-dev add-dev test-dev destroy-dev
