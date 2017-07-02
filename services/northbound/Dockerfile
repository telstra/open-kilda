FROM kilda/base-floodlight

ADD src/pom.xml /app/pom.xml
ADD src/checkstyle.xml /app/checkstyle.xml
ADD src/messaging /app/messaging
RUN mvn -f /app/pom.xml --non-recursive clean install
RUN mvn -f /app/messaging/pom.xml clean install

ADD src/northbound /app/northbound
RUN mvn -f /app/northbound/pom.xml clean package
