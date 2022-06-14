# Upgrade OrientDB to use HA

## Prerequisites
* OrientDB 3.0.x with the database to be migrated.
  + Downloaded and unpacked *orientdb-tp3-3.0.x* package on the host. See https://orientdb.org/docs/3.0.x/release/3.0/Available-Packages.html
  + Activated "tinkerpop.orientdb" plugin in the gremlin client on the host.
  The command ":plugin use tinkerpop.orientdb". See https://orientdb.org/docs/3.0.x/tinkerpop3/OrientDB-TinkerPop3.html

* OrientDB 3.2.x in a clustered configuration. See https://orientdb.org/docs/3.2.x/.
  + A blank database in OrientDB to import data into. Initialize it with Kilda schema - see https://github.com/telstra/open-kilda/blob/develop/docker/db-migration/migrations/README.md.
  + Downloaded and unpacked *gremlin-console-3.4.7-distribution* package on the host. See https://github.com/orientechnologies/orientdb/issues/9496#issuecomment-1013083394
  + Activated "tinkerpop.orientdb" plugin in the gremlin client on the host.
  The command ":plugin use tinkerpop.orientdb". See https://orientdb.org/docs/3.2.x/tinkerpop3/OrientDB-TinkerPop3.html

## Migration steps

1. Dump Kilda data via Northbound for further validation.
2. Turn off all feature toggles, remembering which toggles were turned on.
3. Export from OrientDB 3.0.x to GraphML. 
   ```
    /path/to/orientdb-tp3-3.0.x/bin/gremlin.sh
    ```
   and then
    ```
    gremlin> graph = OrientGraph.open("remote:{orientdb_3.0.x_host}/{database}")
    gremlin> graph.io(IoCore.graphml()).writeGraph("/path/to/graphml/kilda_exported_data.graphml")
    ```

4. Copy the exported GraphML file to the host with *gremlin-console-3.4.7-distribution* tool.
5. Preprocess the exported GraphML:
    ```
    /path/to/scripts/preprocess-graphml.sh /path/to/graphml/kilda_exported_data.graphml
    ```

6. Import the result GraphML into OrientDB 3.2.x:
    ```
    /path/to/gremlin-console-3.4.7-distribution/bin/gremlin.sh
    ```
   and then
    ```
    gremlin> graph = OrientGraph.open("remote:{orientdb_3.2.x_host}/{database}")
    gremlin> graph.io(IoCore.graphml()).readGraph("/path/to/graphml/kilda_exported_data.graphml")
    ```
7. Redeploy of Kilda.
8. Enable disabled feature toggles.
9. Dump Kilda data via Northbound, validate it with the previously taken dump.

Done.

## Revert migration steps

The above steps can be used to revert migration from OrientDB 3.2.x to OrientDB 3.0.: `gremlin-console-3.4.7-distribution/bin/gremlin.sh` is used to write a GraphML file, and `orientdb-tp3-3.0.x/bin/gremlin.sh` is used to import from a GraphML file to OrientDB 3.0.x.
