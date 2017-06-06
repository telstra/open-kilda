FROM kilda/base-floodlight

ADD src/messaging /app/messaging
RUN mvn -f /app/messaging clean install

ADD src/topology /app/topology
RUN mvn -f /app/topology clean package
