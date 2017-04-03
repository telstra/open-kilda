import os
import time
from neo4j.v1 import GraphDatabase, basic_auth, TRUST_DEFAULT
from py2neo import Graph

def runner(query):
    session = driver.session()
    result = session.run(query)
    session.close()
    return result

def create_driver():
    neo4jhost = os.environ['neo4jhost']
    neo4juser = os.environ['neo4juser']
    neo4jpass = os.environ['neo4jpass']    
    driver = GraphDatabase.driver("bolt://{}".format(neo4jhost), auth=basic_auth(neo4juser, neo4jpass), encrypted=True, trust=TRUST_DEFAULT)
    return driver

def create_p2n_driver():
    graph = Graph("http://{}:{}@{}:7474/db/data/".format(os.environ['neo4juser'], os.environ['neo4jpass'], os.environ['neo4jhost']))
    return graph

def test_neo4j_connection():
    Neo4jConnectionRetries = 10
    while Neo4jConnectionRetries > 0:
        try:
            Neo4jConnectionRetries -= 1
            driver = create_driver()
            print "Connected to Neo4j"
            break
        except:
            print "Waiting for Neo4j to become available"
            time.sleep(1)
    
    return True

driver = create_driver()
