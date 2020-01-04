#
# NB: when changing this variables, ensure to update:
# --> services/src/pom.xml
# --> services/wfm/pom.xml
OPENFLOWJ_VERSION := 3.2.0-kilda-2
FLOODLIGHT_VERSION := 1.2-kilda-2

GIT_HEAD := .git/refs/heads/STABLE

FLOODLIGHT_REPO_URL := https://github.com/kilda/floodlight.git
FLOODLIGHT_DIR := floodlight
FLOODLIGHT_SOURCES := $(FLOODLIGHT_DIR)/$(GIT_HEAD)
FLOODLIGHT_JAR := $(FLOODLIGHT_DIR)/target/floodlight-1.2-kilda-2.jar
FLOODLIGHT_JAR += $(FLOODLIGHT_DIR)/target/floodlight.jar

LOXIGEN_REPO_URL := https://github.com/kilda/loxigen.git
LOXIGEN_DIR := loxigen
LOXIGEN_SOURCES := $(LOXIGEN_DIR)/$(GIT_HEAD)
OPENFLOWJ_DIR := $(LOXIGEN_DIR)/loxi_output/openflowj
OPENFLOWJ_JAR := $(OPENFLOWJ_DIR)/target/openflowj-$(OPENFLOWJ_VERSION).jar
GENERATE_WIRESHARK := $(LOXIGEN_DIR)/loxi_output/wireshark

sync-and-install-all: git-validate-repo git-pull install-all

install-all: install-floodlight install-openflowj

# in case you have applied some patches on loxigen or floodlight sources
# and whant to rebuild corresponding packages
force-install-all:
	touch $(FLOODLIGHT_SOURCES)
	touch $(LOXIGEN_SOURCES)
	$(MAKE) install-all

floodlight_install_deps := install-openflowj $(FLOODLIGHT_JAR)
ifdef SHARE
floodlight_install_deps += $(SHARE)/build-artifact/floodlight.jar
endif

install-floodlight: $(floodlight_install_deps)
	mvn -B -f $(FLOODLIGHT_DIR)/pom.xml jar:jar install:install -DskipTests

install-openflowj: $(OPENFLOWJ_JAR)
	mvn -B -f $(OPENFLOWJ_DIR)/pom.xml jar:jar install:install -DskipTests

clean: clean-loxigen clean-floodlight

$(LOXIGEN_SOURCES):
	git clone -b STABLE --single-branch $(LOXIGEN_REPO_URL) $(LOXIGEN_DIR)

$(OPENFLOWJ_DIR)/pom.xml: $(LOXIGEN_SOURCES)
	$(MAKE) -C $(LOXIGEN_DIR) java
	touch $@

$(OPENFLOWJ_JAR): $(OPENFLOWJ_DIR)/pom.xml
	mvn -B -f $(OPENFLOWJ_DIR)/pom.xml clean package -DskipTests

openflow.lua: $(LOXIGEN_SOURCES)
	$(MAKE) -C $(LOXIGEN_DIR) wireshark
	sed 's/"eth"/"eth_withoutfcs"/' $(GENERATE_WIRESHARK)/$@ > $@

clean-loxigen:
	rm -f openflow.lua
	if [ -d "$(LOXIGEN_DIR)" ]; then $(MAKE) -C $(LOXIGEN_DIR) clean; fi

$(FLOODLIGHT_SOURCES):
	git clone -b STABLE --single-branch $(FLOODLIGHT_REPO_URL) $(FLOODLIGHT_DIR)

$(FLOODLIGHT_JAR): $(FLOODLIGHT_SOURCES)
	mvn -B -f $(FLOODLIGHT_DIR)/pom.xml clean package -DskipTests

ifdef SHARE
$(SHARE)/build-artifact/floodlight.jar: $(FLOODLIGHT_JAR)
	cp $(FLOODLIGHT_DIR)/target/floodlight.jar $(SHARE)/build-artifact/floodlight.jar
endif

clean-floodlight:
	if [ -d "$(FLOODLIGHT_DIR)" ]; then  mvn -f $(FLOODLIGHT_DIR)/pom.xml clean; fi

git-pull: $(LOXIGEN_SOURCES) $(FLOODLIGHT_SOURCES)
	cd "$(LOXIGEN_DIR)" && git fetch origin && git checkout STABLE && git pull -r
	cd "$(FLOODLIGHT_DIR)" && git fetch origin && git checkout STABLE && git pull -r

git-validate-repo: $(LOXIGEN_SOURCES) $(FLOODLIGHT_SOURCES)
	[ "$$(cd $(LOXIGEN_DIR) && git remote get-url origin)" = "$(LOXIGEN_REPO_URL)" ]
	[ "$$(cd $(FLOODLIGHT_DIR) && git remote get-url origin)" = "$(FLOODLIGHT_REPO_URL)" ]

.PHONY: sync-and-install-all install-all force-install-all
.PHONY: install-floodlight install-openflowj
.PHONY: clean clean-loxigen clean-floodlight
.PHONY: git-validate-repo
