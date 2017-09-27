#FROM kilda/base-ubuntu
FROM kilda/storm:latest

ENV DEBIAN_FRONTEND noninteractive

## MAVEN
RUN apt-get update
RUN apt-get install -y maven
ENV MAVEN_VERSION 3.3.9
ENV MAVEN_HOME /usr/share/maven

RUN pip install kafka
ADD wfm/app /app
RUN chmod 777 /app/*

RUN echo "PATH=$PATH:/opt/storm/bin" >> ~/.bashrc

ADD src/pom.xml /app/pom.xml
ADD src/checkstyle.xml /app/checkstyle.xml
ADD src/messaging /app/messaging
ADD src/pce /app/pce
RUN mvn -f /app/pom.xml --non-recursive clean install
RUN mvn -f /app/messaging/pom.xml clean install
RUN mvn -f /app/pce/pom.xml clean install

ADD wfm /app/wfm
RUN mvn -f /app/wfm assembly:assembly -DskipTests

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

WORKDIR /app/wfm

RUN TZ=Australia/Melbourne date >> /container_baked_on.txt

# Default command.
CMD ["/bin/bash"]
