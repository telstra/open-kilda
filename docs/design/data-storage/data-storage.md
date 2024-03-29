# OpenKilda Data storage V2

## Overview
This doc describes the solution for data storage(s) used by Kilda.

## Requirements for Data storage
Kilda architecture among other requirements to the data storage, expects flexibility in choice of the storage options:
from a single instance deployed on a developer's workstation, up to scalable and distributed, which provides high-availability.

A dedicated storage for specific data may have own requirements, but all of them share the following:
- Durable writes with strong or eventual consistency.
- Support configuration with high availability and failover.
- Support online or incremental backups.
- Minimal overhead introduced by the persistence layer on CPU and memory resources.

## Data groups
The data model consists of elements which can be split into several groups by their purpose,
specific use cases and requirements to storage:
- Network topology: switches and links (ISLs)
- Flows (each one contains multiple Flow paths and corresponding Path segments)
- History records (flow events, port status changes, etc)
- Resources pools (flow encapsulation, meter, etc) 

Each data group has own and specific use cases, so we evaluate it separately from others:

### Network topology, flow data
_Use cases:_
- Transactional CRUD operations on entities that represent the network.
- Find a path between nodes (via PCE).
- Find related entities (flows by an ISL or by a switch)
- Visualize as a graph model on GUI / web application.
- Analyze connectivity in the network, and identify required backup links
- Execute custom traversals over the network structure for ad-hoc investigations.

_The solution:_ 
- The data is stored in a graph database ([OrientDB](https://orientdb.com/) or [JanusGraph](https://janusgraph.org/)) 
which has a complete implementation of [Tinkerpop Graph API](http://tinkerpop.apache.org/docs/3.4.6/reference/#graph).
- The persistence layer utilizes a one of existing OGM for data mapping: [Tinkerpop](https://tinkerpop.apache.org/) / [Ferma](http://syncleus.com/Ferma/).
- PCE utilizes graph traversal on the database side.

_Alternative solutions:_
- Use a graph database which supports [Gremlin](https://tinkerpop.apache.org/gremlin.html) traversal language,
but doesn't have a complete implementation of Tinkerpop Graph API: [Amazon Neptune](https://aws.amazon.com/neptune/), 
[Azure Cosmos DB](https://azure.microsoft.com/services/cosmos-db/). This requires custom data mapping to be coded in the persistence layer.

### History records
Use cases:
- Record an event / event details
- Find by keys, time periods, etc.
- Free-text search in event details (task_id, switch, etc.)

_The solution:_ 
- The data is stored in a relational database (Postgre, MySQL, RDS) or multi-model storage ([OrientDB](https://orientdb.com/multi-model-database/)). 
- The persistence layer utilizes ORM frameworks (e.g. Hibernate) for data mapping.

### Resources pools
Use cases:
- Reliable allocation under high contention of requests.  

_The solution:_ 
- The data is stored in a relational database (Postgre, MySQL, RDS) or multi-model storage ([OrientDB](https://orientdb.com/multi-model-database/)).
- The database must be ACID compliant.
- The persistence layer utilizes ORM frameworks (e.g. Hibernate) for data mapping.

_Alternative solutions:_
- Use the same graph database as for Network topology, flow data.

## Implementation overview

_The solution with a combination of graph and relational databases:_
![Persistence Layer with graph and relational databases](persistence-layer-rdbms.svg)

_The solution with a multi-model databases:_
![Persistence Layer with multi-model databases](persistence-layer-multi-model.svg)
 

### Notes on Tinkerpop / Ferma as OGM 

#### Tinkerpop
The major advocates of Tinkerpop are JanusGraph developers (supported by IBM - http://rredux.com/the-path-to-tinkerpop4.html, https://janusgraph.org/, 
https://yearofthegraph.xyz/newsletter/2019/04/graphs-in-the-cloud-the-year-of-the-graph-newsletter-april-2019/)

#### Ferma
Requires Tinkerpop Graph API implementation, not only Gremlin support. It doesn't work with Neptune or CosmosDB
https://stackoverflow.com/questions/48417910/how-is-it-possible-to-use-ferma-ogm-over-gremlin-server
https://docs.aws.amazon.com/neptune/latest/userguide/access-graph-gremlin-java.html
https://docs.microsoft.com/en-us/azure/cosmos-db/create-graph-java

#### Neo4j
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

#### OrientDB
The previous version got some negative feedback related to stability and support. 
https://www.reddit.com/r/nosql/comments/9rs3q8/neo4j_vs_orientdb/
