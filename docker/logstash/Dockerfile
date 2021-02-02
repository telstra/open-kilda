ARG base_image=logstash:5.6.12
FROM ${base_image}

COPY . /

RUN \
    export DEBIAN_FRONTEND=noninteractive && \
    apt -y update && \
    apt -y install net-tools telnet netcat && \
    rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/*

CMD ["-f", "/etc/logstash/conf.d/logstash.conf"]


EXPOSE 5000 5001 5002 5003 5004 5005
