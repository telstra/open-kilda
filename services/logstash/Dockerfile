ARG base_image=logstash:5.6.12
FROM ${base_image}

COPY . /

CMD ["-f", "/etc/logstash/conf.d/logstash.conf"]

EXPOSE 5000 5001 5002 5003 5004 5005
