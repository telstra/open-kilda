neo4j console &
pip install cycli &&
# Wait for neo4j to be and execute queries.
./app/wait-for-it.sh -t 120 -h localhost -p 7473 -- cycli -f /app/neo4j-queries.cql
sleep infinity