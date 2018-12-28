# Acceptance Test Driven Development (ATDD)

## INTRODUCTION

This directory holds the outcome-based acceptance tests for Kilda. There is a lot of
written material regarding various "driven-development" techniques (eg. ATDD,
UCDD, BDD, ect.). For this project, ATDD will be used to express high level outcomes.
The outcomes will be based on tests against the running system, but could be based on
static code analysis as well. Consequently, there'll have to be some constructs for
setting up and tearing down the controller and/or testing apparatus.

## How to run

Steps:
1. Build Kilda controller. See [*"How to Build Kilda Controller"*](/README.md#how-to-build-kilda-controller) section
2. Run Kilda controller in *"test mode"*. ```make up-test-mode```
3. Update your /etc/hosts file. Replace ```127.0.0.1 localhost``` to
   ```127.0.0.1    localhost kafka.pendev```
4. Run ATDD using ```make atdd``` command (from the base dir).

## CONTINUOUS INTEGRATION

Some observations:

* This source tree does have code, so it'll need to be compiled.
* It'll have a pipeline file (ie Jenkinsfile) so that acceptance tests can run as
  part of the build system.

## CODE STRUCTURE

* CUCUMBER OPTIONS - The class org.openkilda.atdd._RunCucumberTest is the
primary configuration class.
  * This File controls which acceptance tests to run
  * `feature =` - controls which directories to include
  * `tags =` - controls which scenarios to include

## EXAMPLES

### Calling Maven with specific IP


This is useful if you want to run the acceptance test against a separate server
(e.g. not local):

    mvn -DargLine="-Dkilda.host=1.2.3.4" test

## REFERENCE

In this section I'll cover some simple acceptance tests. First in abbreviated form, then
I'll add a pointer to the gherkin file that expresses the acceptance criteria in a form
that [cucumber](http://cucumber.io) can work with.

# SMOKE Tests

## Introduction

The code in this directory facilitates the "lights on" verification process for Kilda.

We've allowed for validation steps in both java and python
