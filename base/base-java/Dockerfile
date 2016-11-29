FROM kilda/builder-base:0.0.2

RUN apt-get update

# need the following install in order to do add-apt-repository
RUN apt-get install -y software-properties-common python-software-properties

ENV JAVA_VER 8
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# need to silence the "accept license"
RUN echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections
RUN add-apt-repository ppa:webupd8team/java
RUN apt-get update

RUN apt-get install -y oracle-java8-installer
# to guarantee java 8 is the default .. just installing it doesn't make this guarantee
RUN apt-get install -y oracle-java8-set-default

RUN update-java-alternatives -s java-8-oracle
RUN echo "export JAVA_HOME=/usr/lib/jvm/java-8-oracle" >> ~/.bashrc
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
