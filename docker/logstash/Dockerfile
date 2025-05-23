ARG base_image=logstash:5.6.12
FROM ${base_image}

COPY . /

RUN \
    export DEBIAN_FRONTEND=noninteractive && \
    echo "deb http://archive.debian.org/debian stretch main" > /etc/apt/sources.list && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 605C66F00D6C9793 0E98404D386FA1D9 648ACFD622F3D138 && \
    apt -y update && \
    apt -y install net-tools telnet netcat && \
    rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/*

CMD ["-f", "/etc/logstash/conf.d/logstash.conf"]


EXPOSE 5000 5001 5002 5003 5004 5005
