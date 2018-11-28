# Floodlight - Modules

This project holds the Floodlight application.
It is used along with Floodlight SDN controller to manage OpenFlow compatible switches.

# Developers

## Meters for default (system) rules.
We added meters for our system rules to prevent switch and controller being overloaded by package floods (such flood can be a consequence of traffic loop on client side, i.e. beyond our control). Such meters are being used for dropping packets to prevent controller being killed by receiving too many packets. For every type of these rules there is a separate property to define specific rate of the meter.

Since not all switches support OFPMF_PKTPS flag (using this flag we are able to define rate value in packets per second), we install meters with OFPMF_KBPS flag and rate value is calculated by the following way:
```
rate = (ratePkts * disco_packet_size) / 1024L
where:
 ratePkts - is the rate in packets for specific type of rule
 disco_packet_size - the size of discovery packet, is being defined in properties file with the key 'org.openkilda.floodlight.switchmanager.SwitchManager.disco-packet-size'.
```

## Debugging Tips

## Testing tips

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
