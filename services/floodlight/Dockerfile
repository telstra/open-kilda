FROM kilda/base-floodlight

ADD src/pom.xml /app/pom.xml
ADD src/checkstyle.xml /app/checkstyle.xml
ADD src/messaging /app/messaging
RUN mvn -f /app/pom.xml --non-recursive clean install
RUN mvn -f /app/messaging/pom.xml clean install

ADD src/floodlight-modules /app/floodlight-modules
RUN (cp /app/floodlight-modules/src/main/resources/floodlightkilda.properties /app && cp /app/floodlight-modules/src/test/resources/logback.xml /app)
RUN mvn -f /app/floodlight-modules/pom.xml clean package
