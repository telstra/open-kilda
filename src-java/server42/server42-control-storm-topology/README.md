# Useful commands

- Messages from flowhs to server42-storm `docker exec -ti kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server=kafka.kilda:9092 --topic=kilda.flowhs.server42-storm-notify.priv`
- Messages from server42-storm to server42-control `docker exec -ti kafka /opt/kafka/bin/kafka-console-consumer.sh  --property=print.key=true --bootstrap-server=kafka.pendev:9092 --topic=kilda.server42-storm.commands.priv`

- Compile and push topology to storm
    ```
    make compile
    storm kill server42-control
    docker-compose run wfm ./deploy_single_topology.sh server42-control topology.properties
    ```
  