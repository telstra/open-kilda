import os
from neo4j.v1 import GraphDatabase, basic_auth, TRUST_DEFAULT

neo4jhost = "localhost"
neo4juser = "neo4j"
neo4jpass = "temppass"


driver = GraphDatabase.driver(
    "bolt://{}".format(neo4jhost), 
    auth=basic_auth(neo4juser, neo4jpass), 
    encrypted=True, 
    trust=TRUST_DEFAULT)

def runner(query):
    session = driver.session()
    result = session.run(query)
    session.close()
    return result