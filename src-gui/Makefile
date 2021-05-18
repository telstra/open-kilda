APP := openkilda-gui

rebuild: clean clean-java build

build:  clean-cache build/libs/${APP}.war

build/libs/${APP}.war: .deps/node .deps/resources
	./gradlew build

.deps/node: .deps
	docker run --rm -e LOCAL_UID=`id -u $(USER)` -e LOCAL_GID=`id -g $(USER)` -v $(CURDIR)/src:/app/src -v $(CURDIR)/ui:/app/ui node:14.17-alpine  \
		sh -c 'npm cache clean -f && npm install -g @angular/cli@12.0.0 --unsafe-perm && cd /app/ui && npm install && ng build --prod && chown -R $$LOCAL_UID:$$LOCAL_GID /app/src /app/ui'
	touch $@

.deps/resources: .deps
	mkdir -p src/main/webapp/lib/css/
	mkdir -p src/main/webapp/lib/javascript/
	wget -O src/main/webapp/lib/css/roboto.css https://fonts.googleapis.com/css?family=Roboto:100,100i,300,300i,400,400i,500,500i,700,700i
	wget -O src/main/webapp/lib/css/bootstrap.min.css https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css
	wget -O src/main/webapp/lib/javascript/bootstrap.min.js https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js
	wget -O src/main/webapp/lib/javascript/jquery-3.5.1.min.js https://cdnjs.cloudflare.com/ajax/libs/jquery/3.5.1/jquery.js
	touch $@

.deps:
	mkdir -p .deps
	mkdir -p tmp

clean-cache: clean

clean:
	rm -f .deps/node
	rm -f .deps/resources

clean-java:
	./gradlew clean

.PHONY: rebuild clean clean-java
