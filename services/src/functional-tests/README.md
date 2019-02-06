# Functional tests
This module holds functional tests designed to be run against staging OR virtual environment.
- [A word about the testing approach](#a-word-about-the-testing-approach)
  - [Single topology for the whole test suite](#single-topology-for-the-whole-test-suite)
  - [Failfast with no cleanup](#failfast-with-no-cleanup)
- [How to run](#how-to-run)
	- [Virtual (local Kilda)](#virtual-local-kilda)
	- [Hardware (Staging)](#hardware-staging)
	- [Artifacts](#artifacts)
- [How to create a test](#how-to-create-a-test)
	- [Best Practices](#best-practices)
- [Other](#other)

# A word about the testing approach
### Single topology for the whole test suite
Since this test suite should have ability to be run both on hardware and virtual topologies,
we consider that we have the same amount of switches/same topology throughout the run even
for virtual runs (obviously we cannot change the topology during a hardware run).  
Topology scheme is defined via a special config file (`topology.yaml`) and remains the same throughout
the test run.  
For this reason we cannot allow tests to assume they will have a 'needed' topology, so each
test should be designed to work on ANY topology (or skip itself if unable to run on given topology).  
Some tests require a 'special' topology state (no alternative paths, isolated switches etc.).
This can be achieved by manipulating existing topology via so-called A-Switch (transit switch not
connected to controller, allows to change ISLs between switches) or controlling ports on
switches (bring ports down to fail certain ISLs).
It is required to bring the topology to the original state afterwards.

### Failfast with no cleanup
We do not do a 'finally' cleanup. Any cleanup steps are usually part of the test itself and they
are **not** run if the test fails somewhere in the middle.  
In case of failure, any subsequent tests are skipped. This allows to diagnose the 'broken' system state when the test failed.  
The drawback is that the engineer will have to manually bring the system/topology back to its original
state after analysing the test failure (usually not an issue for virtual topology since it is
recreated at the start of the test run).  

# How to run
Ensure that `topology.yaml` and
`kilda.properties` files are present in the root of the functional-tests module.  
If run from IDE: mark `groovy` subdirectory in the `functional-tests` module as a test sources root.

### Virtual (local Kilda)
- Spawn your Kilda env locally by running
```
make build-latest
make up-test-mode
```
- Create the `kilda.properties` file in the `functional-tests` directory.
- Copy all properties from `kilda.properties.example` to the `kilda.properties` file. Please note that this is just an
example file and some values may diverge from the actual ones. All properties should represent the actual properties
your Kilda is deployed with. The casual `make up-test-mode` will use properties from `open-kilda/confd/vars/main.yaml`
during deployment.
- Change endpoint properties (url, user and password) if needed. It should point
to your localhost environment. `spring.profiles.active` should be set to `virtual`.
- Check your `topology.yaml`. This is a file which will be used to spawn a virtual
topology used by all the tests.  
The default `topology.yaml` file for the virtual topology is located in the `src/test/resources/` directory.  
In order to use it for test runs copy this file to the root of the functional-tests module or specify the file path via  
`-Dtopology.definition.file=src/test/resources/topology.yaml` in the run command.
- Now you can run tests by executing the following command in the terminal:  
`mvn clean test -Pfunctional`.  
If you want to run a single test, you can use the following command:  
`mvn clean test -Pfunctional -Dtest="<path_to_test_file>#<test_name>"`.
For example:  
`mvn clean test -Pfunctional -Dtest="spec.northbound.flows.FlowsSpec#Able to create a single-switch flow"`
Or just run tests from your IDE as regular JUnit tests.

### Hardware (Staging)
- Check your `kilda.properties`. It should point to your staging environment.  
`spring.profiles.active` should be set to `hardware`.
- Check your `topology.yaml`. It should represent your actual hardware topology.
- Now you can run tests by executing the following command in the terminal:  
`mvn clean test -Pfunctional`.

## Artifacts
* Logs - ```target/logs```
  * `request_logs` - stores all HTTP transactions being made during test run
  * `logs` - casual test log including DEBUG+ messages
* Reports - ```target/spock-reports```

# How to create a test
- Get understanding of what [SpockFramework](http://spockframework.org/) and [Groovy](http://groovy-lang.org/) is.
- Get understanding of what is the package structure out here:
  - `org.openkilda.functionaltests`
    - `extension` - holds various [Spock Extensions](http://spockframework.org/spock/docs/1.1/extensions.html)
    that enrich our framework with additional features. Every extension usually holds a comprehensive description
    of itself in Javadoc format;
    - `helpers` - any helper or tool classes aimed to ease certain testing tasks or hide bulky code;
    - `spec` - all the actual functional tests are stored here. Every spec and feature usually well-documented, so reading through existing tests may become a good start;
    - `unit` - internal unit tests for helper classes.
- Familiarize with existing tests.
- Start creating your test:
  - if it fits under semantics of already existing specification add it there;
  - if it does not fit under semantics of already existing specification create new specification:
    - its name should end with `Spec`, e.g. `SwitchRulesSpec`;
    - it should inherit from `org.openkilda.functionaltests.BaseSpecification`.

## Best Practices
- Don't be too laconic when naming a test. Specify what behavior is being tested instead
of what actions are being took.  
  - Good:
    - "Unable to delete meter with invalid id";
    - "Flow in 'Down' status is rerouted when discovering a new ISL".
  - Bad:
    - "Delete meter with invalid id";
    - "Discover new ISL while flow is down".
- Please provide a comprehensive comment for *every* given-when-then block, so that it forms a valid readable
test case at the end.
- Add a blank line between given-when-then blocks.
- Make your best on cleaning up after your test. Make sure your test does not influence subsequent tests:
  - revert any changes made to the system: topology, database, switches etc.;
  - take care of doing all the proper waits after your test has finished to be sure that all your reverts actually took
  place.
- Make sure your test is stable. Run it multiple times before committing. Cases that can often be unstable
(list is not exhaustive, just for an example purpose):
  - flow creation, deletion and reroute may require additional waits for flow rules to be actually installed
  on switches, since floodlight needs some time to install them, while Northbound responds that everything's done.
  Thus knocking out switch right after flow create/delete command is a dangerous operation;
  - switches knockout/revive operations should have proper waits to ensure that their actual status has changed.
  Same for ISLs.
- keep in mind that the same test will be also run against a staging env (not only local Kilda) with hardware switches, longer delays and different Kilda environment properties.

# Other
### How to create a markdown report with test-cases from specifications
Pass `-Dcom.athaydes.spockframework.report.IReportCreator=org.openkilda.functionaltests.helpers.TestCaseReportCreator`
and find the report under `spock-reports/summary.md` after the test run.
