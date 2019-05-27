# Changelog

## v1.20.0 (27/05/2019)
### Features:
-  [#1954](https://github.com/telstra/open-kilda/pull/1954) Add new api for update max bandwidth (Issues: [#1937](https://github.com/telstra/open-kilda/issues/1937) [#1944](https://github.com/telstra/open-kilda/issues/1944) [#2003](https://github.com/telstra/open-kilda/issues/2003)) [**northbound**][**storm-topologies**]
-  [#2279](https://github.com/telstra/open-kilda/pull/2279) MultiFL. Add FL for statistics. [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#2380](https://github.com/telstra/open-kilda/pull/2380) Fix perf test to respect existing topology and properly avoid isl ports [**tests**]
-  [#2385](https://github.com/telstra/open-kilda/pull/2385) Remove unhandledInput from IslStatsBolt [**storm-topologies**]

### Improvements:
-  [#2305](https://github.com/telstra/open-kilda/pull/2305) MultiFL statistics documentation. [**docs**]
-  [#1860](https://github.com/telstra/open-kilda/pull/1860) Design for create flow using H&S approach [**docs**][**storm-topologies**]
-  [#2374](https://github.com/telstra/open-kilda/pull/2374) Minor fixes in meters centec test to keep up with latest code updates [**tests**]
-  [#2312](https://github.com/telstra/open-kilda/pull/2312) Rename IslStatsTopology to IslLatencyTopology and refactor (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**storm-topologies**]
-  [#2347](https://github.com/telstra/open-kilda/pull/2347) Add functional tests for BFD feature [**tests**]
-  [#2379](https://github.com/telstra/open-kilda/pull/2379) fix tests for protected path according to staging env [**tests**]
-  [#2389](https://github.com/telstra/open-kilda/pull/2389) Hotfix for update max bandwith 
-  [#2399](https://github.com/telstra/open-kilda/pull/2399) Added role and region as custom fields to FL logback config [**configuration**][**floodlight**]

## v1.19.0 (22/05/2019)
### Features:
-  [#2325](https://github.com/telstra/open-kilda/pull/2325) Use OF groups for discovery process (Issues:  [#580](https://github.com/telstra/open-kilda/issues/580) [**feature**] )
-  [#2320](https://github.com/telstra/open-kilda/pull/2320) Adding feature to display meter stats graph [**area/gui**] (Issues:  [#2025](https://github.com/telstra/open-kilda/issues/2025) [**area/gui**][**feature**] )
-  [#2155](https://github.com/telstra/open-kilda/pull/2155) Protected paths implementation [**area/api**]  (Issues:  [#1232](https://github.com/telstra/open-kilda/issues/1232) [**area/arch**][**feature**][**priority/2-high**] )
-  [#2217](https://github.com/telstra/open-kilda/pull/2217) Accomplish BFD session management (Issues:  [#1487](https://github.com/telstra/open-kilda/issues/1487) [**epic/BFD**] )

### Bug Fixes:
-  [#2362](https://github.com/telstra/open-kilda/pull/2362) Fix issue in topology settings upload json file. 
-  [#2344](https://github.com/telstra/open-kilda/pull/2344) Fix API PATCH /flows/{flow-id}. (Issues:  [#2343](https://github.com/telstra/open-kilda/issues/2343) [**bug**][**priority/2-high**] )

### Improvements:
-  [#2365](https://github.com/telstra/open-kilda/pull/2365) Fix unstable meters test to properly wait for flow update to complete [**area/testing**]
-  [#2352](https://github.com/telstra/open-kilda/pull/2352) Refactor flow crud test to avoid isl-port endpoints [**area/testing**]
-  [#2351](https://github.com/telstra/open-kilda/pull/2351) Feature/isl and switch maintenance [**area/gui**][**feature**] (Issues:  [#2294](https://github.com/telstra/open-kilda/issues/2294), [#2295](https://github.com/telstra/open-kilda/issues/2295) [**area/gui**][**feature**][**area/gui**][**feature**] )
-  [#2345](https://github.com/telstra/open-kilda/pull/2345) Fix KafkaBreaker hardcoded topic name [**area/testing**]
-  [#2341](https://github.com/telstra/open-kilda/pull/2341) REFACTOR: rename method according to internal convention [**area/testing**]
-  [#2340](https://github.com/telstra/open-kilda/pull/2340) Add EnduranceSpec with 'simulation' test [**area/testing**]
-  [#2330](https://github.com/telstra/open-kilda/pull/2330) extend existing 'Unable to create a flow on an isl port' test [**area/testing**]
-  [#2329](https://github.com/telstra/open-kilda/pull/2329) Use docker memlimits for 16GB setup as default (instead of unlimited) [**area/config**]
-  [#2326](https://github.com/telstra/open-kilda/pull/2326) ADD test: System is able to set min port speed for isl capacity [**area/testing**]
-  [#2324](https://github.com/telstra/open-kilda/pull/2324) Use virtual/hardware tags instead of assumptions directly in test [**area/testing**]
-  [#2323](https://github.com/telstra/open-kilda/pull/2323) Extend switch validation spec  [**area/testing**]
-  [#2317](https://github.com/telstra/open-kilda/pull/2317) Misc improvements in tests [**area/testing**] (Issues:  [#1865](https://github.com/telstra/open-kilda/issues/1865) [**area/cicd**][**area/testing**] )
-  [#2284](https://github.com/telstra/open-kilda/pull/2284) extend the 'Able to swap flow path' test [**area/testing**]
-  [#2269](https://github.com/telstra/open-kilda/pull/2269) ADD: System is able to reroute(intentional) flow with protected according to the priority field [**area/testing**]
-  [#2264](https://github.com/telstra/open-kilda/pull/2264) ADD test: Able to validate switch rules in case flow is created with protected path [**area/testing**]
-  [#2262](https://github.com/telstra/open-kilda/pull/2262) ADD test: Able to validate flow with protected path  [**area/testing**]
-  [#2261](https://github.com/telstra/open-kilda/pull/2261) ADD test: Able to synchronize rules for a flow with protected path [**area/testing**]
-  [#2241](https://github.com/telstra/open-kilda/pull/2241) Refactoring of VerificationPacket class [**refactor**]
-  [#2213](https://github.com/telstra/open-kilda/pull/2213) Add performance-tests skeleton and example test [**area/testing**]
-  [#2204](https://github.com/telstra/open-kilda/pull/2204) Stats request to Storm [**epic/multi-floodlight**]
-  [#2192](https://github.com/telstra/open-kilda/pull/2192) #1060 Add check for conflicts with ISL ports in FlowValidator.  (Issues:  [#1060](https://github.com/telstra/open-kilda/issues/1060) [**techdebt**] )
### Upgrade notes
If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j 
before you deploy and start this version.  You should execute migration script before starting of deployment from [migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.19.0/services/neo4j/migrations/1.1-bfd-session/1-update-constraints-changelog.xml)

In case of any issues you are able to rollback these changes using [rollback-script.cql](https://github.com/telstra/open-kilda/blob/v1.19.0/services/neo4j/migrations/1.1-bfd-session/rollback.cql)

## v1.18.2
### Features:
-  [#2309](https://github.com/telstra/open-kilda/pull/2309) Adding feature to import and export topology screen settings [**area/gui**] (Issues:  [#2293](https://github.com/telstra/open-kilda/issues/2293) [**area/gui**][**feature**] )
-  [#2280](https://github.com/telstra/open-kilda/pull/2280) Adding feature of copy to clipboard on list screens [**area/gui**] (Issues:  [#2275](https://github.com/telstra/open-kilda/issues/2275) [**area/gui**][**feature**] )
-  [#2271](https://github.com/telstra/open-kilda/pull/2271) Changed oracle jdk to openjdk 
-  [#2252](https://github.com/telstra/open-kilda/pull/2252) Enhancement to change default metric in packet loss graph to packets [**area/gui**] (Issues:  [#2202](https://github.com/telstra/open-kilda/issues/2202) [**area/gui**][**enhancement**]


### Bug Fixes:
-  [#2250](https://github.com/telstra/open-kilda/pull/2250) Add error response for CRUD Flow when features disabled [**area/northbound**] (Issues:  [#1920](https://github.com/telstra/open-kilda/issues/1920) [**bug**] )
-  [#2256](https://github.com/telstra/open-kilda/pull/2256) Add test for stats max values. Bump spock to 1.3 [**area/testing**] (Issues:  [#2255](https://github.com/telstra/open-kilda/issues/2255) [**bug**] )
-  [#2274](https://github.com/telstra/open-kilda/pull/2274) Restore gitignore coverage

### Improvements:
-  [#2311](https://github.com/telstra/open-kilda/pull/2311) Customise neo4j container 
-  [#2308](https://github.com/telstra/open-kilda/pull/2308) Add test to verify excess rules and meters deletion while sync [**area/testing**]
-  [#2306](https://github.com/telstra/open-kilda/pull/2306) Expose ElasticSearch ports. 
-  [#2303](https://github.com/telstra/open-kilda/pull/2303) Slightly improve lab service api parallelism [**area/testing**]
-  [#2300](https://github.com/telstra/open-kilda/pull/2300) Remove AbstractException class [**refactor**]
-  [#2297](https://github.com/telstra/open-kilda/pull/2297) Refactoring of functional tests to use topology helper where possible [**area/testing**]
-  [#2287](https://github.com/telstra/open-kilda/pull/2287) Rework TagExtension for new tags format. Introduce IterationTags [**area/testing**]
-  [#2286](https://github.com/telstra/open-kilda/pull/2286) Log discovery data received by speaker 
-  [#2277](https://github.com/telstra/open-kilda/pull/2277) Added argument for FROM in all Dockerfile [**area/cicd**]
-  [#2272](https://github.com/telstra/open-kilda/pull/2272) Improve test name logging in functional-tests module [**area/testing**]
-  [#2270](https://github.com/telstra/open-kilda/pull/2270) Changed default elasticsearch index name from logstash to kilda 
-  [#2260](https://github.com/telstra/open-kilda/pull/2260) Add fields to SwitchDto from database model. 
-  [#2240](https://github.com/telstra/open-kilda/pull/2240) Minor tests updates [**area/testing**]
-  [#2238](https://github.com/telstra/open-kilda/pull/2238) Manage to pass kafka-key from storm to speaker 
-  [#2234](https://github.com/telstra/open-kilda/pull/2234) Bolts in fl-router are stateless in terms of storm 
-  [#2233](https://github.com/telstra/open-kilda/pull/2233) Extend functional test for Floodlight and Kafka outage [**area/testing**]
-  [#2226](https://github.com/telstra/open-kilda/pull/2226) Improve TopologyHelper vastly. Introduce PotentialFlow entity [**area/testing**]
-  [#2223](https://github.com/telstra/open-kilda/pull/2223) Add test: "Unable to create a flow on an isl port" [**area/testing**] (Issues:  [#2222](https://github.com/telstra/open-kilda/issues/2222) [**area/testing**]
-  [#2210](https://github.com/telstra/open-kilda/pull/2210) Improve discovery topology logging 
-  [#2208](https://github.com/telstra/open-kilda/pull/2208) Lower log level for "lost" discovery packets 
-  [#2196](https://github.com/telstra/open-kilda/pull/2196) Fix functional tests for logging [**area/testing**]
-  [#2188](https://github.com/telstra/open-kilda/pull/2188) Func tests/add tests for updating bw [**area/testing**]
-  [#2186](https://github.com/telstra/open-kilda/pull/2186) Remove execute method override into `CoordinatedBolt` 
-  [#2167](https://github.com/telstra/open-kilda/pull/2167) ADD: meter validation tests [**area/testing**] (Issues:  [#2066](https://github.com/telstra/open-kilda/pull/2066), [#2131](https://github.com/telstra/open-kilda/issues/2131) [**area/testing**]

---

## v1.18.1

### Bug Fixes:
-  [#2318](https://github.com/telstra/open-kilda/pull/2318) Fix resource allocation below the low boundaries. (Resource pools may provide a value below the low boundaries if there's a gap in already allocated ones and this gap is below the low boundary)

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.18.0...v1.18.1).

---

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
