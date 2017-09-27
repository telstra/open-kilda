import os
from py2neo import Graph


def create_p2n_driver():
    graph = Graph("http://{}:{}@{}:7474/db/data/".format(
        os.environ['neo4juser'],
        os.environ['neo4jpass'],
        os.environ['neo4jhost']))
    return graph
