# Path Computation Engine (PCE)

## Introduction

Most of the logic for path computation resides in the services/src/pce project.

The initial implementation of the path algorithm relies on the mechanisms native
to Neo4J - i.e. we have an abstraction for a path computer, and the initial / 
default implementation is Neo4J. The Neo4J driver is just a thin facade.

## Testing


### Acceptance Tests:

There are a couple of Acceptance Tests that exercise this code base:

* FlowPathTest.java
* PathComputationTest.java

These are in the atdd project in services/src/atdd .. src/test.

### Unit Tests

The pce project has multiple unit tests that excercise most / all of the core logic.

## Development

Here are the basic steps to getting up and running:

1. Ensure you can run this maven project, run the tests, and they all should pass.
2. Ensure you can run the acceptance tests:
    1. make atdd tags="@PCE,@FPATH"

## Notea

In addition to this sparse guide, there are more notes in PathComputationTests.java regarding how the acceptance is executed.  That should give you a good guide to how the code comes together.

 