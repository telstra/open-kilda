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

### Configuration
To pass configuration options into northbound you have several ways. 

You can put file application.properties into "current" folder(from where you starts northbound.jar) and override all
required options/properties. application.properties uses generic java property file format.

Example application.properties file that define kafka connection
```properties
kafka.hosts=lab.local:9092
kafka.groupid=northbound
kafka.topic=kilda-devel
```

Location and the name of application.properties file can be passed via CLI
```bash
java -jar northbound.jar --spring.config.name=northbound
```
to use northbound.properties

```bash
java -jar northbound.jar --spring.config.location=file:/srv/app/config/ --spring.config.name=northbound
```
to use northbound.properties placed into /srv/app/config folder.

Instead of CLI you can use SPRING_CONFIG_NAME and SPRING_CONFIG_LOCATION environment variables for same purposes. 

You can also override properties by passing them via CLI. For example to pass kafka host

```bash
java -jar northbound.jar --kafka.hosts=lab.local:9092
```

You can find list of existing properties into "src/main/resources/northbound.propertiesnorthbound.properties"

[Here you can find more details about overriding properties](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config-application-property-files])

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
