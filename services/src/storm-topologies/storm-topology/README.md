------
STORM TOPOLOGY
------

# INTRODUCTION

This project contains all the library for all Kilda Storm Topologies.

# DEVELOPER ONBOARDING

TODO

## BUILDING, IMAGING, RUNNING

### Building - Options

### Logging
- SLF4J and Logback are used. The configuration file is in src/main/resources/logback.xml

### Troubleshooting

#### Maven Build: Maven invalid LOC header (bad signature)
One of the dependent libraries is corrupt. Delete from ~/.m2/repositories and run maven again.

## DEVELOPING

### Key libraries

A good place to start is to look at the pom.xml file to see what libraries are included. 
That'll also give the latest set.  We document a few here.

1. [Storm](http://storm.apache.org/releases/1.0.2/index.html) - Apache Storm.
1. [Guice](http://curator.apache.org/getting-started.html) - Google Guice.


## TESTING

TODO

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

# TODO

1. Error handling for bad JSON or JSON that doesn't map to my object.
1. Move YAML parsing to library so I don't have to copy it here.
1. Remove bolt package once these cases become available in a new storm-kafka-client release.
