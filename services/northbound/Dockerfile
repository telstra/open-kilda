FROM kilda/base-floodlight

ADD src/messaging /app/messaging
RUN mvn -f /app/messaging clean install

ADD src/northbound /app/northbound
RUN mvn -f /app/northbound/pom.xml clean package
