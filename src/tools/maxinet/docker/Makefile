build:
	docker image build -t openkilda/maxinet .

run:
	docker container run -d -it --rm --privileged --name mymaxi openkilda/maxinet

exec:
	docker container exec -it mymaxi /bin/bash

clean:
	docker container stop mymaxi >> /dev/null 2>&1  || true
