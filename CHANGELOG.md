# Changelog

## v1.18.0

### Enhancements:
-  [#2230](https://github.com/telstra/open-kilda/pull/2230) ISL and switch details can be open from a flow path on GUI (Issue:  [#1740](https://github.com/telstra/open-kilda/issues/1740))

### Bug Fixes:
-  [#2245](https://github.com/telstra/open-kilda/pull/2245) Add error response for sync meters on umetered flow  (Issue:  [#2043](https://github.com/telstra/open-kilda/issues/2043))

### Improvements:
-  [#2002](https://github.com/telstra/open-kilda/pull/2002) Introduce the data model with flow encapsulation, flow paths and resource pools. (Issue:  [#1519](https://github.com/telstra/open-kilda/issues/1519))
-  [#2103](https://github.com/telstra/open-kilda/pull/2103) Adopt the new data model via a wrapper entity (Issue:  [#2018](https://github.com/telstra/open-kilda/issues/2018))
-  [#2106](https://github.com/telstra/open-kilda/pull/2106) Implement transactional resource pools (Issue:  [#2017](https://github.com/telstra/open-kilda/issues/2017))
-  [#2138](https://github.com/telstra/open-kilda/pull/2138) Migration scripts for the new data model. (Issue:  [#1519](https://github.com/telstra/open-kilda/issues/1519))
-  [#2247](https://github.com/telstra/open-kilda/pull/2247) Add core properties auto create metrics,tagks,tagvs to opentsdb.conf

### Other changes:
-  [#1906](https://github.com/telstra/open-kilda/pull/1906) Protected paths design [**area/docs**] (Issue:  [#1232](https://github.com/telstra/open-kilda/issues/1232))
-  [#1904](https://github.com/telstra/open-kilda/pull/1904) Design for flow rerouting using H&S approach [**area/docs**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.17.1...v1.18.0).

### Upgrade notes
If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j 
before you deploy and start this version. 

The data migration steps:
- If any of Kilda components is running, shut it down. Neo4j must be up and a backup is created.
- Execute [1-update-constraints-changelog.xml](https://github.com/telstra/open-kilda/blob/v1.18.0/services/neo4j/migrations/1.0-flow-paths-n-resources/1-update-constraints-changelog.xml)
 and [2-migration-changelog.xml](https://github.com/telstra/open-kilda/blob/v1.18.0/services/neo4j/migrations/1.0-flow-paths-n-resources/2-migration-changelog.xml) scripts using Liquigraph tools 
(see [migration README.md](https://github.com/telstra/open-kilda/blob/v1.18.0/services/neo4j/migrations/README.md)).
- Deploy and start all Kilda components of this version.
- After CRUD tests are passed and the system is being operational with no issues for some period, you need to execute the cleanup script 
[3-cleanup-changelog.xml](https://github.com/telstra/open-kilda/blob/v1.18.0/services/neo4j/migrations/1.0-flow-paths-n-resources/3-cleanup-changelog.xml).

In case of any issue you can rollback the migration:
- Execute queries from [rollback-migration.cql](https://github.com/telstra/open-kilda/blob/v1.18.0/services/neo4j/migrations/1.0-flow-paths-n-resources/rollback-migration.cql)
to revert changes made by ```2-migration-changelog.xml```. 
- Execute queries from [rollback-constaints.cql](https://github.com/telstra/open-kilda/blob/v1.18.0/services/neo4j/migrations/1.0-flow-paths-n-resources/rollback-constaints.cql)
to revert changes made by ```1-update-constraints-changelog.xml```. 
 
**IMPORTANT:** The changes made by the cleanup script (3-cleanup-changelog.xml) can't be reverted!  

---

## v1.17.1

### Bug Fixes:
-  [#2259](https://github.com/telstra/open-kilda/pull/2259) Propagate ISL's cost updates to the link-props objects. (Issue:  [#2257](https://github.com/telstra/open-kilda/issues/2257))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.17.0...v1.17.1).

---

## v1.17.0

### Enhancements:
-  [#2161](https://github.com/telstra/open-kilda/pull/2161) Combine `api` and `northbound` modules, prepare Northbound for API v2 support

### Bug Fixes:
-  [#2236](https://github.com/telstra/open-kilda/pull/2236) Fix the network dump issue in Floodlight router (Router doesn't set proper switch statuses when a region goes back up after being offline).
-  [#2242](https://github.com/telstra/open-kilda/pull/2242) Fix anonymous authentication for Northbound health-check ([#2161](https://github.com/telstra/open-kilda/pull/2161) broke anonymous authentication for `/api/v1/health-check`)

### Improvements:
-  [#2231](https://github.com/telstra/open-kilda/pull/2231) Add logging of stacktrace in DiscoveryBolt exception handling
-  [#2227](https://github.com/telstra/open-kilda/pull/2227) Add storm.yaml template config in confd
-  [#2235](https://github.com/telstra/open-kilda/pull/2235) Enable OVS virtual meters by default [**area/testing**]
-  [#2179](https://github.com/telstra/open-kilda/pull/2179) Add test: System is able to reroute flow in the correct order based on the priority field [**area/testing**] (Issue:  [#2144](https://github.com/telstra/open-kilda/issues/2144))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.16.2...v1.17.0).