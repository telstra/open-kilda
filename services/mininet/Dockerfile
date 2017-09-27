FROM kilda/base-ubuntu

ENV DEBIAN_FRONTEND noninteractive
ENV MININET_REPO https://github.com/mininet/mininet.git
ENV MININET_INSTALLER mininet/util/install.sh
ENV INSTALLER_SWITCHES -fbinptvwyx

RUN add-apt-repository 'deb http://security.ubuntu.com/ubuntu xenial-security main'
RUN apt-get update
RUN apt-get -qy install mininet net-tools tcpdump inetutils-ping
RUN apt-get -qy install autoconf automake ca-certificates git libtool
RUN apt-get -qy install openssh-client patch vim
RUN apt-get -qy install python-bottle python-jsonschema python-scapy
RUN ln -s /usr/sbin/tcpdump /usr/bin/tcpdump

ADD services/mininet/app /app
RUN chmod 777 /app/*

WORKDIR /tmp

VOLUME ["/data"]

WORKDIR /data

# Default command.
CMD ["bash"]
