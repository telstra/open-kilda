# Floodlight - OpenKilda Modules

This sub-project holds the OpenKilda modules for the Floodlight application.
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

#### How to debug Floodlight component

In order to run floodLight in the debug mode we have to add this line of arguments
```
"-agentlib:jdwp=transport=dt_socket,address=50506,suspend=n,server=y”
``` 
when running the app.
Particularly in floodLight we can not do this straight to the Dockerfile since it leads us to entrypoint.sh script.
We have to add these arguments to the
```
projectroot/docker/floodlight-modules/entrypoint.sh
```
file;


in this file find `exec java` line under the ```"$1" = 'floodlight’```  condition.
In the end it should look something like this:
```
exec java "-agentlib:jdwp=transport=dt_socket,address=50506,suspend=n,server=y" -XX:+PrintFlagsFinal……… the rest arguments….;
```
then we have to expose debugging port in docker-compose.tmpl file.
go to ```projectroot/confd/templates/docker-compose/docker-compose.tmpl```
find floodlight_1 and add desirable port mapping:
```ex: 50506:323232     - (inside:outside).```
In this example, 50506 is the port number that we have pointed for entrypoint.sh file previously,
323232 - port number that we will use to attach to debug.
As a last step we have to stop our container, rebuild image, run new container.
let ensure that we expose our ports: run this command:
```
docker ps | grep floodlight
```
should show us our mapping ports.
Inside the container run
```
docker exec -it floodlight_1(2) bash
```

the entrypoint.sh should contain this argument
```
"-agentlib:jdwp=transport=dt_socket,address=50506,suspend=n,server=y” argument.
```