# Changelog

## v1.23.0 (10/06/2019)

### Features:
-  [#2466](https://github.com/telstra/open-kilda/pull/2466) Add a feature toggle to reroute a flow with default encapsulation type. (Issue: [#647](https://github.com/telstra/open-kilda/issues/647)) [**api**][**storm-topologies**]
-  [#2402](https://github.com/telstra/open-kilda/pull/2402) Add a VxLAN resource pool. (Issue: [#647](https://github.com/telstra/open-kilda/issues/647)) [**neo4j**]
-  [#2406](https://github.com/telstra/open-kilda/pull/2406) Added new Encapslation model class and Extended Encapsulation Enum [**storm-topologies**]

### Bug Fixes:
-  [#2475](https://github.com/telstra/open-kilda/pull/2475) Exclude switch object from switch features equals and hashCode

### Improvements:
-  [#2470](https://github.com/telstra/open-kilda/pull/2470) Add round trip rule into test [**tests**]
-  [#2440](https://github.com/telstra/open-kilda/pull/2440) add test: check that protected flow allows traffic on main/protected paths (Issues: [#2367](https://github.com/telstra/open-kilda/issues/2367) [#2415](https://github.com/telstra/open-kilda/issues/2415)) [**tests**]
-  [#2444](https://github.com/telstra/open-kilda/pull/2444) add possibility to set specific controllet on an OVS [**tests**]
-  [#2253](https://github.com/telstra/open-kilda/pull/2253) Add CommandContext into all tuples. (Issues: [#2199](https://github.com/telstra/open-kilda/issues/2199) [#2239](https://github.com/telstra/open-kilda/issues/2239)) [**storm-topologies**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.22.0...v1.23.0).

### Affected Components:
isllatency, stats, neo4j, flow, portstate, otsdb, swmanager, router, nbworker

### Upgrade notes
If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j 
before you deploy and start this version. You should execute [migration script](https://github.com/telstra/open-kilda/blob/v1.23.0/services/neo4j/migrations/1.5-switch-features/1-create-switch-features-changelog.xml) before starting of deployment.

In case of any issues you are able to rollback these changes using [rollback script](https://github.com/telstra/open-kilda/blob/v1.23.0/services/neo4j/migrations/1.5-switch-features/rollback.cql).

---

## v1.22.0 (06/06/2019)

### Features:
-  [#2322](https://github.com/telstra/open-kilda/pull/2322) Create a config with default encapsulation type in Neo4j. (Issues: [#2427](https://github.com/telstra/open-kilda/issues/2427) [#647](https://github.com/telstra/open-kilda/issues/647)) [**api**][**northbound**][**storm-topologies**]
-  [#2327](https://github.com/telstra/open-kilda/pull/2327) RTL: Part 2. Added round trip latency default rule (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**floodlight**]
-  [#2467](https://github.com/telstra/open-kilda/pull/2467) Add round trip latency rule to install/delete rules API (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**api**][**floodlight**]
-  [#2328](https://github.com/telstra/open-kilda/pull/2328) Pinned flow (Issue: [#2334](https://github.com/telstra/open-kilda/issues/2334)) [**api**][**storm-topologies**]

### Bug Fixes:
-  [#2390](https://github.com/telstra/open-kilda/pull/2390) Fix orphan meter resources [**storm-topologies**]

### Improvements:
-  [#2451](https://github.com/telstra/open-kilda/pull/2451) add tests for a pinned flow [**tests**]
-  [#2427](https://github.com/telstra/open-kilda/pull/2427) Add flow allocate protected path field migration 


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.21.2...v1.22.0).

### Affected Components:
reroute, nbworker, flow, nb, neo4j, fl

### Upgrade notes:

If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j
before you deploy and start this version. You should execute migration scripts before starting of deployment:
 - [1.3 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.22.0/services/neo4j/migrations/1.3-add-allocate-protected-path-field/1-add-allocate-protected-path-field-changelog.xml)
 - [1.4 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.22.0/services/neo4j/migrations/1.4-kilda-configuration/1-update-constraints-changelog.xml)

In case of any issues you are able to rollback 1.4 changes using [1.4 rollback-script.cql](https://github.com/telstra/open-kilda/blob/v1.22.0/services/neo4j/migrations/1.4-kilda-configuration/rollback.cql).
For migration 1.3 you must make a database backup to rollback changes.

---

## v1.21.2 (05/06/2019)
### Improvements:
-  [#2397](https://github.com/telstra/open-kilda/pull/2397) Use same transit VLAN for paths in both directions (Issue: [#2386](https://github.com/telstra/open-kilda/issues/2386)) [**storm-topologies**]
-  [#2384](https://github.com/telstra/open-kilda/pull/2384) extend GRPC tests to iterate over all available firmware versions (6.4+) (Issue: [#2251](https://github.com/telstra/open-kilda/issues/2251)) [**tests**]
-  [#2442](https://github.com/telstra/open-kilda/pull/2442) add verification that one transit vlan is created for a flow [**tests**]
-  [#2437](https://github.com/telstra/open-kilda/pull/2437) Add global flag to switch H&S reroutes [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.21.1...v1.21.2).

### Affected Components:
flow

---

## v1.21.1 (04/06/2019)
### Bug Fixes:
-  [#2439](https://github.com/telstra/open-kilda/pull/2439) Fix failsafe transaction interface 
-  [#2283](https://github.com/telstra/open-kilda/pull/2283) Do not reset defaultMaxBanwidth ISL's field on network topology start (Issue: [#2248](https://github.com/telstra/open-kilda/issues/2248)) [**storm-topologies**]
-  [#2412](https://github.com/telstra/open-kilda/pull/2412) Do not fail lab-service if openvswitch service is not running [**tests**]

### Improvements:
-  [#2436](https://github.com/telstra/open-kilda/pull/2436) Make transaction bolt "non-stateful" in terms of storm [**storm-topologies**]
-  [#2423](https://github.com/telstra/open-kilda/pull/2423) Router remove stateful (Issue: [#2234](https://github.com/telstra/open-kilda/issues/2234)) [**storm-topologies**]
-  [#2296](https://github.com/telstra/open-kilda/pull/2296) Extend transation manager with repeat mecahnism (Issue: [#2291](https://github.com/telstra/open-kilda/issues/2291)) 
-  [#2316](https://github.com/telstra/open-kilda/pull/2316) Refactor of PathVerificationService (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**floodlight**]
-  [#2289](https://github.com/telstra/open-kilda/pull/2289) SwitchManager topology refactoring (Issue: [#2215](https://github.com/telstra/open-kilda/issues/2215)) [**northbound**][**storm-topologies**]
-  [#2354](https://github.com/telstra/open-kilda/pull/2354) Detect and handle self looped ISL (Issue: [#2314](https://github.com/telstra/open-kilda/issues/2314)) [**storm-topologies**]
-  [#2424](https://github.com/telstra/open-kilda/pull/2424) fix test: System doesn't reroute main flow path when protected path is broken and new alt path is available (Issue: [#2420](https://github.com/telstra/open-kilda/issues/2420)) [**tests**]
-  [#2396](https://github.com/telstra/open-kilda/pull/2396) add verification that system deletes props for both directions, even … (Issue: [#2388](https://github.com/telstra/open-kilda/issues/2388)) [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.21.0...v1.21.1).

### Affected Components:
swmanager, fl, nb, neo4j, flow, network, router

---

## v1.21.0 (03/06/2019)
### Features:
-  [#2403](https://github.com/telstra/open-kilda/pull/2403) Adding  feature to toggle the menu sidebar (Issue:  [#2387](https://github.com/telstra/open-kilda/issues/2387)) [**gui**]
-  [#2394](https://github.com/telstra/open-kilda/pull/2394) Adding feature to delete ISL and improvement for list screen cache reset , latency calculation on flow detail screen,changed speed with max_bandwidth on isl detail (Issues:  [#2348](https://github.com/telstra/open-kilda/issues/2348), [#2339](https://github.com/telstra/open-kilda/issues/2339), [#2337](https://github.com/telstra/open-kilda/issues/2337), [#2338](https://github.com/telstra/open-kilda/issues/2338)) [**gui**]
-  [#2395](https://github.com/telstra/open-kilda/pull/2395) Re-implement flow create feature using H&S approach (Issue:  [#1866](https://github.com/telstra/open-kilda/issues/1866)) [**northbound**]
-  [#2360](https://github.com/telstra/open-kilda/pull/2360) Re-implement flow reroute feature using H&S approach (Issue:  [#2017](https://github.com/telstra/open-kilda/issues/2017)) [**northbound**]
-  [#2315](https://github.com/telstra/open-kilda/pull/2315) Add flow create API V2  (Issue:  [#1866](https://github.com/telstra/open-kilda/issues/1866)) [**northbound**][**area/api**] [**northbound**]

### Bug Fixes:
-  [#2364](https://github.com/telstra/open-kilda/pull/2364) Add test for issue #2363 (Issue:  [#2363](https://github.com/telstra/open-kilda/issues/2363)) [**tests**]

### Improvements:
-  [#2425](https://github.com/telstra/open-kilda/pull/2425) Log the root cause of OGM mapping exception in repositories 
-  [#2421](https://github.com/telstra/open-kilda/pull/2421) ignore broken test due to incorrect logic in test [**tests**]
-  [#2414](https://github.com/telstra/open-kilda/pull/2414) Implement "self-executable" fl commands, refactor fl structure (Issue:  [#1866](https://github.com/telstra/open-kilda/issues/1866))
-  [#2409](https://github.com/telstra/open-kilda/pull/2409) rename correlation id for functional and grpc tests [**tests**]
-  [#2408](https://github.com/telstra/open-kilda/pull/2408) add tests for pinned+protected flow [**tests**]
-  [#2405](https://github.com/telstra/open-kilda/pull/2405) Persist python lib versions for traffexam [**tests**]
-  [#2401](https://github.com/telstra/open-kilda/pull/2401) Add logging for ISL disco response in fl kafka producer and fl router
-  [#2399](https://github.com/telstra/open-kilda/pull/2399) Added role and region as custom fields to FL logback config [**area/config**]
-  [#2393](https://github.com/telstra/open-kilda/pull/2393) Create changelog generator script [**area/ops**]
-  [#2368](https://github.com/telstra/open-kilda/pull/2368) System doesn't reroute main flow path when protected path is broken and new alt path is available [**tests**]
-  [#2347](https://github.com/telstra/open-kilda/pull/2347) Add functional tests for BFD feature [**tests**]
-  [#2276](https://github.com/telstra/open-kilda/pull/2276) Make broadcast MAC address for a discovery packet configurable. (Issues:  [#2053](https://github.com/telstra/open-kilda/issues/2053))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.20.0...v1.21.0).

### Upgrade notes
If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j 
before you deploy and start this version.  You should execute [migration script](https://github.com/telstra/open-kilda/blob/v1.21.0/services/neo4j/migrations/1.2-history-event/1-update-constraints-changelog.xml) before starting of deployment.

In case of any issues you are able to rollback these changes using [rollback script](https://github.com/telstra/open-kilda/blob/v1.21.0/services/neo4j/migrations/1.2-history-event/rollback.cql).

---

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

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.19.0...v1.20.0).

---

## v1.19.0 (22/05/2019)
### Features:
-  [#2325](https://github.com/telstra/open-kilda/pull/2325) Use OF groups for discovery process (Issue:  [#580](https://github.com/telstra/open-kilda/issues/580))
-  [#2320](https://github.com/telstra/open-kilda/pull/2320) Adding feature to display meter stats graph (Issue:  [#2025](https://github.com/telstra/open-kilda/issues/2025) [**gui**])
-  [#2155](https://github.com/telstra/open-kilda/pull/2155) Protected paths implementation  (Issue:  [#1232](https://github.com/telstra/open-kilda/issues/1232) [**area/arch**])
-  [#2217](https://github.com/telstra/open-kilda/pull/2217) Accomplish BFD session management (Issue:  [#1487](https://github.com/telstra/open-kilda/issues/1487))

### Bug Fixes:
-  [#2362](https://github.com/telstra/open-kilda/pull/2362) Fix issue in topology settings upload json file. 
-  [#2344](https://github.com/telstra/open-kilda/pull/2344) Fix API PATCH /flows/{flow-id}. (Issue:  [#2343](https://github.com/telstra/open-kilda/issues/2343))

### Improvements:
-  [#2365](https://github.com/telstra/open-kilda/pull/2365) Fix unstable meters test to properly wait for flow update to complete [**tests**]
-  [#2352](https://github.com/telstra/open-kilda/pull/2352) Refactor flow crud test to avoid isl-port endpoints [**tests**]
-  [#2351](https://github.com/telstra/open-kilda/pull/2351) Feature/isl and switch maintenance  (Issues:  [#2294](https://github.com/telstra/open-kilda/issues/2294), [#2295](https://github.com/telstra/open-kilda/issues/2295) [**gui**])
-  [#2345](https://github.com/telstra/open-kilda/pull/2345) Fix KafkaBreaker hardcoded topic name [**tests**]
-  [#2341](https://github.com/telstra/open-kilda/pull/2341) REFACTOR: rename method according to internal convention [**tests**]
-  [#2340](https://github.com/telstra/open-kilda/pull/2340) Add EnduranceSpec with 'simulation' test [**tests**]
-  [#2330](https://github.com/telstra/open-kilda/pull/2330) extend existing 'Unable to create a flow on an isl port' test [**tests**]
-  [#2329](https://github.com/telstra/open-kilda/pull/2329) Use docker memlimits for 16GB setup as default (instead of unlimited) [**area/config**]
-  [#2326](https://github.com/telstra/open-kilda/pull/2326) ADD test: System is able to set min port speed for isl capacity [**tests**]
-  [#2324](https://github.com/telstra/open-kilda/pull/2324) Use virtual/hardware tags instead of assumptions directly in test [**tests**]
-  [#2323](https://github.com/telstra/open-kilda/pull/2323) Extend switch validation spec  [**tests**]
-  [#2317](https://github.com/telstra/open-kilda/pull/2317) Misc improvements in tests (Issue:  [#1865](https://github.com/telstra/open-kilda/issues/1865) [**tests**])
-  [#2284](https://github.com/telstra/open-kilda/pull/2284) extend the 'Able to swap flow path' test [**tests**]
-  [#2269](https://github.com/telstra/open-kilda/pull/2269) ADD: System is able to reroute(intentional) flow with protected according to the priority field [**tests**]
-  [#2264](https://github.com/telstra/open-kilda/pull/2264) ADD test: Able to validate switch rules in case flow is created with protected path [**tests**]
-  [#2262](https://github.com/telstra/open-kilda/pull/2262) ADD test: Able to validate flow with protected path  [**tests**]
-  [#2261](https://github.com/telstra/open-kilda/pull/2261) ADD test: Able to synchronize rules for a flow with protected path [**tests**]
-  [#2241](https://github.com/telstra/open-kilda/pull/2241) Refactoring of VerificationPacket class 
-  [#2213](https://github.com/telstra/open-kilda/pull/2213) Add performance-tests skeleton and example test [**tests**]
-  [#2204](https://github.com/telstra/open-kilda/pull/2204) Stats request to Storm 
-  [#2192](https://github.com/telstra/open-kilda/pull/2192) #1060 Add check for conflicts with ISL ports in FlowValidator.  (Issue:  [#1060](https://github.com/telstra/open-kilda/issues/1060))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.18.2...v1.19.0).

### Upgrade notes
If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j 
before you deploy and start this version.  You should execute [migration script](https://github.com/telstra/open-kilda/blob/v1.19.0/services/neo4j/migrations/1.1-bfd-session/1-update-constraints-changelog.xml) before starting of deployment.

In case of any issues you are able to rollback these changes using [rollback script](https://github.com/telstra/open-kilda/blob/v1.19.0/services/neo4j/migrations/1.1-bfd-session/rollback.cql).

---

## v1.18.2
### Features:
-  [#2309](https://github.com/telstra/open-kilda/pull/2309) Adding feature to import and export topology screen settings (Issue:  [#2293](https://github.com/telstra/open-kilda/issues/2293) [**gui**])
-  [#2280](https://github.com/telstra/open-kilda/pull/2280) Adding feature of copy to clipboard on list screens (Issue:  [#2275](https://github.com/telstra/open-kilda/issues/2275) [**gui**])
-  [#2271](https://github.com/telstra/open-kilda/pull/2271) Changed oracle jdk to openjdk 
-  [#2252](https://github.com/telstra/open-kilda/pull/2252) Enhancement to change default metric in packet loss graph to packets (Issue:  [#2202](https://github.com/telstra/open-kilda/issues/2202) [**gui**])


### Bug Fixes:
-  [#2250](https://github.com/telstra/open-kilda/pull/2250) Add error response for CRUD Flow when features disabled [**northbound**] (Issue:  [#1920](https://github.com/telstra/open-kilda/issues/1920))
-  [#2256](https://github.com/telstra/open-kilda/pull/2256) Add test for stats max values. Bump spock to 1.3 [**tests**] (Issue:  [#2255](https://github.com/telstra/open-kilda/issues/2255))
-  [#2274](https://github.com/telstra/open-kilda/pull/2274) Restore gitignore coverage

### Improvements:
-  [#2311](https://github.com/telstra/open-kilda/pull/2311) Customise neo4j container 
-  [#2308](https://github.com/telstra/open-kilda/pull/2308) Add test to verify excess rules and meters deletion while sync [**tests**]
-  [#2306](https://github.com/telstra/open-kilda/pull/2306) Expose ElasticSearch ports. 
-  [#2303](https://github.com/telstra/open-kilda/pull/2303) Slightly improve lab service api parallelism [**tests**]
-  [#2300](https://github.com/telstra/open-kilda/pull/2300) Remove AbstractException class 
-  [#2297](https://github.com/telstra/open-kilda/pull/2297) Refactoring of functional tests to use topology helper where possible [**tests**]
-  [#2287](https://github.com/telstra/open-kilda/pull/2287) Rework TagExtension for new tags format. Introduce IterationTags [**tests**]
-  [#2286](https://github.com/telstra/open-kilda/pull/2286) Log discovery data received by speaker 
-  [#2277](https://github.com/telstra/open-kilda/pull/2277) Added argument for FROM in all Dockerfile [**area/cicd**]
-  [#2272](https://github.com/telstra/open-kilda/pull/2272) Improve test name logging in functional-tests module [**tests**]
-  [#2270](https://github.com/telstra/open-kilda/pull/2270) Changed default elasticsearch index name from logstash to kilda 
-  [#2260](https://github.com/telstra/open-kilda/pull/2260) Add fields to SwitchDto from database model. 
-  [#2240](https://github.com/telstra/open-kilda/pull/2240) Minor tests updates [**tests**]
-  [#2238](https://github.com/telstra/open-kilda/pull/2238) Manage to pass kafka-key from storm to speaker 
-  [#2234](https://github.com/telstra/open-kilda/pull/2234) Bolts in fl-router are stateless in terms of storm 
-  [#2233](https://github.com/telstra/open-kilda/pull/2233) Extend functional test for Floodlight and Kafka outage [**tests**]
-  [#2226](https://github.com/telstra/open-kilda/pull/2226) Improve TopologyHelper vastly. Introduce PotentialFlow entity [**tests**]
-  [#2223](https://github.com/telstra/open-kilda/pull/2223) Add test: "Unable to create a flow on an isl port" [**tests**] (Issue:  [#2222](https://github.com/telstra/open-kilda/issues/2222) [**tests**])
-  [#2210](https://github.com/telstra/open-kilda/pull/2210) Improve discovery topology logging 
-  [#2208](https://github.com/telstra/open-kilda/pull/2208) Lower log level for "lost" discovery packets 
-  [#2196](https://github.com/telstra/open-kilda/pull/2196) Fix functional tests for logging [**tests**]
-  [#2188](https://github.com/telstra/open-kilda/pull/2188) Func tests/add tests for updating bw [**tests**]
-  [#2186](https://github.com/telstra/open-kilda/pull/2186) Remove execute method override into `CoordinatedBolt` 
-  [#2167](https://github.com/telstra/open-kilda/pull/2167) ADD: meter validation tests [**tests**] (Issues:  [#2066](https://github.com/telstra/open-kilda/pull/2066), [#2131](https://github.com/telstra/open-kilda/issues/2131) [**tests**])

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.18.1...v1.18.2).

---

## v1.18.1 (03/05/2019)
### Bug Fixes:
-  [#2318](https://github.com/telstra/open-kilda/pull/2318) Fix resource allocation below the low boundaries. (Resource pools may provide a value below the low boundaries if there's a gap in already allocated ones and this gap is below the low boundary)

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.18.0...v1.18.1).

---

## v1.18.0 (02/05/2019)
### Features:
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
-  [#1906](https://github.com/telstra/open-kilda/pull/1906) Protected paths design [**docs**] (Issue:  [#1232](https://github.com/telstra/open-kilda/issues/1232))
-  [#1904](https://github.com/telstra/open-kilda/pull/1904) Design for flow rerouting using H&S approach [**docs**]

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

## v1.17.1 (15/04/2019)
### Bug Fixes:
-  [#2259](https://github.com/telstra/open-kilda/pull/2259) Propagate ISL's cost updates to the link-props objects. (Issue:  [#2257](https://github.com/telstra/open-kilda/issues/2257))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.17.0...v1.17.1).

---

## v1.17.0 (10/04/2019)
### Features:
-  [#2161](https://github.com/telstra/open-kilda/pull/2161) Combine `api` and `northbound` modules, prepare Northbound for API v2 support

### Bug Fixes:
-  [#2236](https://github.com/telstra/open-kilda/pull/2236) Fix the network dump issue in Floodlight router (Router doesn't set proper switch statuses when a region goes back up after being offline).
-  [#2242](https://github.com/telstra/open-kilda/pull/2242) Fix anonymous authentication for Northbound health-check ([#2161](https://github.com/telstra/open-kilda/pull/2161) broke anonymous authentication for `/api/v1/health-check`)

### Improvements:
-  [#2231](https://github.com/telstra/open-kilda/pull/2231) Add logging of stacktrace in DiscoveryBolt exception handling
-  [#2227](https://github.com/telstra/open-kilda/pull/2227) Add storm.yaml template config in confd
-  [#2235](https://github.com/telstra/open-kilda/pull/2235) Enable OVS virtual meters by default [**tests**]
-  [#2179](https://github.com/telstra/open-kilda/pull/2179) Add test: System is able to reroute flow in the correct order based on the priority field [**tests**] (Issue:  [#2144](https://github.com/telstra/open-kilda/issues/2144))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.16.2...v1.17.0).
