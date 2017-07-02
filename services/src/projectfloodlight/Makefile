FLOODLIGHT_DIR = floodlight
LOXIGEN_DIR = loxigen

PROJECTFLOODLIGHT_MAVEN := ~/.m2/repository/org/projectfloodlight
FLOODLIGHT_MAVEN := $(PROJECTFLOODLIGHT_MAVEN)/floodlight
FLOODLIGHT_JAR := $(FLOODLIGHT_MAVEN)/1.2-SNAPSHOT/floodlight-1.2-SNAPSHOT.jar
OPENFLOWJ_MAVEN := $(PROJECTFLOODLIGHT_MAVEN)/openflowj
OPENFLOWJ_JAR := $(OPENFLOWJ_MAVEN)/3.2.0-SNAPSHOT/openflowj-3.2.0-SNAPSHOT.jar

GENERATE_OPENFLOWJ = $(LOXIGEN_DIR)/loxi_output/openflowj
GENERATE_WIRESHARK = $(LOXIGEN_DIR)/loxi_output/wireshark

default: $(OPENFLOWJ_JAR) openflow.lua $(FLOODLIGHT_JAR)

$(LOXIGEN_DIR):
	git clone https://github.com/floodlight/loxigen.git $(LOXIGEN_DIR)
	cd $(LOXIGEN_DIR) && git checkout bec5ec03459ae60c8da0ef6a4791e9ceeb7c1939
	cp ../../../base/base-floodlight/app/legacy_meter $(LOXIGEN_DIR)/openflow_input/
	cp ../../../base/base-floodlight/app/nicira_l2 $(LOXIGEN_DIR)/openflow_input/
	cd $(LOXIGEN_DIR) && patch -p1 < ../../../../base/base-floodlight/app/loxigen.diff

$(GENERATE_OPENFLOWJ): $(LOXIGEN_DIR)
	$(MAKE) -C $(LOXIGEN_DIR) java

openflow.lua: $(LOXIGEN_DIR)
	$(MAKE) -C $(LOXIGEN_DIR) wireshark
	sed 's/"eth"/"eth_withoutfcs"/' $(GENERATE_WIRESHARK)/$@ > $@

$(OPENFLOWJ_JAR): $(GENERATE_OPENFLOWJ)
	mvn -f $(GENERATE_OPENFLOWJ)/pom.xml clean install

clean-loxigen:
	rm -rf $(OPENFLOWJ_MAVEN)
	rm -f openflow.lua
	$(MAKE) -C $(LOXIGEN_DIR) clean

$(FLOODLIGHT_DIR):
	git clone https://github.com/kilda/floodlight.git $(FLOODLIGHT_DIR)
	cd $(FLOODLIGHT_DIR) && patch -p1 < ../../../../base/base-floodlight/app/floodlight.diff

$(FLOODLIGHT_JAR): $(FLOODLIGHT_DIR)
	mvn -f $(FLOODLIGHT_DIR)/pom.xml clean install -DskipTests
	#mkdir /var/lib/floodlight
	#chmod 777 /var/lib/floodlight

clean-floodlight:
	rm -rf $(FLOODLIGHT_MAVEN)
	mvn -f $(FLOODLIGHT_DIR)/pom.xml clean

clean: clean-loxigen clean-floodlight

.PHONY: default clean clean-loxigen clean-floodlight
