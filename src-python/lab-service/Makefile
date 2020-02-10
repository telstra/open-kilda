ROOT=../..

find-python-requirements:
	for requirements_file in $$(find . -name requirements.txt); do\
		echo; echo "# $$requirements_file"; echo; \
		cat "$${requirements_file}"; \
	done > "$(ROOT)/docker/base/kilda-base-lab-service/merged-requirements.txt"

.PHONY: find-python-requirements
