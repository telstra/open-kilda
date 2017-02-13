##
## NB: The verify_from() function in project.sh is used to verify the FROM exists
##
FROM kilda/builder-java:0.0.2

ENV PROJECT controller
ENV VERSION 1.0-SNAPSHOT
ENV SRC target/${PROJECT}-${VERSION}-jar-with-dependencies.jar
ENV DST /app/${PROJECT}-${VERSION}.jar
ENV MAIN org.bitbucket.kilda.controller.Main

COPY ${SRC} ${DST}
CMD ["sh", "-c", "java -Dcontroller.profile=dev -cp ${DST} ${MAIN}"]
