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

.PHONY: build boxadd testbox
#   rm -fr tmp 2>&1 >> /dev/null && mkdir tmp
