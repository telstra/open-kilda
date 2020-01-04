# Data migration

The package holds the data migration scripts for Neo4j in Kilda controller application.

### Prerequisites

- Make sure at least JRE 8 is set up and Java is included in your path.
- Download and decompress Liquigraph [zip, tar.gz or tar.bz2](https://www.liquigraph.org/latest/index.html#shell) in LIQUIGRAPH_DIR
- Unix users: make sure ```LIQUIGRAPH_DIR/liquigraph-cli/liquigraph.sh``` is executable.

### Migration scripts
The scripts are [Liquigraph](https://www.liquigraph.org/) changelog files grouped by data schema version.  

Each changelog file can be applied either separately or via the grouping changelog.xml.

### Running Liquigraph

```
$> cd LIQUIGRAPH_DIR/liquigraph-cli
$> ./liquigraph.sh --changelog MIGRATION_SCRIPT_DIR/changelog.xml" \
    --username neo4j
    --password \ # leave empty (password prompt will appear)
    --graph-db-uri jdbc:neo4j:http://localhost:7474/
```
