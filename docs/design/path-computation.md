# Path Computation Engine (PCE)

## Introduction

Most of the logic for path computation resides in the services/src/pce project.

The initial implementation of the path algorithm relies on the mechanisms native
to Neo4J - i.e. we have an abstraction for a path computer, and the initial / 
default implementation is Neo4J. The Neo4J driver is just a thin facade.

## Testing

### Unit Tests

The pce project has multiple unit tests that excercise most / all of the core logic.

## Development

Here are the basic steps to getting up and running:

1. Ensure you can run kilda-pce maven project, run the tests, and they all should pass.
