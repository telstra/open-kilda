# Acceptance Test Driven Development (ATDD)

## INTRODUCTION

This directory holds the outcome-based acceptance tests for Kilda. There is a lot of
written material regarding various "driven-development" techniques (eg. ATDD,
UCDD, BDD, ect.). For this project, ATDD will be used to express high level outcomes.
The outcomes could be based on static code analysis, or go as far as to test the
running system. Consquently, there'll have to be some constructs for setting up and
tearing down the controller and/or testing apparatus.

## CONTINUOUS INTEGRATION

Some observations:

* This source tree does have code, so it'll need to be compiled.
* It'll have a pipeline file (ie Jenkinsfile) so that

## EXAMPLES

In this section I'll cover some simple acceptance tests. First in abbreviated form, then
I'll add a pointer to the gherkin file that expresses the acceptance criteria in a form
that [cucumber](http://cucumber.io) can work with.
