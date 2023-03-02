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

In order to run Floodlight in the debug mode, we have to add the following arguments when run the app:  

`"-agentlib:jdwp=transport=dt_socket, address=50506, suspend=n, server=y”`.  

Particularly in Floodlight we can not do this straight in the Dockerfile since it leads us to the entrypoint.sh script.
Thus, we have to add the arguments above to the following file:  `projectroot/docker/floodlight-modules/entrypoint.sh`.  
To do so, in the entrypoint.sh file find a line with the `exec java`  under the following condition block: ```"$1" = 'floodlight’```  ,
add the arguments for debug, and leave the existing arguments as they are.  
As a result, the `exec java` line should look something like this:
```
exec java "-agentlib:jdwp=transport=dt_socket,address=50506,suspend=n,server=y" -XX:+PrintFlagsFinal……… the rest arguments….;
```
Then we have to expose a debugging port in the docker-compose.tmpl file.

Go to the docker-compose.tmpl file which is located on the following path:  
`projectroot/confd/templates/docker-compose/docker-compose.tmpl`.  
Find `floodlight_1` and add a desirable port mapping:  
`ex: 50506:323232 -(inside:outside)`.  
In this example, port `50506` is the port number that we previously have pointed in the entrypoint.sh file, and
`323232` - is the port number that we will use in order to debug.  

Next, we have to stop the container, rebuild the image, and run a new container.  

To make sure that we have exposed our ports, run the following command, it should show us our mapping ports:  
`docker ps | grep floodlight`.

Connect to the container using the following command:  
`docker exec -it floodlight_1(2) bash`,  
Inside the container locate the entrypoint.sh file, it should contain this argument:  
`"-agentlib:jdwp=transport=dt_socket,address=50506,suspend=n,server=y”`.