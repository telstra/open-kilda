FROM kilda/builder-java:0.0.2

RUN apt-get update
RUN apt-get install -y maven
ENV MAVEN_VERSION 3.3.9
ENV MAVEN_HOME /usr/share/maven

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

VOLUME /root/.m2
CMD ["mvn"]
