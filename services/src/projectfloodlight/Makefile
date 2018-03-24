FLOODLIGHT_DIR = floodlight
LOXIGEN_DIR = loxigen

PROJECTFLOODLIGHT_MAVEN := ~/.m2/repository/org/projectfloodlight
FLOODLIGHT_MAVEN := $(PROJECTFLOODLIGHT_MAVEN)/floodlight
FLOODLIGHT_JAR := $(FLOODLIGHT_MAVEN)/1.2-SNAPSHOT/floodlight-1.2-kilda-2.jar
OPENFLOWJ_MAVEN := $(PROJECTFLOODLIGHT_MAVEN)/openflowj
#
# NB: when changing this output, ensure to update:
# --> services/src/pom.xml
# --> services/wfm/pom.xml
#
OPENFLOWJ_JAR := $(OPENFLOWJ_MAVEN)/3.2.0-SNAPSHOT/openflowj-3.2.0-kilda-2.jar

GENERATE_OPENFLOWJ = $(LOXIGEN_DIR)/loxi_output/openflowj
GENERATE_WIRESHARK = $(LOXIGEN_DIR)/loxi_output/wireshark

default: $(OPENFLOWJ_JAR) $(FLOODLIGHT_JAR)

$(LOXIGEN_DIR):
	git clone -b STABLE --single-branch https://github.com/kilda/loxigen.git $(LOXIGEN_DIR)

$(GENERATE_OPENFLOWJ): git-pull $(LOXIGEN_DIR)/.git/HEAD
	$(MAKE) -C $(LOXIGEN_DIR) java

openflow.lua: $(LOXIGEN_DIR)
	$(MAKE) -C $(LOXIGEN_DIR) wireshark
	sed 's/"eth"/"eth_withoutfcs"/' $(GENERATE_WIRESHARK)/$@ > $@

$(OPENFLOWJ_JAR): $(GENERATE_OPENFLOWJ)
	mvn -B -f $(GENERATE_OPENFLOWJ)/pom.xml clean install

clean-loxigen:
	rm -rf $(OPENFLOWJ_MAVEN)
	rm -f openflow.lua
	$(MAKE) -C $(LOXIGEN_DIR) clean

$(FLOODLIGHT_DIR):
	git clone -b STABLE --single-branch https://github.com/kilda/floodlight.git $(FLOODLIGHT_DIR)

$(FLOODLIGHT_JAR): git-pull
	mvn -B -f $(FLOODLIGHT_DIR)/pom.xml clean install -DskipTests
	#mkdir /var/lib/floodlight
	#chmod 777 /var/lib/floodlight

git-pull: $(LOXIGEN_DIR) $(FLOODLIGHT_DIR)
	(cd "$(LOXIGEN_DIR)"; git fetch origin; git checkout STABLE; git pull -r)
	(cd "$(FLOODLIGHT_DIR)"; git fetch origin; git checkout STABLE; git pull -r)

clean-floodlight:
	rm -rf $(FLOODLIGHT_MAVEN)
	mvn -f $(FLOODLIGHT_DIR)/pom.xml clean

clean: clean-loxigen clean-floodlight

.PHONY: default clean clean-loxigen clean-floodlight git-pull
