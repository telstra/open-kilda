smoke:
	echo Running Smoke Tests
	cd ../atdd && mvn install
	mvn verify -f java/pom.xml
	pytest python
