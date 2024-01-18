FROM kilda/base-ubuntu

ENV JAVA_VER 8
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64

ENV JAVA_MEM_CONFIG "-XX:+PrintFlagsFinal -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

COPY src/main/resources/application.properties src/main/resources/logback.xml build/libs/openkilda-gui.war /app/

RUN apt-get update
RUN apt-get purge -y openjdk-17-jdk
RUN apt-get autoremove -y
RUN apt-get install -yq --no-install-recommends openjdk-8-jdk
RUN echo "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64" >> ~/.bashrc
RUN . ~/.bashrc
RUN rm -rf /var/lib/apt/lists/*
WORKDIR /app

EXPOSE 1010

CMD ["java", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "openkilda-gui.war"]

