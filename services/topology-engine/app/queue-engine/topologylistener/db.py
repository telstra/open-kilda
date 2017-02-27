import os
import time
from neo4j.v1 import GraphDatabase, basic_auth, TRUST_DEFAULT

def runner(query):
    driver = create_driver()
    session = driver.session()
    result = session.run(query)
    session.close()
    return result

def create_driver():
    neo4jhost = "neo4j"
    neo4juser = "neo4j"
    neo4jpass = "temppass"    
    driver = GraphDatabase.driver("bolt://{}".format(neo4jhost), auth=basic_auth(neo4juser, neo4jpass), encrypted=True, trust=TRUST_DEFAULT)
    return driver

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

    while driver:
        try:
            runner("MATCH (n) RETURN n")
            print "DB layer connected"
            break
        except Exception as e:
            time.sleep(1)
            print e
            print "Waiting for DB layer"
    
    return True



