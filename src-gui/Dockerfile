FROM kilda/base-ubuntu

COPY src/main/resources/application.properties src/main/resources/logback.xml build/libs/openkilda-gui.war /app/
WORKDIR /app

EXPOSE 1010

CMD ["java", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "openkilda-gui.war"]

