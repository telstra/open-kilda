#FROM kilda/base-ubuntu
FROM kilda/storm:latest

ENV DEBIAN_FRONTEND noninteractive

## MAVEN
RUN apt-get update
RUN apt-get install -y maven
ENV MAVEN_VERSION 3.3.9
ENV MAVEN_HOME /usr/share/maven

RUN pip install kafka
ADD services/wfm/app /app
RUN chmod 777 /app/*

RUN echo "PATH=$PATH:/opt/storm/bin" >> ~/.bashrc

ADD /src/messaging /app/messaging
RUN mvn -f /app/messaging clean install

COPY /services/wfm /app/wfm
WORKDIR /app/wfm
RUN mvn assembly:assembly -DskipTests

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

WORKDIR /app/wfm

RUN TZ=Australia/Melbourne date >> /container_baked_on.txt

# Default command.
CMD ["/bin/bash"]
