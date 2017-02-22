FROM kilda/base-ubuntu

ENV DEBIAN_FRONTEND noninteractive

RUN pip install kafka
ADD services/wfm/app /app
RUN chmod 777 /app/*

WORKDIR /tmp

# Default command.
CMD ["bash"]
