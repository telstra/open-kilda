find-python-requirements:
	for requirements_file in $$(find src-python/  -name requirements.txt); do\
            mkdir -p docker/base/kilda-base-lab-service/requirements/$$(dirname $${requirements_file}); \
            cp $${requirements_file} docker/base/kilda-base-lab-service/requirements/$$(dirname $${requirements_file})/; \
        done

.PHONY: find-python-requirements
