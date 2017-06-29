------
CONTROLLER
------

# INTRODUCTION

This directory holds the main source code for the Kilda Controller.

# DEVELOPER ONBOARDING

The code is designed to compile and run in isolation. However, it integrates with
zookeeper, kafka, storm, and the other components in the services directory. To get
the full experience, ensure the following services are operational somewhere, and pass
the configuration in.

## BUILDING, IMAGING, RUNNING

### Building - Options

1. Use project.sh (this will run the code inside a container):
	- ./project.sh build
	- ./project.sh image
	- ./project.sh run
2. Use Maven locally (run outside of a container)
	-  `mvn -q package && mvn -q exec:java`
	-  NB: this is faster.

__NB: There is a dependency on base images (eg: kilda-controller/base/build-base.sh)__

### Logging
- SLF4J and Log4J2 are used. The configuration file is in src/main/resources/log4j2.properties

### Troubleshooting

#### Maven Build: Maven invalid LOC header (bad signature)
One of the dependendent libraries is corrupt. Delete from ~/.m2/repositories and run maven again.

## DEVELOPING

### Key libraries

A good place to start is to look at the pom.xml file to see what libraries are included. 
That'll also give the latest set.  We document a few here.

1. [Guava](http://google.github.io/guava/releases/20.0/api/docs/) - Google Core Libraries for Java
2. [Curator](http://curator.apache.org/getting-started.html) - Makes using Zookeeper easier. 
As Guava is to Java, Curator is to Zookeeper.


## TESTING

At present we are using JUnit 4 (4.12 as of this writing). [JUnit 5](http://junit.org/junit5/) 
will release soon and we may migrate to that.

# ARCHITECTURE

## Platform components

### Storm

#### New to Storm

A good place to start is by looking at the code examples in the [github project](https://github.com/apache/storm/). 
__NB: if you clone this project__, remember to do a `mvn package` at the top level before trying any examples

#### Storm CLI

Ensure you have the CLI installed; there are quite a few useful commands and things to assist development.

- http://storm.apache.org/releases/current/Command-line-client.html
	- http://storm.apache.org/releases/current/Setting-up-development-environment.html
- _Mac / Brew pro tip:_
	- `brew install storm`

## BOOTUP

### PROPERTIES

Kilda uses the [Java Properties](https://docs.oracle.com/javase/tutorial/essential/environment/properties.html) 
patterns to load in default values, and the ability to override these defaults.

To override the defaults, make sure to pass in an argument during execution. An example of
during this with maven is:

```
mvn exec:java -Dexec.args="--config=src/test/resources/test.properties"
```