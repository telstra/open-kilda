FROM kilda/base-floodlight

ADD src/messaging /app/messaging
RUN mvn -f /app/messaging clean install

ADD src/northbound /app/northbound
RUN (cd /app/northbound && mvn clean package)
