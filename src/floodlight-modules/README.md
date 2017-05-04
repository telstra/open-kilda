# Floodlight - Modules

This project holds the Floodlight application.
It is used along with Floodlight SDN controller to manage OpenFlow compatible switches.

# Developers

## Debugging Tips

## Testing tips

### Unit tests

Running JUnit target with coverage from the IDE builds coverage and should integrate data with the sources.

The command __mvn clean package__ builds unit tests and code coverage reports for using by external apps.

Generated reports are stored in:
* ```reports/jacoco``` - jacoco reports
* ```reports/junit``` - junit reports
* ```target/coverage-reports``` - coverage data files

Jacoco generates a site-package in ```reports/jacoco``` directory.
It could be opened in any browser (```reports/jacoco/index.html```).

Junit reports could be used by external services (CI/CD for example) to represent test results.

Generated coverage data from the ```target/coverage-reports``` directory could also be opened in IDE to show the coverage.
