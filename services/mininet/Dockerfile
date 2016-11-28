FROM kilda/base-ubuntu

ENV DEBIAN_FRONTEND noninteractive
ENV MININET_REPO https://github.com/mininet/mininet.git
ENV MININET_INSTALLER mininet/util/install.sh
ENV INSTALLER_SWITCHES -fbinptvwyx

ADD services/mininet/ansible /ansible
RUN ansible-playbook -s /ansible/deploy.yml

WORKDIR /tmp

VOLUME ["/data"]

WORKDIR /data

# Default command.
CMD ["bash"]