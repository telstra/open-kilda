FROM kilda/base-floodlight

ADD src/messaging /app/messaging
RUN mvn -f /app/messaging clean install

ADD src/floodlight-modules /app/floodlight-modules
RUN (cp /app/floodlight-modules/src/main/resources/floodlightkilda.properties /app && cp /app/floodlight-modules/src/test/resources/logback.xml /app)
RUN mvn -f /app/floodlight-modules/pom.xml clean package
