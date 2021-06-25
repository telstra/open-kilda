# Functional tests
This module holds functional tests designed to be run against staging(hardware) OR virtual environment.
<!-- TOC -->

- [Functional tests](#functional-tests)
- [How to run](#how-to-run)
	- [Virtual (local Kilda)](#virtual-local-kilda)
	- [Hardware (remote Kilda, Staging)](#hardware-remote-kilda-staging)
	- [Partial Kilda deployment](#partial-kilda-deployment)
	- [General info](#general-info)
	- [Test suites](#test-suites)
	- [Parallel Tests](#parallel-tests)
	- [Artifacts](#artifacts)
- [Practices](#practices)
	- [Test execution may be aborted due to 'uncleanupable contamination'](#test-execution-may-be-aborted-due-to-uncleanupable-contamination)
	- [How to create a test](#how-to-create-a-test)
	- [Other recommendations](#other-recommendations)
- [Other](#other)
	- [How to create a markdown report with test-cases from specifications](#how-to-create-a-markdown-report-with-test-cases-from-specifications)

<!-- /TOC -->

# How to run
### Virtual (local Kilda)
- Build Kilda `make build-stable`
- Deploy Kilda locally `make up-test-mode`
- Run all tests `make func-tests` or just create a test topology to play with `make test-topology`
> Note that the above command will overwrite any existing kilda.properties and topology.yaml
files with default ones

### Hardware (remote Kilda, Staging)
- Ensure that `topology.yaml` and
`kilda.properties` files are present in the root of the functional-tests module.
- Check your `kilda.properties`. It should point to your environment.  
`spring.profiles.active` should be set to `hardware`.  
Note that other properties should
correspond to actual Kilda properties that were used for deployment of the target env.
- Check your `topology.yaml`. It should represent your actual expected hardware topology. You can automatically generate
`topology.yaml` based on currently discovered topology, but be aware that this will prevent you from catching
some switch/isl discovery-related issues: `make test-topology PARAMS="--tests GenerateTopologyConfig"` && `cp functional-tests/build/topology.yaml functional-tests/`.
 This will also not generate information about 'a-switch' and traffgens.
- Now you can run tests by executing the following command in the terminal:  
`make func-tests`.

### Partial Kilda deployment
If you have limited resources available, it is possible to not deploy some optional Kilda components, for example
deploy 1 Floodlight instead of 3 (default). Though this will disable some of the tests.
In order to achieve this look into `confd/vars/docker-compose.yaml`. If you deploy only 1 Floodlight make sure to:
- adjust `kilda.properties` values for `floodlight.controllers` and `floodlight.regions`
- in `topology.yaml` check that no switches try to connect to 'region: 2'  

Make relative changes for any other properties related to not deployed components.

### General info
- Framework requires `topology.yaml` and `kilda.properties` files. Custom locations can be specified via
`-Dtopology.definition.file=custom/topology.yaml` and `-Dkilda.config.file=custom/kilda.properties`
- Tests can be run via gradle (given we in the `src-java` dir)
`./gradlew :functional-tests:functionalTest`.
If you want to run a single test, you can use the following command:  
`./gradlew :functional-tests:functionalTest --info --tests <test_file>."<test_name>"`.
For example:  
`./gradlew :functional-tests:functionalTest --info --tests LinkSpec."Unable to delete an active link"`
- Tests can be run as regular JUnit tests from your IDE
- To exclude certain tests pass 'excludeTests' param:  
`./gradlew :functional-tests:functionalTest -DexcludeTests='Server42RttSpec'`

## Test suites
We leverage test suites by first tagging tests and then supplying a required `tag experession` when starting a test run.
More info on how to form a tag expression can be found in javadoc here `org.openkilda.functionaltests.extension.tags.TagExtension`.  
Common usages:  
`./gradlew :functional-tests:functionalTest -Dtags='smoke'` #shorten suite of most valuable test cases
`./gradlew :functional-tests:functionalTest -Dtags='topology_dependent or hardware'`
`./gradlew :functional-tests:functionalTest -Dtags='smoke_switches'` #focus on switch-related tests (e.g. smoke test integration with new switch firmware)
`./gradlew :functional-tests:functionalTest -Dtags='not low_priority'` #exclude regression low-value tests. This suite is used to run
func tests for each PR on github

## Parallel Tests
Tests on virtual lab are run in parallel. Each 'thread' is executed in it's own isolated topology (island).
If number of parallel threads is more than the number of available islands, such threads will be blocked
until free island is available (returned to the pool). Amount of created islands is controlled by
`parallel.topologies` property, which can be set either as system property during build
`-Dparallel.topologies=3` or in `kilda.properties` file. Amount of parallel threads is defined in
`SpockConfig.groovy`([spock docs](https://spockframework.org/spock/docs/2.0/parallel_execution.html)).  
'Hardware' profile is always run on a single island, so technically there is no parallelism available

## Artifacts
* Logs - ```build/logs```
  * `logs` - casual test log including DEBUG+ messages
* Reports - ```build/reports```

# Practices
### Test execution may be aborted due to 'uncleanupable contamination'
Sometimes it is very difficult to
compile a comprehensive cleanup code for the test that will cover any failure scenario (especially for long, complex
test cases). Such tests
are __not__ marked as `@Tidy`, failure of such tests will abort further execution of any subsequent tests
 because at this point system is considered as contaminated.
On the other hand, if the test is supplied with a bullet-proof cleanup code, then it should have a
`@Tidy` annotation above it, meaning that in case of failure no 'failfast' policy should be applied, allowing
execution of subsequent tests.

### How to create a test
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
    - it should inherit from `org.openkilda.functionaltests.HealthCheckSpecification`.

### Other recommendations
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
- Make sure your test is stable. Run it multiple times before committing.
- Keep in mind that the same test will be also run against a staging env (not only local Kilda) with hardware switches, longer delays and different Kilda environment properties.

# Other
### How to create a markdown report with test-cases from specifications
Pass `-Dcom.athaydes.spockframework.report.IReportCreator=org.openkilda.functionaltests.helpers.TestCaseReportCreator`
and find the report under `spock-reports/summary.md` after the test run. We use this report to update our wiki page
https://github.com/telstra/open-kilda/wiki/Testing
