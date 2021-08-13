ARG base_image=logstash:7.14.0
FROM ${base_image}

COPY . /

USER root
RUN \
    yum -y install epel-release && \
    yum -y \
        install \
            supervisor \
            net-tools \
            telnet \
            netcat \
            jemalloc && \
    yum clean all && \
    rm -rf /var/cache/yum && \
    rm -rf /usr/share/logstash/config/logstash-sample.conf

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisord.conf", "--nodaemon"]


EXPOSE 5000 5001 5002 5003 5004 5005
