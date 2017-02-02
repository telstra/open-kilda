FROM kilda/base-ubuntu
RUN apt-get install -y python-pip
RUN pip install neo4j-driver
RUN pip install kafka
ADD services/topology-engine/app /app

