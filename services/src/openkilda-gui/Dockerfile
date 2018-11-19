FROM kilda/base-ubuntu

#RUN echo "deb http://archive.ubuntu.com/ubuntu precise main universe" > /etc/apt/sources.list
RUN apt-get update
RUN apt-get install -y nginx
RUN echo 'Our first Docker image for Nginx' > /usr/share/nginx/html/index.html
EXPOSE 1010

RUN apt-get install -y maven wget
RUN apt-get install -y net-tools
RUN curl -sL https://deb.nodesource.com/setup_10.x | bash
RUN apt-get install -y nodejs
RUN rm -rf /usr/bin/ng
RUN npm cache clean -f
RUN npm install -g @angular/cli@latest
RUN mkdir -p /app/src

WORKDIR /app
ADD pom.xml /app
ADD src/ /app/src/
ADD ui/  /app/ui/
RUN mkdir target
RUN mkdir -p /app/src/main/resources/lib/css/
RUN mkdir -p /app/src/main/resources/lib/javascript/
WORKDIR /app/ui
RUN npm install
RUN ng build --prod
WORKDIR /app

#WGET CSS
RUN wget --output-document=roboto.css https://fonts.googleapis.com/css?family=Roboto:100,100i,300,300i,400,400i,500,500i,700,700i
RUN wget https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css
#WGET JS
RUN wget https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js
RUN wget --output-document=jquery-3.2.1.min.js https://cdnjs.cloudflare.com/ajax/libs/jquery/3.2.1/jquery.min.js

#MOVING CSS FILES
RUN mv roboto.css src/main/resources/lib/css/
RUN mv bootstrap.min.css src/main/resources/lib/css/

#MOVING JAVASCRIPT FILES
RUN mv bootstrap.min.js src/main/resources/lib/javascript/
RUN mv jquery-3.2.1.min.js src/main/resources/lib/javascript/
RUN ls -la
RUN mvn clean install
RUN cp /app/src/main/resources/application.properties /app/target/ && \
    cp /app/src/main/resources/logback.xml /app/target/

WORKDIR /app/target

CMD ["java", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "openkilda-gui.jar"]

