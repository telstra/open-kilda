APP := openkilda-gui

rebuild: clean-java build

build: target/${APP}.jar

target/${APP}.jar: .deps/node .deps/resources
	mvn -B clean install

.deps/node: .deps
	docker run --rm -e LOCAL_UID=`id -u $(USER)` -e LOCAL_GID=`id -g $(USER)` -v $(CURDIR)/src:/app/src -v $(CURDIR)/ui:/app/ui node:10-alpine \
		sh -c 'npm cache clean -f && npm install -g @angular/cli@latest --unsafe-perm && cd /app/ui && npm install && ng build --prod && chown -R $$LOCAL_UID:$$LOCAL_GID /app/src /app/ui'
	touch $@

.deps/resources: .deps
	mkdir -p src/main/resources/lib/css/
	mkdir -p src/main/resources/lib/javascript/
	wget -O src/main/resources/lib/css/roboto.css https://fonts.googleapis.com/css?family=Roboto:100,100i,300,300i,400,400i,500,500i,700,700i
	wget -O src/main/resources/lib/css/bootstrap.min.css https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css
	wget -O src/main/resources/lib/javascript/bootstrap.min.js https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js
	wget -O src/main/resources/lib/javascript/jquery-3.2.1.min.js https://cdnjs.cloudflare.com/ajax/libs/jquery/3.2.1/jquery.min.js
	touch $@

.deps:
	mkdir -p .deps
	mkdir -p tmp

clean:
	rm -f .deps/node
	rm -f .deps/resources

clean-java:
	mvn clean

.PHONY: rebuild clean clean-java
