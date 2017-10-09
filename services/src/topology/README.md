# Northbound REST API service

This project holds the Northbound API service for Kilda controller application.

## Authentication

Northbound API services uses basic auth for authentication. Username and password are obtained via environment variables.

Default environment variables names are:
* __KILDA_USERNAME__
* __KILDA_PASSWORD__

These values could be changed in ```src/main/resources/northbound.properties``` file:

* _security.username.env=KILDA_USERNAME_
* _security.password.env=KILDA_PASSWORD_

Default username and password are:
* __kilda__
* __kilda__

These values could also be changed in ```src/main/resources/northbound.properties``` file:

* _security.username.default=kilda_
* _security.password.default=kilda_

## Documentation

### REST API

REST API documentation could be generated via __mvn clean package__ command.

* generated site package is stored in ```target/docs/apidocs/``` folder
* archived version is ```target/docs.zip```

### Unit tests

Running JUnit target with coverage from the IDE builds coverage and should integrate data with the sources.

The command __mvn clean package__ builds unit tests and code coverage reports for using by external apps.

Generated reports are stored in:
* ```target/jacoco``` - jacoco reports
* ```target/junit``` - junit reports
* ```target/coverage-reports``` - coverage data files

Jacoco generates a site-package in ```target/jacoco``` directory.
It could be opened in any browser (```target/jacoco/index.html```).

Junit reports could be used by external services (CI/CD for example) to represent test results.

Generated coverage data from the ```target/coverage-reports``` directory could also be opened in IDE to show the coverage.


### Javadoc

Javadoc mvn documentation could be generated via __mvn clean javadoc:javadoc__ command.
Generated site-package is located under ```target/site/apidocs/``` directory.
