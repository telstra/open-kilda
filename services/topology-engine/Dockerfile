FROM kilda/base-ubuntu
RUN apt-get install -y python-pip
RUN pip install neo4j-driver
RUN pip install kafka
RUN pip install flask
RUN pip install jsonschema
ADD services/topology-engine/app /app

