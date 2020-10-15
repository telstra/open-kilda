# Migration of Kilda data from Neo4j to OrientDB

## Prerequisites
- Neo4j with installed APOC library (https://neo4j.com/labs/apoc/) version 3.3.0.4 or higher. 
Here's the command to check the version:
    ```
    RETURN apoc.version()
    ```

- Neo4j with enabled exporting and importing onto the file system. Check the following properties in Neo4j apoc.conf
(https://neo4j.com/labs/apoc/4.0/import/graphml/, https://neo4j.com/labs/apoc/4.0/export/graphml/):
    ```
    apoc.import.file.enabled=true  
    apoc.export.file.enabled=true
    ```
- OrientDB 3.0.x in a single node or clustered configuration. See https://orientdb.com/docs/3.0.x/.
- A blank database in OrientDB to import data into. Initialize it with Kilda schema - see https://github.com/telstra/open-kilda/blob/develop/docker/orientdb/init/create-db-with-schema.osql.
- The OrientDB database has *"admin"* user with allowed permissions for all resources except "database.bypassRestricted".
- The OrientDB database has *"kilda"* user with default *"writer"* role permissions, plus: UPDATE on "database.cluster.internal", 
ALL on "database.schema", READ on "database.systemclusters". See https://github.com/telstra/open-kilda/blob/develop/docker/orientdb/init/create-db-with-schema.osql.
- A host with network access to OrientDB, and a copy of these migration scripts.
- Downloaded and unpacked *orientdb-tp3-3.0.x* package on the host. See https://orientdb.com/docs/3.0.x/release/3.0/Available-Packages.html
- Activated "tinkerpop.orientdb" plugin in the gremlin client on the host. 
The command ":plugin use tinkerpop.orientdb". See https://orientdb.com/docs/3.0.x/tinkerpop3/OrientDB-TinkerPop3.html
  
## Migration steps
1. Dump Kilda data via Northbound for further validation.
2. Stop Kilda.
3. Export from Neo4j to GraphML. See https://neo4j.com/labs/apoc/4.0/export/graphml/
	```
    CALL apoc.export.graphml.query("MATCH (n) WHERE NOT labels(n) IN [['flow_event'],['flow_history'],['flow_dump'],['port_history']] OPTIONAL MATCH (n)-[l]-() RETURN n, l", "kilda_exported_data.graphml", {useTypes: true})
    CALL apoc.export.graphml.query("MATCH (n) WHERE labels(n) IN [['flow_event'],['flow_history'],['flow_dump'],['port_history']] OPTIONAL MATCH (n)-[l]-() RETURN n, l", "kilda_exported_history.graphml", {useTypes: true})
    ```
 
4. Copy the exported GraphML files to the host with *orientdb-tp3-3.0.x* tools & migration scripts.
5. Preprocess the exported GraphML:
    ```
    /path/to/scripts/preprocess-graphml.sh /path/to/graphml/kilda_exported_data.graphml
    ```

6. Temporary disable some indexes:
    ```
    /path/to/orientdb-tp3-3.0.x/bin/console.sh
    ```
    and then
    ```
    orientdb> CONNECT remote:{orientdb_host}/{database} admin
    orientdb {db=database}> LOAD SCRIPT /path/to/scripts/disable-indexes.osql
    ```

7. Import the result GraphML with Kilda data (**without history!**) into OrientDB:
    ```
    /path/to/orientdb-tp3-3.0.x/bin/gremlin.sh
    ```
    and then
    ```
    gremlin> graph = OrientGraph.open("remote:{orientdb_host}/{database}")
    gremlin> graph.io(IoCore.graphml()).readGraph("/path/to/graphml/kilda_exported_data.graphml")
    ```
   
8. Complete/denormalize the imported data:
    ```
    /path/to/orientdb-tp3-3.0.x/bin/console.sh
    ```
    and then
    ```
    orientdb> CONNECT remote:{orientdb_host}/{database} admin
    orientdb {db=database}> LOAD SCRIPT /path/to/scripts/complete-imported-data.osql
    ```

9. Re-enable the indexes:
    ```
    /path/to/orientdb-tp3-3.0.x/bin/console.sh
    ```
    and then
    ```
    orientdb> CONNECT remote:{orientdb_host}/{database} admin
    orientdb {db=database}> LOAD SCRIPT /path/to/scripts/reenable-indexes.osql
    ```

10. Deploy and start the new version of Kilda.
11. Dump Kilda data via Northbound, validate it with the previously taken dump.
12. Prepare the exported GraphML with Kilda history to be imported into "shadow" classes:
    ```
    /path/to/scripts/prepare-history-graphml.sh /path/to/graphml/kilda_exported_history.graphml
    /path/to/scripts/preprocess-graphml.sh /path/to/graphml/kilda_exported_history.graphml
    ```
13. Import the result GraphML with Kilda history into OrientDB:
    ```
    /path/to/orientdb-tp3-3.0.x/bin/gremlin.sh
    ```
    and then
    ```
    gremlin> graph = OrientGraph.open("remote:{orientdb_host}/{database}");
    gremlin> graph.io(IoCore.graphml()).readGraph("/path/to/graphml/kilda_exported_history.graphml")
    ```

14. Move the imported history data from "shadow" classes into standard into OrientDB:
    ```
    MOVE VERTEX (SELECT FROM flow_event_copy) TO CLASS:flow_event
    MOVE VERTEX (SELECT FROM flow_dump_copy) TO CLASS:flow_dump
    MOVE VERTEX (SELECT FROM flow_history_copy) TO CLASS:flow_history
    MOVE VERTEX (SELECT FROM port_history_copy) TO CLASS:port_history
    ```

Done.

## Revert migration steps

The following steps can be used to migrate the data back from OrientDB to Neo4j. This may be required if rollback
is to be performed after Kilda run on OrientDB backend and made changed into the data. 

1. Stop Kilda 
2. Export from OrientDB to GraphML:

    In the case of larger databases, you may need to allow usage of more heap memory. Proper amount of heap should be 
    considered empirically. Here's the example for 8 GB:
    ```
    export JAVA_OPTIONS="-Xmx8G"
    ```
   
    Run the gremlin client:
    ```
    cd orientdb-tp3-3.0.x/bin
    ./gremlin.sh
    ```
    and then
    ```
    gremlin> graph = OrientGraph.open("remote:{host}/{database}")
    gremlin> graph.io(IoCore.graphml()).writeGraph("orientdb_exported.graphml")
    ```

3. Copy the exported GraphML to the host with the migration scripts.
4. Preprocess the exported GraphML:
    ```
    /path/to/scripts/revert-preprocess-graphml.sh /path/to/graphml/orientdb_exported.graphml
    ```

5. Copy the exported GraphML to the Neo4j import folder. The import folder is configurable and depends on your Neo4j setup.
E.g. the default is */var/lib/neo4j/import* 
6. Import the exported GraphML into Neo4j. See https://neo4j.com/labs/apoc/4.0/import/graphml/

    In the case of larger databases, you may need to allocate more heap memory for Neo4j. Proper amount of heap should be considered empirically. 
    See https://neo4j.com/docs/operations-manual/current/performance/memory-configuration/index.html     
    
    Check the following properties in Neo4j apoc.conf:
    ```
    dbms.memory.heap.initial_size=8G
    dbms.memory.heap.max_size=8G
    dbms.memory.pagecache.size=4G
    ```

    Run in Neo4j:
	```
	CALL apoc.import.graphml("/path/to/graphml/orientdb_exported.graphml", {readLabels:true, batchSize:100})
    ```

Done.
