# JVM monitoring
This document describes how to enable monitoring for OpenKilda to get runtime information.

## Northbound
To enable JMX for northbound, start NB with the following modifications to northbound dockerfile:

```dockerfile
CMD ["java", "-Dcom.sun.management.jmxremote", "-Dcom.sun.management.jmxremote.rmi.port=9445", "-Dcom.sun.management.jmxremote.port=9445", "-Djava.rmi.server.hostname=172.19.114.89", "-Dcom.sun.management.jmxremote.local.only=false", "-Dcom.sun.management.jmxremote.ssl=false", "-Dcom.sun.management.jmxremote.authenticate=false", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseContainerSupport", "-jar", "northbound.jar"]
EXPOSE 9445
```

Then, once NB is started, you can connect JConsole, VisualVM, or other tools to gather runtime information.
In VisualVM, you can simply specify the hostname or IP and port in remote connections. Or use the following connection:
```
service:jmx:rmi:///jndi/rmi://your_host_with_running_open_kilda:9445/jmxrmi
```
It is required to forward ports from the container. See the Port forwarding section below.

## Storm
According to the Storm documentation you can enable JMX by specifying additional settings in storm.yaml:
```yaml
nimbus.childopts: "-Xmx4g -Djava.net.preferIPv4Stack=true -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9446 -Djava.rmi.server.hostname=172.19.114.89 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"
worker.childopts: "-Xmx512m -XX:+UseG1GC -Djava.net.preferIPv4Stack=true -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9447 -Djava.rmi.server.hostname=172.19.114.89 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"
supervisor.childopts: "-Xmx4g -Djava.net.preferIPv4Stack=true -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9448 -Djava.rmi.server.hostname=172.19.114.89 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"

```

IP address 172.19.114.89 in this example is the external IP, which you will use to connect to JMX.

# Port forwarding
It is required to forward the ports used by JMX from the container to the host. This is done in docker-compose.tmpl for 
the appropriate containers in the following manner: 
```yaml
  storm-nimbus:
    container_name: storm-nimbus    
    ports:
      - "9446:9446"
      - "9447:9447"
```
