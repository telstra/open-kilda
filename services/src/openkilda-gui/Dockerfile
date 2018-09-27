FROM kilda/base-ubuntu

#RUN echo "deb http://archive.ubuntu.com/ubuntu precise main universe" > /etc/apt/sources.list
RUN apt-get update \
    && apt-get install -y \
        nginx \
        maven \
        wget \
        net-tools \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN mkdir -p /app/src

RUN echo 'Our first Docker image for Nginx' > /usr/share/nginx/html/index.html
EXPOSE 1010

WORKDIR /app

ADD pom.xml /app
ADD src/ /app/src/
RUN mkdir target
RUN mkdir /app/src/main/resources/lib/font/
RUN mkdir /app/src/main/resources/lib/javascript/

#WGET FONTS
RUN wget https://cdnjs.cloudflare.com/ajax/libs/font-awesome/3.2.1/font/fontawesome-webfont.ttf
RUN wget https://cdnjs.cloudflare.com/ajax/libs/font-awesome/3.2.1/font/fontawesome-webfont.woff

#WGET CSS
RUN wget --output-document=roboto.css https://fonts.googleapis.com/css?family=Roboto:100,100i,300,300i,400,400i,500,500i,700,700i
RUN wget https://cdnjs.cloudflare.com/ajax/libs/font-awesome/3.2.1/css/font-awesome.min.css
RUN wget https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css
RUN wget http://dygraphs.com/2.0.0/dygraph.css
RUN wget https://cdnjs.cloudflare.com/ajax/libs/jquery-datetimepicker/2.5.14/jquery.datetimepicker.css
RUN wget https://cdnjs.cloudflare.com/ajax/libs/jquery-toast-plugin/1.3.2/jquery.toast.min.css
RUN wget https://cdnjs.cloudflare.com/ajax/libs/datatables/1.10.16/css/jquery.dataTables.min.css
RUN wget https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.12.1/jquery-ui.min.css
RUN wget https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.5/css/select2.min.css
RUN wget https://cdnjs.cloudflare.com/ajax/libs/multi-select/0.9.12/css/multi-select.css

#WGET JS
RUN wget https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/jquery-datetimepicker/2.5.14/jquery.datetimepicker.full.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.19.3/moment.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/dygraph/2.0.0/dygraph.js
RUN wget --output-document=jquery-3.2.1.min.js https://cdnjs.cloudflare.com/ajax/libs/jquery/3.2.1/jquery.min.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/jquery-toast-plugin/1.3.2/jquery.toast.min.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/datatables/1.10.16/js/jquery.dataTables.min.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/d3/3.2.2/d3.v3.min.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.12.1/jquery-ui.min.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.5/js/select2.min.js
RUN wget https://cdnjs.cloudflare.com/ajax/libs/multi-select/0.9.12/js/jquery.multi-select.js

#MOVING FONTS
RUN mv fontawesome-webfont.ttf src/main/resources/lib/font/
RUN mv fontawesome-webfont.woff src/main/resources/lib/font/

#MOVING CSS FILES
RUN mv roboto.css src/main/resources/lib/css/
RUN mv dygraph.css src/main/resources/lib/css/
RUN mv bootstrap.min.css src/main/resources/lib/css/
RUN mv jquery.datetimepicker.css src/main/resources/lib/css/
RUN mv font-awesome.min.css src/main/resources/lib/css/
RUN mv jquery.toast.min.css src/main/resources/lib/css/
RUN mv jquery.dataTables.min.css src/main/resources/lib/css/
RUN mv jquery-ui.min.css src/main/resources/lib/css/
RUN mv select2.min.css src/main/resources/lib/css/
RUN mv multi-select.css src/main/resources/lib/css/

#MOVING JAVASCRIPT FILES
RUN mv jquery.datetimepicker.full.js src/main/resources/lib/javascript/
RUN mv bootstrap.min.js src/main/resources/lib/javascript/
RUN mv moment.js src/main/resources/lib/javascript/
RUN mv dygraph.js src/main/resources/lib/javascript/
RUN mv jquery-3.2.1.min.js src/main/resources/lib/javascript/
RUN mv jquery.toast.min.js src/main/resources/lib/javascript/
RUN mv jquery.dataTables.min.js src/main/resources/lib/javascript/
RUN mv d3.v3.min.js src/main/resources/lib/javascript/
RUN mv jquery-ui.min.js src/main/resources/lib/javascript/
RUN mv select2.min.js src/main/resources/lib/javascript/
RUN mv jquery.multi-select.js src/main/resources/lib/javascript/
RUN ls -la 
RUN mvn clean install
RUN cp /app/src/main/resources/application.properties /app/target/ && \
    cp /app/src/main/resources/logback.xml /app/target/

CMD ["java","-jar","target/openkilda-gui.jar"]
