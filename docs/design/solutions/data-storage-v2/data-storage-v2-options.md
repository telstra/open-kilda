# Kilda Data storage V2

## Overview
This doc describes the proposed solutions for migration OpenKilda from Neo4j to another data storage.

## Goals
The goal is to implement support of a data storage solution that satisfies the system requirements.

Currently, Kilda uses [Neo4j](https://neo4j.com/) as a persistent storage for its [data model](../domain-model/domain-model.md),
and [Neo4j-OGM](https://github.com/neo4j/neo4j-ogm) as the mapping library in the persistence layer.

Since the persistence layer was introduced, we faced multiple related issues: Neo4j-OGM performance on concurrent updates (https://github.com/telstra/open-kilda/pull/2747),
missed changes in persistent objects (https://github.com/telstra/open-kilda/issues/3064), 
improper handling of data types and converters (https://github.com/telstra/open-kilda/issues/3166).

Considering the above mentioned, it was decided that Neo4j version 3.x along with Neo4j-OGM  
doesn't correspond to the current 
and emerging system requirements, although perform the basic functions. 

## Requirements for Data storage
Kilda architecture among other requirements to the data storage, expects flexibility in choice of the storage options:
from single instance deployed on a developer's workstation, up to scalable and distributed, which provides high-availability 
(https://github.com/telstra/open-kilda/issues/940). 

**TBD: durability, backups, replication, HA** 

## Data stored by Kilda
Kilda operates with and stores the following data:
- Network topology: switches and links (ISLs)
- Flows (each one contains multiple Flow paths and correspoing Path segments)
- History records (flow events, port status changes, etc) 
- Resources pools (flow encapsulation, meter, etc) 

Each data type has own use cases and handling requirements:
- Network topology, flow data:
	- Transactional CRUD on entities that represent the network.
	- Find a path between nodes (via PCE).
	- Find related entities (flows by an ISL or by a switch)
	- Visualize as a graph model on GUI / web application.
	- Analyze connectivity in the network, and identify required backup links
	- Execute custom traversals over the network structure for ad-hoc investigations.
	
- History records:
	- Record an event / event details
	- Find by keys, time periods, etc.
	- Free-text search in event details (task_id, switch, etc.)
	
- Resources pools:
    - Reliable allocation under high contention of requests.  

## Possible solutions

### Graph database with support of a standard API / query language 
Store the whole data model in the same graph database, use a one of existing standard API / query language.
Implement custom data mapping for Kilda.

- [OpenCypher](http://www.opencypher.org/) query language. Databases: Neo4j or [AgensGraph](http://www.agensgraph.org/). 
- [Gremlin](https://tinkerpop.apache.org/gremlin.html) traversal language. 
Databases: [JanusGraph](https://janusgraph.org/), [OrientDB](https://orientdb.com/), [Amazon Neptune](https://aws.amazon.com/neptune/), [Azure Cosmos DB](https://azure.microsoft.com/services/cosmos-db/).  

Pros:
- Data in a storage can be easily mapped to the business entities (as has the similar graph model).
- We can benefit from build-in functions / libraries. E.g. Neo4j has [the graph algorithm library](https://neo4j.com/docs/graph-algorithms/current/projected-graph-model/) which 
demonstrated very good results comparing to the current PCE implementation (https://github.com/telstra/open-kilda/pull/2770)    

Cons:
- Custom data mapping must be coded, tested and supported.
- Limited or paid-only administration and monitoring tools.
- Limited options for schema versioning: [Liquigraph](https://www.liquigraph.org/) supports Neo4j only.

### Graph database with OGM 
Store the whole data model in the same graph database, use a one of existing OGM for data mapping in the persistence layer.

- [Neo4j-OGM](https://neo4j.com/docs/ogm-manual/current/)
- [Tinkerpop](https://tinkerpop.apache.org/) / [Ferma](http://syncleus.com/Ferma/)

Pros:
- Data in a storage can be easily mapped to the business entities (as has the similar graph model).

Cons:
- Although OGM frameworks exist, but either non-portable (Neo4j-OGM is for Neo4j only) 
or require a specific implementation (Ferma works only with a complete implementation of [Tinkerpop Graph API](http://tinkerpop.apache.org/docs/3.4.6/reference/#graph)
 which is OrientDB) or https://janusgraph.org/ as for now)
- Limited or paid-only administration and monitoring tools.
- Limited options for schema versioning: Liquigraph supports Neo4j only.

**Note**: Hibernate OGM differs form other OGM solutions as relies on JPQL as a query language, 
which is closer by nature to SQL. So even a simple graph traversal becomes a bunch of JOINs. 

### Combination of graph database and other storages
Store the network topology and flows in a graph database, while history records directed to relational / NoSQL storage.

- OrientDB with OGM (Ferma) + RDBMS (Postgre, MySQL, RDS) with ORM (Hibernate)
- OrientDB as multi-model database with OGM (Ferma) and ORM (Hibernate)
- Neo4j with custom mapping + RDBMS (Postgre, MySQL, RDS) with ORM (Hibernate)

Pros:
- Network topology data in a graph storage can be easily mapped to the business entities (as has the similar graph model).
- NoSQL storages may propose more flexible indexing for History records.

Cons:
- Multiple storages complicate their administration and support.

### Relational database 
Store the whole data model in the same relational database.

Pros:
- Extensive administration and monitoring tools available.
- ORM frameworks (e.g. Hibernate) can be used in the persistence layer. This should simplify the code.
- Powerful schema versioning tools: [Flyway](https://flywaydb.org/), Liquibase.

Cons:
- Data in a storage kept in a way that differs from how the business sees it. This may complicate future integrations
with external systems, data migrations, data analysis.
- Graph traversal operations can be implemented as in-memory PCE only. 

## Implementation details

### Neo4j
Neo4j invest into development of own language (Cypher) and push to make it as a standard for graph databases (http://www.opencypher.org/, https://www.gqlstandards.org/, 
https://gql.today/wp-content/uploads/2018/05/a-proposal-to-the-database-industry-not-three-but-one-gql.pdf, https://www.linkedin.com/pulse/sql-now-gql-alastair-green/).

In the case of Tinkerpop API, 3 options are possible for Neo4j:
- Deploy a plugin on Neo4j server and access it remotely (https://github.com/neo4j-contrib/gremlin-plugin - archived, no activity for 4 years).
This does NOT work with OGM like Ferma.
- Gremlin Server with embedded Neo4j engine. This does NOT work with OGM like Ferma (https://stackoverflow.com/questions/48417910/how-is-it-possible-to-use-ferma-ogm-over-gremlin-server
), but perfectly fits PCE needs.
- Access a remote Neo4j server via Tinkerpop Java API using the BOLT protocol (https://github.com/SteelBridgeLabs/neo4j-gremlin-bolt - contributors keep it up to date, e.g. Neo4j 4.0.0 is already supported).
This complies with Ferma.

  **Important**: The implementation has a fundamental performance issue - it fetchs ALL vertex or edges into memory on the first Gremlin request. 
  https://github.com/SteelBridgeLabs/neo4j-gremlin-bolt/issues/70, https://github.com/SteelBridgeLabs/neo4j-gremlin-bolt/issues/46
  
### Tinkerpop
The major advocates of Tinkerpop are JanusGraph developers (supported by IBM - http://rredux.com/the-path-to-tinkerpop4.html, https://janusgraph.org/, 
https://yearofthegraph.xyz/newsletter/2019/04/graphs-in-the-cloud-the-year-of-the-graph-newsletter-april-2019/)

### Ferma
Requires Tinkerpop Graph API implementation, not only Gremlin support. It doesn't work with Neptune or CosmosDB
https://stackoverflow.com/questions/48417910/how-is-it-possible-to-use-ferma-ogm-over-gremlin-server
https://docs.aws.amazon.com/neptune/latest/userguide/access-graph-gremlin-java.html
https://docs.microsoft.com/en-us/azure/cosmos-db/create-graph-java

### OrientDB
The previous version got some negative feedback related to stability and support. 
https://www.reddit.com/r/nosql/comments/9rs3q8/neo4j_vs_orientdb/

## The next steps
**TBD**