FROM kilda/builder-java:0.0.2

COPY java-hello-world/target/java-hello-world-1.0-SNAPSHOT.jar /app/java-hello-world-1.0-SNAPSHOT.jar
CMD ["java","-cp","/app/java-hello-world-1.0-SNAPSHOT.jar","org.bitbucket.kilda.App"]
