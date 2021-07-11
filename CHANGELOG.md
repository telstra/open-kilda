# Changelog

## v1.99.0 (15/07/2021)

### Features:
-  [#4327](https://github.com/telstra/open-kilda/pull/4327) add test for flowSLA [**tests**]

### Bug Fixes:
-  [#4288](https://github.com/telstra/open-kilda/pull/4288) Fix link properties validation (Issue: [#4161](https://github.com/telstra/open-kilda/issues/4161)) [**storm-topologies**]
-  [#4303](https://github.com/telstra/open-kilda/pull/4303) Fix the missing cache errors in Stats caused by partial flow updates (Issue: [#4246](https://github.com/telstra/open-kilda/issues/4246)) [**storm-topologies**]
-  [#4368](https://github.com/telstra/open-kilda/pull/4368) Fix/build issues in UI [**gui**]
-  [#4346](https://github.com/telstra/open-kilda/pull/4346) Fix feature toggles (Issues: [#2441](https://github.com/telstra/open-kilda/issues/2441) [#4345](https://github.com/telstra/open-kilda/issues/4345)) [**storm-topologies**]

### Improvements:
-  [#4357](https://github.com/telstra/open-kilda/pull/4357) fix assertPortIsStable in PortAntiflapHelper [**tests**]
-  [#4358](https://github.com/telstra/open-kilda/pull/4358) unignore test according to  #4271 [**tests**]
-  [#4359](https://github.com/telstra/open-kilda/pull/4359) Minor typos fixed in tests [**tests**]
-  [#4360](https://github.com/telstra/open-kilda/pull/4360) Force ordering of flow's history events
-  [#4364](https://github.com/telstra/open-kilda/pull/4364) reduce latencyUpdateInterval for local func-tests [**tests**]
-  [#4341](https://github.com/telstra/open-kilda/pull/4341) Add func tests for strict_bandwidth feature [**tests**]
-  [#4343](https://github.com/telstra/open-kilda/pull/4343) Fix 'make test-topology' command [**tests**]
-  [#4349](https://github.com/telstra/open-kilda/pull/4349) unignore according to #4317 [**tests**]
-  [#4351](https://github.com/telstra/open-kilda/pull/4351) Add validation of OrientDB connections in the graph factory [**storm-topologies**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.98.0...v1.99.0).

### Affected Components:
flow-hs, gui, stats, network, orientdb, connected, reroute, ping, isllatency, nbworker, swmanager, flow-monitor

### Upgrade notes:
OrientDB schema have been changed in this release. You need to apply schema migration. Please follow [migration instructions](https://github.com/telstra/open-kilda/tree/develop/docker/db-migration/migrations).

---

## v1.98.0 (02/07/2021)

### Features:
-  [#4328](https://github.com/telstra/open-kilda/pull/4328) refactor/extend tests for islRttSpec [**tests**]
-  [#4331](https://github.com/telstra/open-kilda/pull/4331) Add a strict bandwidth flag to flow [**northbound**][**storm-topologies**]
-  [#4334](https://github.com/telstra/open-kilda/pull/4334) Strict bandwidth flag for flow design [**docs**]
-  [#4336](https://github.com/telstra/open-kilda/pull/4336) Add strict bandwidth flag logic to Reroute topology [**storm-topologies**]

### Bug Fixes:
-  [#4317](https://github.com/telstra/open-kilda/pull/4317) Fixed remove of VXLAN excess rules (Issues: [#4314](https://github.com/telstra/open-kilda/issues/4314) [#4315](https://github.com/telstra/open-kilda/issues/4315) [#4316](https://github.com/telstra/open-kilda/issues/4316)) [**floodlight**][**storm-topologies**]
-  [#4323](https://github.com/telstra/open-kilda/pull/4323) Fix reroute flows on ISL (Issue: [#4290](https://github.com/telstra/open-kilda/issues/4290)) [**storm-topologies**]
-  [#4338](https://github.com/telstra/open-kilda/pull/4338) Fixed flow Patch log [**storm-topologies**]
-  [#4340](https://github.com/telstra/open-kilda/pull/4340) Fix delete flow race condition in flow monitoring
-  [#4342](https://github.com/telstra/open-kilda/pull/4342) [Server42] Seek to latest on startup in control app

### Improvements:
-  [#4298](https://github.com/telstra/open-kilda/pull/4298) unignore vxlan+flowLoop test [**tests**]
-  [#4325](https://github.com/telstra/open-kilda/pull/4325) add test for #4316 [**tests**]
-  [#4262](https://github.com/telstra/open-kilda/pull/4262) add tests for #3841, #3858 (Issues: [#3841](https://github.com/telstra/open-kilda/issues/3841) [#3858](https://github.com/telstra/open-kilda/issues/3858)) [**tests**]
-  [#4137](https://github.com/telstra/open-kilda/pull/4137) unignore vxlan spec according to obsolete issues (#2995,#2996,#3021) (Issue: [#4281](https://github.com/telstra/open-kilda/issues/4281)) [**tests**]
-  [#4330](https://github.com/telstra/open-kilda/pull/4330) adjust swapEnpointSpec to a new validation(s42Port) [**tests**]
-  [#4270](https://github.com/telstra/open-kilda/pull/4270) extend MaxLatencySpec [**tests**]
-  [#4344](https://github.com/telstra/open-kilda/pull/4344) Fix flow monitoring deactivation

### Other changes:
-  [#4307](https://github.com/telstra/open-kilda/pull/4307) Refactor how healthchecks are performed in func tests [**tests**]
-  [#4266](https://github.com/telstra/open-kilda/pull/4266) Add func tests for traffic mirror feature [**tests**]
-  [#4333](https://github.com/telstra/open-kilda/pull/4333) Upgrade gradle to 6.9 and test-logger plugin to 3.0.0 [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.97.0...v1.98.0).

### Affected Components:
nb, swmanager, server42, fl, reroute, flow-hs, nbworker, flow-monitor

---

## v1.97.0 (22/06/2021)

### Features:
-  [#4256](https://github.com/telstra/open-kilda/pull/4256) Implement rules handling for Server42 ISL RTT [**floodlight**][**storm-topologies**]
-  [#4257](https://github.com/telstra/open-kilda/pull/4257) Implement messaging for Server42 ISL RTT (Issue: [#4256](https://github.com/telstra/open-kilda/issues/4256)) [**storm-topologies**]
-  [#4264](https://github.com/telstra/open-kilda/pull/4264) Implement ISL RTT support in Server42 (Issue: [#4257](https://github.com/telstra/open-kilda/issues/4257))
-  [#4309](https://github.com/telstra/open-kilda/pull/4309) - adding feature to export switch list to csv format (Issue: [#4308](https://github.com/telstra/open-kilda/issues/4308)) [**gui**]

### Bug Fixes:
-  [#4295](https://github.com/telstra/open-kilda/pull/4295) Add validation for conflict between connected devices and flow mirror point
-  [#4302](https://github.com/telstra/open-kilda/pull/4302) Put correct field name for message payload
-  [#4304](https://github.com/telstra/open-kilda/pull/4304) Add validation for conflict between server 42 and flow mirror point (Issue: [#4282](https://github.com/telstra/open-kilda/issues/4282)) [**storm-topologies**]
-  [#4312](https://github.com/telstra/open-kilda/pull/4312) Add set_udp_src_port into action list for server42 output action [**floodlight**]
-  [#4281](https://github.com/telstra/open-kilda/pull/4281) Sync transit rules via SpeackerCommand [**floodlight**]

### Improvements:
-  [#4321](https://github.com/telstra/open-kilda/pull/4321) Replace sun.misc.BASE64* with java.util.Base64 [**gui**]
-  [#4293](https://github.com/telstra/open-kilda/pull/4293) Move flow transit encapsulation actions to group bucket [**floodlight**]
-  [#4296](https://github.com/telstra/open-kilda/pull/4296) Add feature toggle for flow SLA monitoring reactions

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.96.0...v1.97.0).

### Affected Components:
isllatency, swmanager, gui, fl, flow-monitor, nbworker, network, server42, flow-hs

### Upgrade notes:
OrientDB schema have been changed in this release. You need to apply schema migration. Please follow [migration instructions](https://github.com/telstra/open-kilda/tree/develop/docker/db-migration/migrations).

---

## v1.96.0 (11/06/2021)

### Features:
-  [#4292](https://github.com/telstra/open-kilda/pull/4292) Added Kilda OVS copy and swap fields into FeatureDetecter service [**floodlight**][**storm-topologies**]
-  [#4300](https://github.com/telstra/open-kilda/pull/4300) Use Kilda OVS [**tests**]
-  [#4306](https://github.com/telstra/open-kilda/pull/4306) Set UDP src for VXLAN Server42 packets [**floodlight**]

### Bug Fixes:
-  [#4299](https://github.com/telstra/open-kilda/pull/4299) Fixed typo in missing meters log [**storm-topologies**]
-  [#4279](https://github.com/telstra/open-kilda/pull/4279) Update eth src/dst for looped VXLAN packets (Issue: [#4072](https://github.com/telstra/open-kilda/issues/4072))
-  [#4287](https://github.com/telstra/open-kilda/pull/4287) Added/updated requirements for lab service and traff exam [**tests**]

### Improvements:
-  [#4258](https://github.com/telstra/open-kilda/pull/4258) Test/fix meter burst size [**tests**]
-  [#4234](https://github.com/telstra/open-kilda/pull/4234) Test/flow monitoring stats [**tests**]
-  [#4268](https://github.com/telstra/open-kilda/pull/4268) add test for #4267 [**tests**]
-  [#4284](https://github.com/telstra/open-kilda/pull/4284) Update flow-monitoring documentation [**docs**]
-  [#4286](https://github.com/telstra/open-kilda/pull/4286) improve FlowStatSpec [**tests**]

### Other changes:
-  [#4225](https://github.com/telstra/open-kilda/pull/4225) Unignore tests due to #2585 reason. Add comments about issue #4224 [**tests**]
-  [#4291](https://github.com/telstra/open-kilda/pull/4291) Ignore func test according to #4290 [**tests**]
-  [#4199](https://github.com/telstra/open-kilda/pull/4199) Add tests for #3923 [**tests**]
-  [#4239](https://github.com/telstra/open-kilda/pull/4239) Add ability to change sw ip to lockkeeper [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.95.0...v1.96.0).

### Affected Components:
flow-hs, network, swmanager, fl

## v1.95.0 (07/06/2021)

### Features:
-  [#4230](https://github.com/telstra/open-kilda/pull/4230) Add mirror config into floodlight commands. (Issue: [#3849](https://github.com/telstra/open-kilda/issues/3849)) [**floodlight**]
-  [#4263](https://github.com/telstra/open-kilda/pull/4263) Add flow SLA monitoring reactions [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#4294](https://github.com/telstra/open-kilda/pull/4294) Fix flow latency properties migration 
-  [#4280](https://github.com/telstra/open-kilda/pull/4280) Server42-stats: read statistics from latest commited (Issue: [#3799](https://github.com/telstra/open-kilda/issues/3799)) [**storm-topologies**]
-  [#4271](https://github.com/telstra/open-kilda/pull/4271) Use right encapsulation type during reroute if encapsulation was changed (Issue: [#4267](https://github.com/telstra/open-kilda/issues/4267)) 
-  [#4276](https://github.com/telstra/open-kilda/pull/4276) Replace EMPTY fields with getEmpty() methods (Issue: [#4275](https://github.com/telstra/open-kilda/issues/4275)) [**floodlight**]
-  [#4277](https://github.com/telstra/open-kilda/pull/4277) Update boost library url 
-  [#4252](https://github.com/telstra/open-kilda/pull/4252) Reinstall meter with incorrect burst size (Issue: [#4251](https://github.com/telstra/open-kilda/issues/4251)) [**floodlight**]

### Improvements:
-  [#4260](https://github.com/telstra/open-kilda/pull/4260) Improved logs for switch validation [**storm-topologies**]
-  [#4273](https://github.com/telstra/open-kilda/pull/4273) Fixed incorrect server42 rules count in SwitchActivationSpec [**tests**]
-  [#4274](https://github.com/telstra/open-kilda/pull/4274) fix "cleanup" in retries/swPortConfig specs [**tests**]
-  [#4278](https://github.com/telstra/open-kilda/pull/4278) Rework flow rtt metric tags [**storm-topologies**]
-  [#4253](https://github.com/telstra/open-kilda/pull/4253) Remove network reduction in PCE 
-  [#3937](https://github.com/telstra/open-kilda/pull/3937) [Server42] script for generate protobuf java files 
-  [#4269](https://github.com/telstra/open-kilda/pull/4269) Extend traffgen api to return address stats [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.94.0...v1.95.0).

### Affected Components:
swmanager, nb, flow-monitor, fl, server42, flow-hs, stats, reroute

### Upgrade notes:
OrientDB schema have been changed in this release. You need to apply schema migration. Please follow [migration instructions](https://github.com/telstra/open-kilda/tree/develop/docker/db-migration/migrations).

---

## v1.94.0 (01/06/2021)

### Features:
-  [#4229](https://github.com/telstra/open-kilda/pull/4229) Add flow mirror points models. (Issue: [#3849](https://github.com/telstra/open-kilda/issues/3849))
-  [#4220](https://github.com/telstra/open-kilda/pull/4220) Send flow monitoring stats to openTSDB
-  [#4191](https://github.com/telstra/open-kilda/pull/4191) Server 42 QinQ support (Issue: [#4158](https://github.com/telstra/open-kilda/issues/4158)) [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#4261](https://github.com/telstra/open-kilda/pull/4261) Specify the docker version for lab-api
-  [#4272](https://github.com/telstra/open-kilda/pull/4272) Do not install/remove server42 QinQ shared rule for single switch flows
-  [#4275](https://github.com/telstra/open-kilda/pull/4275) Use method instead of constant for empty speaker context.
-  [#4247](https://github.com/telstra/open-kilda/pull/4247) Added @ToString into Edge class to make logs correct

### Improvements:
-  [#4226](https://github.com/telstra/open-kilda/pull/4226) Improve get paths api response [**northbound**][**storm-topologies**]
-  [#4265](https://github.com/telstra/open-kilda/pull/4265) extend CleanupVerifierExtension [**tests**]
-  [#3977](https://github.com/telstra/open-kilda/pull/3977) extend server42Spec [**tests**]
-  [#4245](https://github.com/telstra/open-kilda/pull/4245) fix/refactor tests in vxlanFlowSpec, logicalPortSpec, swValidationSingleSwFlowSpec [**tests**]
-  [#4250](https://github.com/telstra/open-kilda/pull/4250) add test according to #4246 [**tests**]

### Other changes:
-  [#4107](https://github.com/telstra/open-kilda/pull/4107) Flow traffic mirroring design (Issue: [#3849](https://github.com/telstra/open-kilda/issues/3849)) [**docs**]
-  [#4204](https://github.com/telstra/open-kilda/pull/4204) Server42: build with main makefile [**configuration**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.93.0...v1.94.0).

### Affected Components:
swmanager, flow-hs, fl, nb, flow-monitor, orientdb, nbworker

### Upgrade notes:
If you have an older version of Kilda installed, then you must migrate the data stored in OrientDB.
Follow the [migration instructions](https://github.com/telstra/open-kilda/tree/develop/docker/db-migration/migrations).

---

## v1.93.0 (17/05/2021)

### Bug Fixes:
-  [#4255](https://github.com/telstra/open-kilda/pull/4255) Upgrading node and angular cli to fix gui build issue (Issue: [#4254](https://github.com/telstra/open-kilda/issues/4254)) [**gui**]
-  [#4148](https://github.com/telstra/open-kilda/pull/4148) Fix deserialization error in flow-monitoring on ISL delete event
-  [#4249](https://github.com/telstra/open-kilda/pull/4249) Fix isl cache unit test [**tests**]

### Improvements:
-  [#4200](https://github.com/telstra/open-kilda/pull/4200) Test/improve cleanups [**tests**]
-  [#4208](https://github.com/telstra/open-kilda/pull/4208) add test for a flow with latency according to #4201 [**tests**]
-  [#3923](https://github.com/telstra/open-kilda/pull/3923) Workaround for loss of switch disconnect event [**storm-topologies**]
-  [#4248](https://github.com/telstra/open-kilda/pull/4248) Update Floodlight version to fix unit tests [**tests**]
-  [#4223](https://github.com/telstra/open-kilda/pull/4223) Document aimed to describe OF cookies format [**docs**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.92.0...v1.93.0).

### Affected Components:
flow-monitor, gui, network

---

## v1.92.0 (05/05/2021)

### Features:
-  [#4201](https://github.com/telstra/open-kilda/pull/4201) Rework LATENCY path computation strategy (Issue: [#3968](https://github.com/telstra/open-kilda/issues/3968))

### Improvements:
-  [#4228](https://github.com/telstra/open-kilda/pull/4228) unignore/fix unstableIslSpec [**tests**]
-  [#4235](https://github.com/telstra/open-kilda/pull/4235) refactor test according to #4231 [**tests**]
-  [#4211](https://github.com/telstra/open-kilda/pull/4211) Test/fix according to rc191 [**tests**]
-  [#4218](https://github.com/telstra/open-kilda/pull/4218) add "cleanup" in the "switch" package [**tests**]

### Other changes:
-  [#4240](https://github.com/telstra/open-kilda/pull/4240) Fix Liquibase dependency - put liquibase-orientdb-0.3.0 into the repo
-  [#4215](https://github.com/telstra/open-kilda/pull/4215) Unignore contention test and reveal #4214 [**tests**]
-  [#4219](https://github.com/telstra/open-kilda/pull/4219) Add required waits after sw knockout in MflStatSpec [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.91.1...v1.92.0).

### Affected Components:
flow-hs

---

## v1.91.1 (05/05/2021)

### Bug Fixes:
-  [#4243](https://github.com/telstra/open-kilda/pull/4243) Hotfix to fix issue in getting flow of ISL (Issue: [#4242](https://github.com/telstra/open-kilda/issues/4242)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.91.0...v1.91.1).

### Affected Components:
gui

---

## v1.91.0 (26/04/2021)

### Features:
-  [#4196](https://github.com/telstra/open-kilda/pull/4196) update func tests according to multyfloodligth-monitoring [**tests**]
-  [#4167](https://github.com/telstra/open-kilda/pull/4167) Multifloodlight monitoring [**floodlight**][**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#4133](https://github.com/telstra/open-kilda/pull/4133) Added error message for unsupported encapsulation type during create/update/swap (Issue: [#2587](https://github.com/telstra/open-kilda/issues/2587)) 
-  [#4207](https://github.com/telstra/open-kilda/pull/4207) Handle exceptions during deserialization in NorthBound and GRPC (Issue: [#4128](https://github.com/telstra/open-kilda/issues/4128)) [**northbound**]
-  [#4213](https://github.com/telstra/open-kilda/pull/4213) Correct grpc speaker consumer group id 

### Improvements:
-  [#4128](https://github.com/telstra/open-kilda/pull/4128) Upgrade and fix gradle dependencies [**floodlight**][**northbound**][**storm-topologies**]
-  [#4206](https://github.com/telstra/open-kilda/pull/4206) Use the same consumer group for both northbound colors [**northbound**]
-  [#4181](https://github.com/telstra/open-kilda/pull/4181) Improvement in flows to update new fields and upgrade to v2 (Issue: [#4178](https://github.com/telstra/open-kilda/issues/4178)) [**gui**]
-  [#4190](https://github.com/telstra/open-kilda/pull/4190) improve/add cleanup in all specs in the 'flow' package [**tests**]
-  [#4158](https://github.com/telstra/open-kilda/pull/4158) Sync server42 ingress rule via Speaker command 

### Other changes:
-  [#4171](https://github.com/telstra/open-kilda/pull/4171) Cleanup the folder with documentation [**docs**]
-  [#4112](https://github.com/telstra/open-kilda/pull/4112) Update flow monitoring feature design (Issue: [#3968](https://github.com/telstra/open-kilda/issues/3968)) [**docs**]
-  [#4184](https://github.com/telstra/open-kilda/pull/4184) Increase stability of s42 spec [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.90.0...v1.91.0).

### Affected Components:
portstate, gui, nbworker, stats-router, reroute, connected, network, ping, isllatency, stats, fl, flow-monitor, swmanager, grpc, router, flow-hs, nb, otsdb

### Upgrade notes:
Release contains DB migration, see [instructions](https://github.com/telstra/open-kilda/blob/develop/docker/db-migration/migrations/README.md) see instructions for its installation.

---

## v1.90.0 (13/04/2021)

### Bug Fixes:
-  [#4104](https://github.com/telstra/open-kilda/pull/4104) Fix zkClient to work in Windows env
-  [#4168](https://github.com/telstra/open-kilda/pull/4168) Cleanup flow multi table flags
-  [#4179](https://github.com/telstra/open-kilda/pull/4179) Fixed unhandled input in RerouteTopology if topology is inactive [**storm-topologies**]
-  [#4187](https://github.com/telstra/open-kilda/pull/4187) Fixed flow loop for single vlan multi table flow (Issue: [#4186](https://github.com/telstra/open-kilda/issues/4186))
-  [#4202](https://github.com/telstra/open-kilda/pull/4202) Fixed missed RequestsLogFile in func tests template [**tests**]
-  [#4140](https://github.com/telstra/open-kilda/pull/4140) Added max latency fields into /v1/network/paths API (Issue: [#4139](https://github.com/telstra/open-kilda/issues/4139)) [**northbound**][**storm-topologies**]

### Improvements:
-  [#4162](https://github.com/telstra/open-kilda/pull/4162) Added unit tests for server42 commands and refactor processInstallServer42Rule() [**floodlight**][**tests**]
-  [#4166](https://github.com/telstra/open-kilda/pull/4166) imrpove "System reverts both flows if fails during rule installation … [**tests**]
-  [#4172](https://github.com/telstra/open-kilda/pull/4172) ignore test according to #4170 [**tests**]
-  [#4174](https://github.com/telstra/open-kilda/pull/4174) improve FlowHistorySpec, FlowValidationNegative/V2Spec [**tests**]
-  [#4182](https://github.com/telstra/open-kilda/pull/4182) Make traffexam to not hide into network namespace [**tests**]
-  [#4192](https://github.com/telstra/open-kilda/pull/4192) Add sonargate github action [**tests**]
-  [#4195](https://github.com/telstra/open-kilda/pull/4195) Switch unittests to Ubuntu 20.04 [**tests**]
-  [#4147](https://github.com/telstra/open-kilda/pull/4147) add test for 'network/paths' endpoint using LATENCY strategy [**tests**]
-  [#4087](https://github.com/telstra/open-kilda/pull/4087) Add tests for flow monitoring feature [**tests**]
-  [#4154](https://github.com/telstra/open-kilda/pull/4154) Removed unused methods and variables from floodlight [**floodlight**]
-  [#4156](https://github.com/telstra/open-kilda/pull/4156) improve s42 spec [**tests**]

### Other changes:
-  [#4165](https://github.com/telstra/open-kilda/pull/4165) Enable test-retry plugin [**tests**]
-  [#4105](https://github.com/telstra/open-kilda/pull/4105) Start moving to spock2.0 [**tests**]
-  [#4109](https://github.com/telstra/open-kilda/pull/4109) Multyfloodlight monitoring design [**docs**]
-  [#4193](https://github.com/telstra/open-kilda/pull/4193) Update lockkeeper readme with proper docker build command [**docs**]
-  [#4153](https://github.com/telstra/open-kilda/pull/4153) Improve assertion error message for sw validation in swHelper [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.89.1...v1.90.0).

### Affected Components:
flow-hs, fl, nb, nbworker, reroute

### Upgrade notes:
If you have an older version of Kilda installed, then you must migrate the data stored in OrientDB.
Follow the [migration instructions](https://github.com/telstra/open-kilda/tree/develop/docker/db-migration/migrations).

---

## v1.89.1 (08/04/2021)

### Bug Fixes:
-  [#4197](https://github.com/telstra/open-kilda/pull/4197) Updated version of switch fsm to fix bfd [**floodlight**][**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.89.0...v1.89.1).

### Affected Components:
fl, network

---

## v1.89.0 (25/03/2021)

### Features:
-  [#4121](https://github.com/telstra/open-kilda/pull/4121) Do not put switch into inactive state on network start [**docs**][**storm-topologies**]
-  [#4080](https://github.com/telstra/open-kilda/pull/4080) update tests according to flowHistoryStatuses [**tests**]

### Bug Fixes:
-  [#4115](https://github.com/telstra/open-kilda/pull/4115) Fixed incorrect reinstallation of server42 vlan/vxlan rules during switch sync (Issue: [#3815](https://github.com/telstra/open-kilda/issues/3815)) [**storm-topologies**]
-  [#4122](https://github.com/telstra/open-kilda/pull/4122) Added command context into Flow Monitoring topology (Issue: [#4142](https://github.com/telstra/open-kilda/issues/4142))
-  [#4123](https://github.com/telstra/open-kilda/pull/4123) Fixed incorrect names in Flow Monitoring topology definition file (Issue: [#4143](https://github.com/telstra/open-kilda/issues/4143))
-  [#4124](https://github.com/telstra/open-kilda/pull/4124) Fixed handling on max latency SLA (Issue: [#4141](https://github.com/telstra/open-kilda/issues/4141))
-  [#4131](https://github.com/telstra/open-kilda/pull/4131) Fix rollback migration for flow multitable flag
-  [#4132](https://github.com/telstra/open-kilda/pull/4132) Fix missing stacktraces in topology logs [**storm-topologies**]
-  [#3954](https://github.com/telstra/open-kilda/pull/3954) fix for issue in importing db sql scripts [**gui**]

### Improvements:
-  [#4113](https://github.com/telstra/open-kilda/pull/4113) Fix path(s) and status cleanup in Flow H&S operations (Issue: [#3090](https://github.com/telstra/open-kilda/issues/3090))
-  [#4125](https://github.com/telstra/open-kilda/pull/4125) Renaming of SwitchSyncFsm methods and states (Issue: [#4115](https://github.com/telstra/open-kilda/issues/4115)) [**storm-topologies**]
-  [#4127](https://github.com/telstra/open-kilda/pull/4127) unignore server42 spec [**tests**]
-  [#4129](https://github.com/telstra/open-kilda/pull/4129) refactor ConfigurationSpec according to #4130 [**tests**]
-  [#4134](https://github.com/telstra/open-kilda/pull/4134) Removed unused methods from FlowCommandFactory [**floodlight**]
-  [#4138](https://github.com/telstra/open-kilda/pull/4138) add test for #3413 [**tests**]
-  [#4015](https://github.com/telstra/open-kilda/pull/4015) minor fixes/updates in LogicalPortSpec and SwitchesSpec [**tests**]
-  [#4144](https://github.com/telstra/open-kilda/pull/4144) Improve logging of processed kafka requests in GRPC speaker
-  [#4145](https://github.com/telstra/open-kilda/pull/4145) refactor unstable test in swapEndpointSpec [**tests**]
-  [#4082](https://github.com/telstra/open-kilda/pull/4082) Application metrics and performance tests for Flow operations (Issue: [#3605](https://github.com/telstra/open-kilda/issues/3605)) [**storm-topologies**][**tests**]
-  [#3958](https://github.com/telstra/open-kilda/pull/3958) Delicate network topology activate deactivate [**storm-topologies**]
-  [#4086](https://github.com/telstra/open-kilda/pull/4086) Support project build with Java 11
-  [#3964](https://github.com/telstra/open-kilda/pull/3964) add test for #3938 [**tests**]

### Other changes:
-  [#3942](https://github.com/telstra/open-kilda/pull/3942) Add func tests for #3940 and #3941 [**tests**]
-  [#4149](https://github.com/telstra/open-kilda/pull/4149) Extend FlowRulesSpec test to do a more precise rule check [**tests**]
-  [#4152](https://github.com/telstra/open-kilda/pull/4152) No longer send lldp/arp traffgen packets in parallel [**tests**]
-  [#4093](https://github.com/telstra/open-kilda/pull/4093) Add zk state verifications to HealthCheckSpec (Issues: [#4052](https://github.com/telstra/open-kilda/issues/4052) [#4078](https://github.com/telstra/open-kilda/issues/4078)) [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.88.0...v1.89.0).

### Affected Components:
swmanager, otsdb, fl, flow-monitor, router, portstate, isllatency, network, reroute, grpc, stats, gui, nbworker, ping, flow-hs, connected

---

## v1.88.0 (12/03/2021)

### Bug Fixes:
-  [#4096](https://github.com/telstra/open-kilda/pull/4096) Set active=false for DatapointParseBolt by default [**storm-topologies**]
-  [#4099](https://github.com/telstra/open-kilda/pull/4099) Fix wrong protected path status in a case of failed reroute and degraded flow (Issue: [#4038](https://github.com/telstra/open-kilda/issues/4038)) [**storm-topologies**] 
-  [#4110](https://github.com/telstra/open-kilda/pull/4110) Added inner vlan into RequestedFlowMapper (Issue: [#4106](https://github.com/telstra/open-kilda/issues/4106)) [**storm-topologies**]
-  [#4114](https://github.com/telstra/open-kilda/pull/4114) Fix serialization of persistent entity in FlowFetcher [**storm-topologies**]
-  [#4053](https://github.com/telstra/open-kilda/pull/4053) Move reroute commands emitting out of transaction (Issue: [#4034](https://github.com/telstra/open-kilda/issues/4034)) [**storm-topologies**]
-  [#4074](https://github.com/telstra/open-kilda/pull/4074) Fight port vs switch dump events race condition (Issue: [#3990](https://github.com/telstra/open-kilda/issues/3990)) [**storm-topologies**]

### Improvements:
-  [#4111](https://github.com/telstra/open-kilda/pull/4111) Send inactive only on fsm terminated for flow create [**storm-topologies**]
-  [#4116](https://github.com/telstra/open-kilda/pull/4116) Remove stale option [**configuration**]
-  [#4054](https://github.com/telstra/open-kilda/pull/4054) Do not create isl on isl fail events [**storm-topologies**]
-  [#4052](https://github.com/telstra/open-kilda/pull/4052) Added expected state for storm components into ZooKeeper [**storm-topologies**] 
-  [#4063](https://github.com/telstra/open-kilda/pull/4063) Improve performance of H&S flow operations: improve parallelism (Issue: [#3605](https://github.com/telstra/open-kilda/issues/3605)) [**storm-topologies**]
-  [#4010](https://github.com/telstra/open-kilda/pull/4010) Accept topology definition file as an argument on topology submission. [**storm-topologies**]
-  [#3946](https://github.com/telstra/open-kilda/pull/3946) Move flow endpoints multitable flag to flow path level (Issues: [#3341](https://github.com/telstra/open-kilda/issues/3341) [#3940](https://github.com/telstra/open-kilda/issues/3940) [#3941](https://github.com/telstra/open-kilda/issues/3941)) [**storm-topologies**] 
-  [#4078](https://github.com/telstra/open-kilda/pull/4078) Added expected state for Floodlight (Issue: [#4052](https://github.com/telstra/open-kilda/issues/4052)) [**floodlight**] 

### Other changes:
-  [#4102](https://github.com/telstra/open-kilda/pull/4102) Updated gitignore file issue in kildagui to fix untracked files (Issue: [#4094](https://github.com/telstra/open-kilda/issues/4094)) [**gui**]
-  [#4103](https://github.com/telstra/open-kilda/pull/4103) Update the issue reference which blocks ContentionV2Spec tests for parallel delete requests [**tests**]
-  [#4108](https://github.com/telstra/open-kilda/pull/4108) Increase wait in ThrottlingRerouteSpec [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.87.0...v1.88.0).

### Affected Components:
ping, nbworker, isllatency, reroute, orientdb, connected, swmanager, portstate, router, network, stats-router, gui, stats, flow-hs, otsdb, fl

### Upgrade notes:
If you have an older version of Kilda installed, then you must migrate the data stored in OrientDB
before you deploy and start this version. Follow the [migration instructions](https://github.com/telstra/open-kilda/tree/develop/docker/db-migration/migrations).

---

## v1.87.0 (10/03/2021)

### Features:
-  [#4050](https://github.com/telstra/open-kilda/pull/4050) add tests for "max_latency_tier2" [**tests**]
-  [#4065](https://github.com/telstra/open-kilda/pull/4065) Add flow monitoring feature implementation (Issue: [#3968](https://github.com/telstra/open-kilda/issues/3968)) [**storm-topologies**]
-  [#4070](https://github.com/telstra/open-kilda/pull/4070) Add flow history statuses endpoint (Issue: [#4058](https://github.com/telstra/open-kilda/issues/4058)) [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#3998](https://github.com/telstra/open-kilda/pull/3998) Fix feature toggles class name (Issue: [#3939](https://github.com/telstra/open-kilda/issues/3939)) 
-  [#4088](https://github.com/telstra/open-kilda/pull/4088) Clean up lifecycle events for hub bolts [**storm-topologies**]

### Improvements:
-  [#4098](https://github.com/telstra/open-kilda/pull/4098) avoid "conflict" flows in ProtectedPathV2Spec [**tests**]
-  [#3921](https://github.com/telstra/open-kilda/pull/3921) Kilda gui gradle build structure (Issue: [#3841](https://github.com/telstra/open-kilda/issues/3841)) [**gui**]
-  [#4062](https://github.com/telstra/open-kilda/pull/4062) Improve performance of H&S flow operations: ISL bandwidth updates 
-  [#4073](https://github.com/telstra/open-kilda/pull/4073) ignore tests according to the issues [**tests**]
-  [#4009](https://github.com/telstra/open-kilda/pull/4009) unignore test according to #3987 [**tests**]
-  [#4083](https://github.com/telstra/open-kilda/pull/4083) fix test:"Able to delete switch rules by cookie/priority" [**tests**]
-  [#4085](https://github.com/telstra/open-kilda/pull/4085) adjust MetersSpec and SwitchHelper according to vxlan on wb [**tests**]
-  [#4089](https://github.com/telstra/open-kilda/pull/4089) remove "neo4j" from templates 
-  [#4091](https://github.com/telstra/open-kilda/pull/4091) improve PartialUpdateSpec(avoid singleSwSinglePort flow after updating) [**tests**]

### Other changes:
-  [#4058](https://github.com/telstra/open-kilda/pull/4058) Flow status timestamps design [**docs**]
-  [#4084](https://github.com/telstra/open-kilda/pull/4084) Add retry for unstable ping verification in func tests [**tests**]
-  [#4092](https://github.com/telstra/open-kilda/pull/4092) Increase some timeout timers and make devices test to wait for rules [**tests**]

---

## v1.86.0 (23/02/2021)

### Bug Fixes:
-  [#4069](https://github.com/telstra/open-kilda/pull/4069) Move initZookeeper for fl into service setup [**floodlight**]
-  [#4075](https://github.com/telstra/open-kilda/pull/4075) Fix for CommandContext in Otsdb DataPointParserBolt 
-  [#4076](https://github.com/telstra/open-kilda/pull/4076) Get rid of timeMillis in json layout for nb logs (Issue: [#4056](https://github.com/telstra/open-kilda/issues/4056)) [**northbound**]
-  [#4077](https://github.com/telstra/open-kilda/pull/4077) Changed match for arp vxlan rule (Issues: [#3199](https://github.com/telstra/open-kilda/issues/3199) [#4019](https://github.com/telstra/open-kilda/issues/4019)) [**floodlight**]
-  [#4081](https://github.com/telstra/open-kilda/pull/4081) Fix PCE data fetching for switches with multiple encapsulation types [**storm-topologies**]

### Improvements:
-  [#4064](https://github.com/telstra/open-kilda/pull/4064) Added waiting for connection into zkClient 
-  [#4066](https://github.com/telstra/open-kilda/pull/4066) Added ability to run integrational tests in parallel [**tests**]
-  [#4071](https://github.com/telstra/open-kilda/pull/4071) fix the "isVxlanEnabled" method in vxlan specs [**tests**]
-  [#4020](https://github.com/telstra/open-kilda/pull/4020) Allow VXLAN rules on WB switches (Issue: [#4019](https://github.com/telstra/open-kilda/issues/4019)) [**floodlight**]
-  [#4059](https://github.com/telstra/open-kilda/pull/4059) Improve performance of H&S flow operations: data model and graph super-nodes [**storm-topologies**]
-  [#4060](https://github.com/telstra/open-kilda/pull/4060) Improve performance of H&S flow operations: PCE [**storm-topologies**]
-  [#4061](https://github.com/telstra/open-kilda/pull/4061) Improve performance of H&S flow operations: back-pressure on FLOW spout 

### Other changes:
-  [#4057](https://github.com/telstra/open-kilda/pull/4057) More tests ignored according to existing defects (Issues: [#4055](https://github.com/telstra/open-kilda/issues/4055) [#4056](https://github.com/telstra/open-kilda/issues/4056)) [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.85.0...v1.86.0).

### Affected Components:
ping, reroute, network, otsdb, swmanager, nb, fl, nbworker, flow-hs

---

## v1.85.0 (15/02/2021)

### Features:
-  [#4046](https://github.com/telstra/open-kilda/pull/4046) Implement logic for the `max_latency_tier2` field (Issue: [#4029](https://github.com/telstra/open-kilda/issues/4029)) [**storm-topologies**]
-  [#4018](https://github.com/telstra/open-kilda/pull/4018) Zero Downtime cleanup Part 1: Removed common vars and deprecated methods
-  [#4022](https://github.com/telstra/open-kilda/pull/4022) Zero Downtime cleanup Part 2: remove component name and runid

### Bug Fixes:
-  [#4047](https://github.com/telstra/open-kilda/pull/4047) Added missed lifeCycleEventComponent into update hub config

### Improvements:
-  [#4032](https://github.com/telstra/open-kilda/pull/4032) minor improvements in ConfigurationSpec and FlowLoopSpec [**tests**]
-  [#4041](https://github.com/telstra/open-kilda/pull/4041) Not handling duplicate lifecycle signals
-  [#4044](https://github.com/telstra/open-kilda/pull/4044) ignore test acording to 4043 [**tests**]
-  [#3997](https://github.com/telstra/open-kilda/pull/3997) Improvement/flow search and user activity (Issue: [#3979](https://github.com/telstra/open-kilda/issues/3979)) [**gui**]
-  [#4018](https://github.com/telstra/open-kilda/pull/4018) Zero Downtime cleanup Part 1: Removed common vars and deprecated methods

### Other changes:
-  [#4036](https://github.com/telstra/open-kilda/pull/4036) Run cleanup verifier after testFixture cleanup instead of before [**tests**]
-  [#4039](https://github.com/telstra/open-kilda/pull/4039) Improve some protected path tests with additional verifications (Issue: [#4038](https://github.com/telstra/open-kilda/issues/4038)) [**tests**]
-  [#4048](https://github.com/telstra/open-kilda/pull/4048) Rely on port history when manipulating ports to workaround antiflap [**tests**]
-  [#4051](https://github.com/telstra/open-kilda/pull/4051) Ignore protected path test due to #4034 [**tests**]
-  [#4057](https://github.com/telstra/open-kilda/pull/4057) More tests ignored according to existing defects (Issues: [#4055](https://github.com/telstra/open-kilda/issues/4055) [#4056](https://github.com/telstra/open-kilda/issues/4056)) [**tests**]
-  [#4011](https://github.com/telstra/open-kilda/pull/4011) Update lombok, gradle and plugins versions to improve build time [**floodlight**][**northbound**][**storm-topologies**][**tests**]
-  [#4016](https://github.com/telstra/open-kilda/pull/4016) Update console output format for func tests [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.84.0...v1.85.0).

### Affected Components:
gui, reroute, fl, stats-router, portstate, nbworker, ping, swmanager, nb, flow-hs, connected, grpc, router, isllatency, stats, otsdb, network

---

## v1.84.0 (09/02/2021)

### Features:
-  [#3905](https://github.com/telstra/open-kilda/pull/3905) Stats topology zero-downtime upgrade [**storm-topologies**]
-  [#3914](https://github.com/telstra/open-kilda/pull/3914) Ping zero-downtime upgrade [**storm-topologies**]
-  [#3922](https://github.com/telstra/open-kilda/pull/3922) Port state zero-downtime upgrade [**storm-topologies**]
-  [#3861](https://github.com/telstra/open-kilda/pull/3861) Zero downtime nb worker [**storm-topologies**]
-  [#3862](https://github.com/telstra/open-kilda/pull/3862) Zero downtime floodlight router [**storm-topologies**]
-  [#3863](https://github.com/telstra/open-kilda/pull/3863) Network 0-Downtime PoC [**storm-topologies**]
-  [#3930](https://github.com/telstra/open-kilda/pull/3930) IslLatency topology zero-downtime upgrade [**storm-topologies**]
-  [#3932](https://github.com/telstra/open-kilda/pull/3932) OpenTSDB topology zero-downtime upgrade 
-  [#3879](https://github.com/telstra/open-kilda/pull/3879) Zero downtime Switch Manager [**storm-topologies**]
-  [#3944](https://github.com/telstra/open-kilda/pull/3944) Zero downtime logs for NB and GRPC [**northbound**]
-  [#3886](https://github.com/telstra/open-kilda/pull/3886) Zero downtime reroute [**storm-topologies**]
-  [#3888](https://github.com/telstra/open-kilda/pull/3888) Zero Downtime for Flow HS 
-  [#3959](https://github.com/telstra/open-kilda/pull/3959) Zero Downtime server42 control topology 

### Bug Fixes:
-  [#4037](https://github.com/telstra/open-kilda/pull/4037) Removed typo grouping from flr zero-downtime [**storm-topologies**]

### Improvements:
-  [#4035](https://github.com/telstra/open-kilda/pull/4035) Added logs to track ZK state 
-  [#4001](https://github.com/telstra/open-kilda/pull/4001) improve "cleanup" in roundTripLatencySpec [**tests**]
-  [#3557](https://github.com/telstra/open-kilda/pull/3557) Continuous ISL replug unit test [**tests**]
-  [#4006](https://github.com/telstra/open-kilda/pull/4006) add test for RTL a-switcl link + antiflap [**tests**]
-  [#4031](https://github.com/telstra/open-kilda/pull/4031) Improve some waits in func tests [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.83.0...v1.84.0).

### Affected Components:
network, otsdb, grpc, router, portstate, stats, nb, swmanager, nbworker, flow-hs, isllatency, reroute, ping

---

## v1.83.0 (04/02/2021)

### Features:
-  [#3970](https://github.com/telstra/open-kilda/pull/3970) Add design for flow SLA monitoring (Issue: [#3968](https://github.com/telstra/open-kilda/issues/3968)) [**docs**]
-  [#3981](https://github.com/telstra/open-kilda/pull/3981) Added missed server42 ZK nodes 
-  [#3982](https://github.com/telstra/open-kilda/pull/3982) Added Versioning for server42 storm stub 
-  [#3983](https://github.com/telstra/open-kilda/pull/3983) Fix zookeeper spout initialization steps 
-  [#3927](https://github.com/telstra/open-kilda/pull/3927) Adding feature to ping flows from switch detail (Issue: [#3842](https://github.com/telstra/open-kilda/issues/3842)) [**gui**]
-  [#3864](https://github.com/telstra/open-kilda/pull/3864) FL 0-Downtime PoC [**floodlight**]
-  [#3931](https://github.com/telstra/open-kilda/pull/3931) NB and GRPC zero downtime upgrade [**northbound**]
-  [#3880](https://github.com/telstra/open-kilda/pull/3880) Connected devices zero-downtime 
-  [#3965](https://github.com/telstra/open-kilda/pull/3965) add healthCheck to GrpcBaseSpecification [**tests**]
-  [#4030](https://github.com/telstra/open-kilda/pull/4030) Added missed zkNodes for NB, GRPC, server42 

### Bug Fixes:
-  [#3975](https://github.com/telstra/open-kilda/pull/3975) Prevention of decreasing the zk status below zero. 
-  [#3987](https://github.com/telstra/open-kilda/pull/3987) Fix second create flow loop request removes loop rules (Issue: [#3960](https://github.com/telstra/open-kilda/issues/3960)) 
-  [#3938](https://github.com/telstra/open-kilda/pull/3938) Fix history writing for a reroute operation. 
-  [#4012](https://github.com/telstra/open-kilda/pull/4012) Added waiting for messaging version from ZooKeeper 

### Improvements:
-  [#3971](https://github.com/telstra/open-kilda/pull/3971) Improvement of zero downtime doc [**docs**]
-  [#3980](https://github.com/telstra/open-kilda/pull/3980) Update readmes with more relevant information [**docs**][**tests**]
-  [#3992](https://github.com/telstra/open-kilda/pull/3992) Enable checkstyle run before unit tests in make unit 
-  [#3994](https://github.com/telstra/open-kilda/pull/3994) Make log4j config for tests omit persistence logs [**tests**]
-  [#3867](https://github.com/telstra/open-kilda/pull/3867) tests for rtl ISL and portDiscovery [**tests**]
-  [#3999](https://github.com/telstra/open-kilda/pull/3999) add test for partialUpdate + singleSwFlow [**tests**]
-  [#4013](https://github.com/telstra/open-kilda/pull/4013) Remove SimulatorTopology 
-  [#4017](https://github.com/telstra/open-kilda/pull/4017) Read signal for FL and ZKSpout 
-  [#4023](https://github.com/telstra/open-kilda/pull/4023) Increase storm slots for storm workers from 30 to 50 [**storm-topologies**]
-  [#4028](https://github.com/telstra/open-kilda/pull/4028) [Snyk] Security upgrade jinja2 from 2.10.1 to 2.11.3 
-  [#3967](https://github.com/telstra/open-kilda/pull/3967) Adding bfd session status in isl detail (Issue: [#3918](https://github.com/telstra/open-kilda/issues/3918)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.82.0...v1.83.0).

### Affected Components:
connected, grpc, fl, nb, gui, flow-hs

---

## v1.82.0 (27/01/2021)

### Improvements:
-  [#4007](https://github.com/telstra/open-kilda/pull/4007) Update base image to 20.04 
-  [#3991](https://github.com/telstra/open-kilda/pull/3991) Make sw validation stop ignoring default rules in other than 'proper' sections 

### Bug Fixes:
-  [#4012](https://github.com/telstra/open-kilda/pull/3012) Wait for kafka version

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.81.2...v1.82.0).

---

## v1.81.2 (22/01/2021)

### Bug Fixes:
-  [#4000](https://github.com/telstra/open-kilda/pull/4000) Fix one switch flow update [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.81.1...v1.81.2).

### Affected Components:
flow-hs

---

## v1.81.1 (21/01/2021)

### Bug Fixes:
-  [#3995](https://github.com/telstra/open-kilda/pull/3995) Increased port count in metadata from 128 to 4096 [**floodlight**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.81.0...v1.81.1).

### Affected Components:
fl

---

## v1.81.0 (18/01/2021)

### Features:
-  [#3844](https://github.com/telstra/open-kilda/pull/3844) Added ability to run Kilda in blue and green modes locally 
-  [#3910](https://github.com/telstra/open-kilda/pull/3910) Handle deserialization messages in KafkaVersioning [**storm-topologies**]
-  [#3986](https://github.com/telstra/open-kilda/pull/3986) Added parallelism properties for server42-control topology 
-  [#3962](https://github.com/telstra/open-kilda/pull/3962) Run make build-stable by default to run kilda 
-  [#3872](https://github.com/telstra/open-kilda/pull/3872) Added kafka message versioning with ZooKeeper [**floodlight**][**northbound**][**storm-topologies**]
-  [#3948](https://github.com/telstra/open-kilda/pull/3948) Added health check API for GRPC 

### Bug Fixes:
-  [#3917](https://github.com/telstra/open-kilda/pull/3917) Remove transient from abstract bolt config field 
-  [#3933](https://github.com/telstra/open-kilda/pull/3933) Added FL role and region to local FL logs [**floodlight**]
-  [#3936](https://github.com/telstra/open-kilda/pull/3936)  adding missing db script file for world map (Issue: [#3935](https://github.com/telstra/open-kilda/issues/3935)) [**gui**]
-  [#3877](https://github.com/telstra/open-kilda/pull/3877) Reimplement round trip discovery event processing [**storm-topologies**]
-  [#3949](https://github.com/telstra/open-kilda/pull/3949) Added retries for ZK connectiong during initialization 
-  [#3950](https://github.com/telstra/open-kilda/pull/3950) Fix for unit tests caused by system properties 

### Improvements:
-  [#3909](https://github.com/telstra/open-kilda/pull/3909)  Improvement in world map popup info for maximize and minimize the window (Issue: [#3855](https://github.com/telstra/open-kilda/issues/3855)) [**gui**]
-  [#3984](https://github.com/telstra/open-kilda/pull/3984) HotFix: Change default parallelism for otsdb and flow hs topologies [**storm-topologies**]
-  [#3925](https://github.com/telstra/open-kilda/pull/3925) Remove some unused classes in network topology [**storm-topologies**]
-  [#3928](https://github.com/telstra/open-kilda/pull/3928) Test/improvements [**tests**]
-  [#3929](https://github.com/telstra/open-kilda/pull/3929) Fix multi-reroute spec [**tests**]
-  [#3943](https://github.com/telstra/open-kilda/pull/3943) Minor fixes in zookeeper event handling. 
-  [#3947](https://github.com/telstra/open-kilda/pull/3947) Add topology_name to ContextMap for logging purposes 
-  [#3951](https://github.com/telstra/open-kilda/pull/3951) Added comments for creating ZK nodes [**docs**]
-  [#3952](https://github.com/telstra/open-kilda/pull/3952) Restore getZkTopoName() for topologies 
-  [#3953](https://github.com/telstra/open-kilda/pull/3953) Added default value for blue green mode 
-  [#3890](https://github.com/telstra/open-kilda/pull/3890) turn on the multiTable mode by default for func tests [**tests**]
-  [#3823](https://github.com/telstra/open-kilda/pull/3823) Lock GRPC low level particularity into a single wrapper 
-  [#3892](https://github.com/telstra/open-kilda/pull/3892) Fix swap endpoints for looped flows (Issue: [#3846](https://github.com/telstra/open-kilda/issues/3846)) 
-  [#3955](https://github.com/telstra/open-kilda/pull/3955) Restore make compile 
-  [#3956](https://github.com/telstra/open-kilda/pull/3956) Fix some cleanups in functional tests to increase test stability (Issue: [#3960](https://github.com/telstra/open-kilda/issues/3960)) [**tests**]
-  [#3957](https://github.com/telstra/open-kilda/pull/3957) Create KildaWallpaper.png [**docs**]
-  [#3898](https://github.com/telstra/open-kilda/pull/3898) Add flow-id for attendant traffic stat entries [**storm-topologies**]
-  [#3963](https://github.com/telstra/open-kilda/pull/3963) Create common ZK nodes by default 


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.80.0...v1.81.0).

### Affected Components:
stats, nb, fl, flow-hs, grpc, gui, otsdb, network

---

## v1.80.0 (22/12/2020)

### Features:
-  [#3843](https://github.com/telstra/open-kilda/pull/3843) Added round_trip_status to /v1/links API response [**northbound**][**storm-topologies**]
-  [#3907](https://github.com/telstra/open-kilda/pull/3907) Added zero downtime producers (Issue: [#3912](https://github.com/telstra/open-kilda/issues/3912)) [**floodlight**][**northbound**][**storm-topologies**]
-  [#3913](https://github.com/telstra/open-kilda/pull/3913) Moved ZkClient initialization out of constructor [**floodlight**][**northbound**][**storm-topologies**]
-  [#3915](https://github.com/telstra/open-kilda/pull/3915) Handle IllegalStateException during ZkClient initialization (Issue: [#3913](https://github.com/telstra/open-kilda/issues/3913)) [**floodlight**][**northbound**][**storm-topologies**]
-  [#3891](https://github.com/telstra/open-kilda/pull/3891) Increased storm slots for storm workers from 15 to 30 [**storm-topologies**]

### Bug Fixes:
-  [#3945](https://github.com/telstra/open-kilda/pull/3945) Zk Connection clean up (Issue: [#3926](https://github.com/telstra/open-kilda/issues/3926)) 

### Improvements:
-  [#3908](https://github.com/telstra/open-kilda/pull/3908) add test for #3906 [**tests**]
-  [#3779](https://github.com/telstra/open-kilda/pull/3779) adjust func test to work with hex cookies [**tests**]
-  [#3911](https://github.com/telstra/open-kilda/pull/3911) Docker base image for Lab-Api bumped to Ubuntu Focal [**tests**]
-  [#3912](https://github.com/telstra/open-kilda/pull/3912) Upgrade openvswitch to 2.14 after migrating to Ubuntu 20.04 [**tests**]
-  [#3850](https://github.com/telstra/open-kilda/pull/3850) Added logs for Update switch props request [**northbound**][**storm-topologies**]
-  [#3818](https://github.com/telstra/open-kilda/pull/3818) unignore grpc tests [**tests**]
-  [#3884](https://github.com/telstra/open-kilda/pull/3884) Use API to get round-trip status in functional tests [**tests**]
-  [#3889](https://github.com/telstra/open-kilda/pull/3889) add flowLoop tests for singleSwFlow [**tests**]
-  [#3903](https://github.com/telstra/open-kilda/pull/3903) Reorder switch events inside network topology [**storm-topologies**]
-  [#3899](https://github.com/telstra/open-kilda/pull/3899) refactor vxlan related tests [**tests**]
-  [#3837](https://github.com/telstra/open-kilda/pull/3837) Prevent updating of time-modify on property change with the same value. [**storm-topologies**]
-  [#3803](https://github.com/telstra/open-kilda/pull/3803) Introduce fine tuning for storm topologies [**configuration**][**storm-topologies**]
-  [#3916](https://github.com/telstra/open-kilda/pull/3916) Zookeeper connection refresh with default retry interval [**floodlight**][**northbound**][**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.79.0...v1.80.0).

### Affected Components:
nbworker, ping, network, isllatency, swmanager, stats, nb, flow-hs, grpc, connected, router, stats-router, portstate, otsdb, reroute, fl

---

## v1.79.0 (07/12/2020)

### Features:
-  [#3859](https://github.com/telstra/open-kilda/pull/3859) Zero downtime
-  [#3860](https://github.com/telstra/open-kilda/pull/3860) Zero downtime version

### Bug Fixes:
-  [#3875](https://github.com/telstra/open-kilda/pull/3875) Remove corrupted global retry in flow create FSM
-  [#3834](https://github.com/telstra/open-kilda/pull/3834) Fix GRPC speaker rest api error handling (Issue: [#3754](https://github.com/telstra/open-kilda/issues/3754))
-  [#3902](https://github.com/telstra/open-kilda/pull/3902) Fix develop compile time error

### Improvements:
-  [#3897](https://github.com/telstra/open-kilda/pull/3897) ignore tests according to #3896 [**tests**]
-  [#3873](https://github.com/telstra/open-kilda/pull/3873) Fix flow loops for Q-in-Q (Issue: [#3846](https://github.com/telstra/open-kilda/issues/3846)) [**floodlight**]
-  [#3874](https://github.com/telstra/open-kilda/pull/3874) extend connectedВevices spec [**tests**]
-  [#3876](https://github.com/telstra/open-kilda/pull/3876) add test for installing/deleting the server42_turning rule [**tests**]
-  [#3878](https://github.com/telstra/open-kilda/pull/3878) Fix flow loops for single switch flows (Issues: [#3846](https://github.com/telstra/open-kilda/issues/3846) [#3873](https://github.com/telstra/open-kilda/issues/3873)) [**floodlight**]
-  [#3819](https://github.com/telstra/open-kilda/pull/3819) Move BFD session transition code out of FSM [**storm-topologies**]
-  [#3833](https://github.com/telstra/open-kilda/pull/3833) Removed legacy docker tag `full_build_number:-latest`
-  [#3900](https://github.com/telstra/open-kilda/pull/3900) refactor qinq spec(reduce amount of tests) [**tests**]
-  [#3668](https://github.com/telstra/open-kilda/pull/3668) improvements in func-test, enable server42 (Issue: [#3853](https://github.com/telstra/open-kilda/issues/3853)) [**tests**]

### Other changes:
-  [#3885](https://github.com/telstra/open-kilda/pull/3885) Add retries for db_migration in docker-compose [**configuration**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.78.0...v1.79.0).

### Affected Components:
fl, network, flow-hs, grpc

--

## v1.78.0 (25/11/2020)

### Features:
-  [#3835](https://github.com/telstra/open-kilda/pull/3835) Adding feature of auto-refresh in world map (Issue: [#3794](https://github.com/telstra/open-kilda/issues/3794)) [**gui**]

### Bug Fixes:
-  [#3560](https://github.com/telstra/open-kilda/pull/3560) Fix ISL behavior on round-trip discovery loss [**storm-topologies**]
-  [#3852](https://github.com/telstra/open-kilda/pull/3852) Fix create protected flow with max latency path computation strategy (Issue: [#3821](https://github.com/telstra/open-kilda/issues/3821))
-  [#3868](https://github.com/telstra/open-kilda/pull/3868) Fix reroute degraded flow (Issue: [#3826](https://github.com/telstra/open-kilda/issues/3826))

### Improvements:
-  [#3853](https://github.com/telstra/open-kilda/pull/3853) Install server 42 rule if RTT toggle is True (Issue: [#3871](https://github.com/telstra/open-kilda/issues/3871)) [**floodlight**][**storm-topologies**]
-  [#3829](https://github.com/telstra/open-kilda/pull/3829) ISL status transition visibility improvement [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.77.0...v1.78.0).

### Affected Components:
nbworker, network, gui, fl, flow-hs

---

## v1.77.0 (17/11/2020)

### Features:
-  [#3851](https://github.com/telstra/open-kilda/pull/3851) [Server42] Software timestamps
-  [#3787](https://github.com/telstra/open-kilda/pull/3787) Flow loop feature implementation [**api**][**floodlight**][**northbound**][**storm-topologies**]
-  [#3747](https://github.com/telstra/open-kilda/pull/3747) Rework design for flow loop feature [**docs**]
-  [#3828](https://github.com/telstra/open-kilda/pull/3828) [Server42] Show warning if dpdk interface is down

### Bug Fixes:
-  [#3854](https://github.com/telstra/open-kilda/pull/3854) Fix BFD logical port FSM action name [**storm-topologies**]

### Improvements:
-  [#3840](https://github.com/telstra/open-kilda/pull/3840) Add test for round-trip isl discovery in special cases [**tests**]
-  [#3781](https://github.com/telstra/open-kilda/pull/3781) Add test that reveals #3780 [**tests**]
-  [#3848](https://github.com/telstra/open-kilda/pull/3848) ignore test according to #3847 [**tests**]
-  [#3722](https://github.com/telstra/open-kilda/pull/3722) Add functional tests for bfd v2 api [**tests**]
-  [#3790](https://github.com/telstra/open-kilda/pull/3790) Add logging for fallbacks in fetching switches in data model [**storm-topologies**]
-  [#3816](https://github.com/telstra/open-kilda/pull/3816) fix flowRulesSpec according to server42FlowRtt feature [**tests**]
-  [#3822](https://github.com/telstra/open-kilda/pull/3822) Add func tests for protected flow + max_latency (Issue: [#3821](https://github.com/telstra/open-kilda/issues/3821)) [**tests**]
-  [#3824](https://github.com/telstra/open-kilda/pull/3824) improve envExtension (configure switch mode if cleanup = true) [**tests**]
-  [#3827](https://github.com/telstra/open-kilda/pull/3827) Refactor reroute tests to reveal #3826 [**tests**]
-  [#3830](https://github.com/telstra/open-kilda/pull/3830) Fight port down and discovery events race condition [**storm-topologies**]
-  [#3831](https://github.com/telstra/open-kilda/pull/3831) Removed legacy variable OK_TESTS="DISABLE_LOGIN"
-  [#3832](https://github.com/telstra/open-kilda/pull/3832) Removed legacy Entry_point from wfm make file
-  [#3838](https://github.com/telstra/open-kilda/pull/3838) ignore unstable test in swapEndpointSpec [**tests**]
-  [#3839](https://github.com/telstra/open-kilda/pull/3839) Wrap git commands for floodlight and loxigen in gradle


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.76.0...v1.77.0).

### Affected Components:
stats, stats-router, ping, network, nb, reroute, nbworker, otsdb, router, connected, flow-hs, isllatency, portstate, fl, swmanager


---

## v1.76.0 (29/10/2020)

### Features:
-  [#3712](https://github.com/telstra/open-kilda/pull/3712) Per ISL BFD properties [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#3812](https://github.com/telstra/open-kilda/pull/3812) [Server42] fix resources leak closes #3797

### Improvements:
-  [#3811](https://github.com/telstra/open-kilda/pull/3811) Add default zknode creation into Zookeeper
-  [#3813](https://github.com/telstra/open-kilda/pull/3813) Docker version prerequisite
-  [#3782](https://github.com/telstra/open-kilda/pull/3782) Bfd ports LCM [**storm-topologies**]

### Other changes:
-  [#3808](https://github.com/telstra/open-kilda/pull/3808) Add functional test for #3807 [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.75.0...v1.76.0).

### Affected Components:
nb, nbworker, network

---

## v1.75.0 (27/10/2020)

### Features:
-  [#3783](https://github.com/telstra/open-kilda/pull/3783) [Server42] LCM part 1 and 2
-  [#3753](https://github.com/telstra/open-kilda/pull/3753) Feature/switch location update (Issue: [#3733](https://github.com/telstra/open-kilda/issues/3733)) [**gui**]
-  [#3793](https://github.com/telstra/open-kilda/pull/3793) [Server42] stats prometheus

### Bug Fixes:
-  [#3784](https://github.com/telstra/open-kilda/pull/3784) Fix Issue in activity logs related to permissions (Issue: [#3773](https://github.com/telstra/open-kilda/issues/3773)) [**gui**]
-  [#3798](https://github.com/telstra/open-kilda/pull/3798) Fix for issue in gui db script execution for fresh deployment [**gui**]

### Improvements:
-  [#3777](https://github.com/telstra/open-kilda/pull/3777) OrientDB schema migration toolset
-  [#3759](https://github.com/telstra/open-kilda/pull/3759) [Snyk] Security upgrade urllib3 from 1.25.8 to 1.25.9
-  [#3796](https://github.com/telstra/open-kilda/pull/3796) add topology_dependent tag for grpc tests [**tests**]
-  [#3735](https://github.com/telstra/open-kilda/pull/3735) extend pathsSpec [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.74.1...v1.75.0).

### Affected Components:
gui, server42

---

## v1.74.1 (21/10/2020)

### Bug Fixes:
-  [#3693](https://github.com/telstra/open-kilda/pull/3693) Add an operations queue to the Reroute topology. (Issues: [#3140](https://github.com/telstra/open-kilda/issues/3140) [#3703](https://github.com/telstra/open-kilda/issues/3703)) [**storm-topologies**]
-  [#3792](https://github.com/telstra/open-kilda/pull/3792) Fixed incorrect constructor for FlowRerouteRequest (Issue: [#3598](https://github.com/telstra/open-kilda/issues/3598)) [**northbound**]

### Improvements:
-  [#3776](https://github.com/telstra/open-kilda/pull/3776) Run single orient db by default for local kilda [**tests**]
-  [#3785](https://github.com/telstra/open-kilda/pull/3785) update doc related to func test [**tests**]
-  [#3786](https://github.com/telstra/open-kilda/pull/3786) Added parallelism for Isl Latency Topology [**storm-topologies**]
-  [#3725](https://github.com/telstra/open-kilda/pull/3725) configure multiTable mode on hardware env via test framework [**tests**]
-  [#3791](https://github.com/telstra/open-kilda/pull/3791) Added storm parallelism option for Flow HS topology
-  [#3743](https://github.com/telstra/open-kilda/pull/3743) Reset BFD session status on start (Issue: [#3744](https://github.com/telstra/open-kilda/issues/3744)) [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.74.0...v1.74.1).

### Affected Components:
isllatency, flow-hs, nb, network, reroute

---

## v1.74.0 (13/10/2020)

### Features:
-  [#3731](https://github.com/telstra/open-kilda/pull/3731) Adding support to login with saml(UI will be available on https) (Issue: [#3715](https://github.com/telstra/open-kilda/issues/3715)) [**gui**]
-  [#3764](https://github.com/telstra/open-kilda/pull/3764) Add `max_latency_tier2` field to the Flow object (Issue: [#3740](https://github.com/telstra/open-kilda/issues/3740)) [**northbound**][**storm-topologies**]

### Improvements:
-  [#3755](https://github.com/telstra/open-kilda/pull/3755) ignore grpc tests according to #3754 [**tests**]
-  [#3756](https://github.com/telstra/open-kilda/pull/3756) fix CheckLoggingSpec [**tests**]
-  [#3758](https://github.com/telstra/open-kilda/pull/3758) update getDefaultCookies by server42 rules [**tests**]
-  [#3772](https://github.com/telstra/open-kilda/pull/3772) fix unstable tests [**tests**]
-  [#3775](https://github.com/telstra/open-kilda/pull/3775) adjust functional test for maxLatencyTier2 field [**tests**]

### Other changes:
-  [#3748](https://github.com/telstra/open-kilda/pull/3748) Add 'lockkeeper' tag to some tests [**tests**]
-  [#3769](https://github.com/telstra/open-kilda/pull/3769) Fix port props tests to respect 'rtl' ISLs [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.73.0...v1.74.0).

### Affected Components:
flow-hs, nb, gui, nbworker

---

## v1.73.1 (13/10/2020)

### Bug Fixes:
-  [#3778](https://github.com/telstra/open-kilda/pull/3778) Fix memory leaks in OrientDB sessions. [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.73.0...v1.73.1).

### Affected Components:
isllatency, reroute, portstate, network, stats, flow-hs, swmanager, ping, connected, stats-router, nb, nbworker, router, otsdb

---

## v1.73.0 (09/10/2020)

### Bug Fixes:
-  [#3761](https://github.com/telstra/open-kilda/pull/3761) Wrap delete BfdSession operations (BfdPortFsm) into a transaction. [**storm-topologies**]
-  [#3762](https://github.com/telstra/open-kilda/pull/3762) Fix creation of BfdSession without a discriminator. [**storm-topologies**]
-  [#3767](https://github.com/telstra/open-kilda/pull/3767) Fix FeatureToggles label in OrientDB init script.

### Improvements:
-  [#3433](https://github.com/telstra/open-kilda/pull/3433) Re-implement the data model for Tinkerpop-based persistence layer (Issues: [#940](https://github.com/telstra/open-kilda/issues/940))
-  [#3434](https://github.com/telstra/open-kilda/pull/3434) Implement Tinkerpop-based persistence layer (Issues: [#940](https://github.com/telstra/open-kilda/issues/940))
-  [#3508](https://github.com/telstra/open-kilda/pull/3508) Implement persistence layer for OrientDB (Issues: [#940](https://github.com/telstra/open-kilda/issues/940))

### Other changes:
-  [#3745](https://github.com/telstra/open-kilda/pull/3745) Add migration scripts for Neo4j to OrientDB (Issue: [#940](https://github.com/telstra/open-kilda/issues/940))
-  [#3765](https://github.com/telstra/open-kilda/pull/3765) Add single orient db local env setup option [**configuration**]
-  [#3766](https://github.com/telstra/open-kilda/pull/3766) Use custom OrientDB user (Kilda) for all-in-one configuration.

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.72.2...v1.73.0).

### Affected Components:
grpc, nbworker, router, stats-router, swmanager, fl, otsdb, stats, ping, reroute, neo4j, connected, portstate, flow-hs, isllatency, nb, orientdb, network

### Upgrade notes:

This version of OpenKilda supports only [OrientDB](https://www.orientdb.org/) as the storage. Neo4j is no longer supported.
It's required to deploy an OrientDB cluster and move the Kilda data to it.
The migration procedure is defined in [neo4j-to-orientdb-migration-steps](https://github.com/telstra/open-kilda/blob/v1.73.0/docs/design/solutions/data-storage-v2/migration-to-orientdb/neo4j-to-orientdb-migration-steps.md) / [#3745](https://github.com/telstra/open-kilda/pull/3745)  

---

## v1.72.2 (01/10/2020)

### Features:
-  [#3724](https://github.com/telstra/open-kilda/pull/3724) [Server42] Stats app reconnecting
-  [#3694](https://github.com/telstra/open-kilda/pull/3694) Add logical port type field for logical port create request (Issues: [#2461](https://github.com/telstra/open-kilda/issues/2461) [#2468](https://github.com/telstra/open-kilda/issues/2468))

### Bug Fixes:
-  [#3751](https://github.com/telstra/open-kilda/pull/3751) Allow processing grpc requests without kafka-key
-  [#3752](https://github.com/telstra/open-kilda/pull/3752) Fix GRPC stats response kafka topic
-  [#3721](https://github.com/telstra/open-kilda/pull/3721) [Server42] Add the ability to monitor flow connected to the same server42 instance (Issue: [#3695](https://github.com/telstra/open-kilda/issues/3695))

### Improvements:
-  [#3750](https://github.com/telstra/open-kilda/pull/3750) update log template for func_test [**tests**]
-  [#3738](https://github.com/telstra/open-kilda/pull/3738) Simplify BFD session management [**storm-topologies**]
-  [#3739](https://github.com/telstra/open-kilda/pull/3739) add tests for #3728 [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.72.1...v1.72.2).

### Affected Components:
network, grpc

---

## v1.72.1 (29/09/2020)

### Bug Fixes:
-  [#3746](https://github.com/telstra/open-kilda/pull/3746) Hotfix/world map issues (Issue: [#3742](https://github.com/telstra/open-kilda/issues/3742)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.72.0...v1.72.1).

### Affected Components:
gui

---

## v1.72.0 (28/09/2020)

### Features:
-  [#3728](https://github.com/telstra/open-kilda/pull/3728) update port/vlan should not cause reroute [**floodlight**][**tests**]
-  [#3730](https://github.com/telstra/open-kilda/pull/3730) Added encapsulation_type and path_computation_strategy to /v1/network/paths API [**api**][**northbound**][**storm-topologies**]


### Improvements:
-  [#3709](https://github.com/telstra/open-kilda/pull/3709) Cleanup outdated kafka-topic references
-  [#3726](https://github.com/telstra/open-kilda/pull/3726) add template functional-tests.log4j2.toml [**tests**]
-  [#3698](https://github.com/telstra/open-kilda/pull/3698) Fix gui build
-  [#3699](https://github.com/telstra/open-kilda/pull/3699) Debug logging toggle for storm topologies
-  [#3741](https://github.com/telstra/open-kilda/pull/3741) Hex view for switch field representing cookies [**api**][**northbound**]

### Other changes:
-  [#3713](https://github.com/telstra/open-kilda/pull/3713) Update functional tests. minor improvements [**tests**]
-  [#3460](https://github.com/telstra/open-kilda/pull/3460) Document solutions for migration to another data storage (Issue: [#940](https://github.com/telstra/open-kilda/issues/940)) [**docs**]
-  [#3705](https://github.com/telstra/open-kilda/pull/3705) Add design for flow loop feature [**docs**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.71.1...v1.72.0).

### Affected Components:
gui, fl, flow-hs, nb, nbworker

---

## v1.71.1 (23/09/2020)

### Bug Fixes:
-  [#3737](https://github.com/telstra/open-kilda/pull/3737) Hotfix/world map cluster cache issue (Issue: [#3736](https://github.com/telstra/open-kilda/issues/3736)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.71.0...v1.71.1).

### Affected Components:
gui

---

## v1.71.0 (23/09/2020)

### Features:
-  [#3723](https://github.com/telstra/open-kilda/pull/3723) Feature to display topology on a world map (Issue: [#2372](https://github.com/telstra/open-kilda/issues/2372)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.70.2...v1.71.0).

### Affected Components:
gui

---

## v1.70.2 (10/09/2020)

### Bug Fixes:
-  [#3711](https://github.com/telstra/open-kilda/pull/3711) Fix validation of server42 ingress rules (Issue: [#3673](https://github.com/telstra/open-kilda/issues/3673)) [**storm-topologies**]

### Improvements:
-  [#3700](https://github.com/telstra/open-kilda/pull/3700) Skip excess paths in validation for server42 rules [**storm-topologies**]
-  [#3716](https://github.com/telstra/open-kilda/pull/3716) Add confd key in order to change space used for logging [**floodlight**]

### Other changes:
-  [#3704](https://github.com/telstra/open-kilda/pull/3704) Update func tests to be able to reveal #3703 [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.70.1...v1.70.2).

### Affected Components:
fl, swmanager

---

## v1.70.1 (07/09/2020)

### Features:
-  [#3674](https://github.com/telstra/open-kilda/pull/3674) Use floodlightrouter sw tracking for stats requests routing [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#3708](https://github.com/telstra/open-kilda/pull/3708) Minor test fix for flow maxLatency history [**tests**]

### Improvements:
-  [#3680](https://github.com/telstra/open-kilda/pull/3680) check flow fields in flowHistory(state before/after) [**tests**]
-  [#3686](https://github.com/telstra/open-kilda/pull/3686) refactor tests(make sure that flowReroute is finished) [**tests**]
-  [#3687](https://github.com/telstra/open-kilda/pull/3687) remove workaround for #3677 [**tests**]
-  [#3702](https://github.com/telstra/open-kilda/pull/3702) max latency in ms for the api [**northbound**]
-  [#3707](https://github.com/telstra/open-kilda/pull/3707) More accurate handling of newrok dumps for region mappings [**storm-topologies**]
-  [#3691](https://github.com/telstra/open-kilda/pull/3691) Make BFD enable/disable API more obvious (Issue: [#2342](https://github.com/telstra/open-kilda/issues/2342)) [**storm-topologies**]
-  [#3706](https://github.com/telstra/open-kilda/pull/3706) Minor tests adjustments [**tests**]
-  [#3645](https://github.com/telstra/open-kilda/pull/3645) Update test framework to allow 2+ management floodlights for switch [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.70.0...v1.70.1).

### Affected Components:
network, router, nb, nbworker, fl

---

## v1.70.0 (26/08/2020)

### Bug Fixes:
-  [#3688](https://github.com/telstra/open-kilda/pull/3688) Fix null pointer exception during producing flow dump (Issue: [#3552](https://github.com/telstra/open-kilda/issues/3552))
-  [#3689](https://github.com/telstra/open-kilda/pull/3689) Filter out one switch flows from affected flows [**storm-topologies**]
-  [#3692](https://github.com/telstra/open-kilda/pull/3692) Ignore overprovisioned isl check for ignore_bw flows
-  [#3696](https://github.com/telstra/open-kilda/pull/3696) Fix reroutes for max_latency path computation strategy

### Improvements:
-  [#3690](https://github.com/telstra/open-kilda/pull/3690) Improve reroute error logging
-  [#3679](https://github.com/telstra/open-kilda/pull/3679) Retry reroute with max_latency strategy

### Other changes:
-  [#3652](https://github.com/telstra/open-kilda/pull/3652) -adding feature to display flows stats graph on ISL (Issue: [#3646](https://github.com/telstra/open-kilda/issues/3646)) [**gui**]
-  [#3684](https://github.com/telstra/open-kilda/pull/3684) Add retries for grpc calls in functional tests [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.69.0...v1.70.0).

### Affected Components:
flow-hs, gui, reroute

---

## v1.69.0 (13/08/2020)

### Features:
-  [#3623](https://github.com/telstra/open-kilda/pull/3623) Simultaneous switch availability in multiple regions [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#3650](https://github.com/telstra/open-kilda/pull/3650) Save cookie -> flowId data for each switch (Issue: [#3628](https://github.com/telstra/open-kilda/issues/3628)) [**storm-topologies**]
-  [#3682](https://github.com/telstra/open-kilda/pull/3682) Fix deleting MOVED ISLs (Issue: [#3677](https://github.com/telstra/open-kilda/issues/3677)) [**storm-topologies**]
-  [#3685](https://github.com/telstra/open-kilda/pull/3685) Use chunked messages for flow history response (Issue: [#3681](https://github.com/telstra/open-kilda/issues/3681)) [**northbound**][**storm-topologies**]
-  [#3651](https://github.com/telstra/open-kilda/pull/3651) Make flow Degraded after reroute if protected flow is Down (Issue: [#3648](https://github.com/telstra/open-kilda/issues/3648))
-  [#3675](https://github.com/telstra/open-kilda/pull/3675) Add check for the allocated paths to ensure that all ISLs are active on those paths (Issue: [#3665](https://github.com/telstra/open-kilda/issues/3665))
-  [#3678](https://github.com/telstra/open-kilda/pull/3678) Use correct values in flow dump before flow update (Issue: [#3577](https://github.com/telstra/open-kilda/issues/3577))

### Improvements:
-  [#3683](https://github.com/telstra/open-kilda/pull/3683) Multi-floodlight part2 (feature-merge) (Issues: [#3580](https://github.com/telstra/open-kilda/issues/3580) [#3623](https://github.com/telstra/open-kilda/issues/3623)) [**floodlight**][**storm-topologies**]
-  [#3655](https://github.com/telstra/open-kilda/pull/3655) Remove default controller role hack [**floodlight**]
-  [#3621](https://github.com/telstra/open-kilda/pull/3621) minor improvements in func-tests (Issue: [#3677](https://github.com/telstra/open-kilda/issues/3677)) [**tests**]
-  [#3659](https://github.com/telstra/open-kilda/pull/3659) extend PartialUpdateSpec [**tests**]

### Other changes:
-  [#3649](https://github.com/telstra/open-kilda/pull/3649) Add test that reveals issue with protected paths #3648 (Issue: [#3648](https://github.com/telstra/open-kilda/issues/3648)) [**tests**]
-  [#3580](https://github.com/telstra/open-kilda/pull/3580) Update FL-route design documents [**docs**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.68.1...v1.69.0).

### Affected Components:
ping, router, nb, nbworker, stats, flow-hs, fl, network

---

## v1.68.1 (10/08/2020)

### Features:
-  [#2746](https://github.com/telstra/open-kilda/pull/2746) Design for kilda apps

### Bug Fixes:
-  [#3660](https://github.com/telstra/open-kilda/pull/3660) Fix link deleting in the Network topology (Issue: [#3656](https://github.com/telstra/open-kilda/issues/3656)) [**storm-topologies**]
-  [#3664](https://github.com/telstra/open-kilda/pull/3664) Fix possible race condition in get flow by switch operation (Issue: [#3603](https://github.com/telstra/open-kilda/issues/3603)) [**storm-topologies**]

### Improvements:
-  [#3672](https://github.com/telstra/open-kilda/pull/3672) Add catch of an exception when the ISL controller is not found [**storm-topologies**]
-  [#3676](https://github.com/telstra/open-kilda/pull/3676) Build outside of git repo


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.68.0...v1.68.1).

### Affected Components:
network, nbworker

---

## v1.68.0 (03/08/2020)

### Features:
-  [#3604](https://github.com/telstra/open-kilda/pull/3604) Implementation of smart discovery feature. (Issue: [#3548](https://github.com/telstra/open-kilda/issues/3548)) [**storm-topologies**]

### Bug Fixes:
-  [#3654](https://github.com/telstra/open-kilda/pull/3654) Improve flow history dumps (Issue: [#3031](https://github.com/telstra/open-kilda/issues/3031))

### Improvements:
-  [#3653](https://github.com/telstra/open-kilda/pull/3653) Add missing fields to flow patch operation. [**northbound**][**storm-topologies**]
-  [#3657](https://github.com/telstra/open-kilda/pull/3657) improve cleanup in swapEndpointSpec [**tests**]
-  [#3666](https://github.com/telstra/open-kilda/pull/3666) Floodlight repo fetch tag insead branch
-  [#3640](https://github.com/telstra/open-kilda/pull/3640) refactor rest according to #3639 [**tests**]

### Other changes:
-  [#3576](https://github.com/telstra/open-kilda/pull/3576) Decrease amount of kafka bolts in flrouter [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.67.0...v1.68.0).

### Affected Components:
nb, flow-hs, router, nbworker, network

---

## v1.67.0 (29/07/2020)

### Features:
-  [#3641](https://github.com/telstra/open-kilda/pull/3641) Disable IPv6 on interfaces(VLAN) created by traffexam [**tests**]

### Improvements:
-  [#3569](https://github.com/telstra/open-kilda/pull/3569) Bump storm and kafka client libs (Issue: [#2977](https://github.com/telstra/open-kilda/issues/2977)) [**configuration**][**storm-topologies**]
-  [#3642](https://github.com/telstra/open-kilda/pull/3642) remove ignore annotation for "Traffic counters in ingress rule are reset on flow rerouting" (Issues: [#3641](https://github.com/telstra/open-kilda/issues/3641) [#3641](https://github.com/telstra/open-kilda/issues/3641)) [**tests**]
-  [#3644](https://github.com/telstra/open-kilda/pull/3644) minor fixes according to v1.66 [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.66.1...v1.67.0).

### Affected Components:
isllatency, swmanager, ping, reroute, nbworker, stats, grpc, stats-router, network, flow-hs, router, otsdb, connected, portstate

---

## v1.66.1 (27/07/2020)

### Bug Fixes:
-  [#3662](https://github.com/telstra/open-kilda/pull/3662) Fix for issue in updating vlan for Flow (Issue: [#3661](https://github.com/telstra/open-kilda/issues/3661)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.66.0...v1.66.1).

### Affected Components:
gui

---

## v1.66.0 (22/07/2020)

### Features:
-  [#3617](https://github.com/telstra/open-kilda/pull/3617) Add a filter by the flow status for the flow get operation (Issue: [#3615](https://github.com/telstra/open-kilda/issues/3615)) [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#3549](https://github.com/telstra/open-kilda/pull/3549) Correct calculataion of expected set vlan actions in flow validation (Issue: [#3077](https://github.com/telstra/open-kilda/issues/3077)) [**storm-topologies**]
-  [#3638](https://github.com/telstra/open-kilda/pull/3638) Fix kilda GUI cleanup issue on build (Issue: [#3637](https://github.com/telstra/open-kilda/issues/3637)) [**gui**]

### Improvements:
-  [#3602](https://github.com/telstra/open-kilda/pull/3602) add test for partialSwitchUpdate [**tests**]
-  [#3614](https://github.com/telstra/open-kilda/pull/3614) enable server42FlowRtt in feature toogle by default for func test [**tests**]
-  [#3620](https://github.com/telstra/open-kilda/pull/3620) fix "System does not create flow when reverse path has different bandwidth than forward path on the second link" [**tests**]
-  [#3632](https://github.com/telstra/open-kilda/pull/3632) ignore test according to #3627 [**tests**]
-  [#3592](https://github.com/telstra/open-kilda/pull/3592) Add more tests for rerouting pinned flows [**tests**]
-  [#3554](https://github.com/telstra/open-kilda/pull/3554) Add replug tests for round-trip case and no portDown case [**tests**]
-  [#3619](https://github.com/telstra/open-kilda/pull/3619) Add more verifications to MultiRerouteSpec [**tests**]
-  [#3624](https://github.com/telstra/open-kilda/pull/3624) Remove FlowReroteFact
-  [#3629](https://github.com/telstra/open-kilda/pull/3629) Add test that shows no-retry behavior after global timeout on operation [**tests**]
-  [#3630](https://github.com/telstra/open-kilda/pull/3630) Minor test adjustments [**tests**]
-  [#3631](https://github.com/telstra/open-kilda/pull/3631) Minor fixes in perf tests [**tests**]
-  [#3634](https://github.com/telstra/open-kilda/pull/3634) Improvement/flows fields update (Issue: [#3601](https://github.com/telstra/open-kilda/issues/3601)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.65.0...v1.66.0).

### Affected Components:
nb, gui, swmanager, nbworker

---

## v1.65.0 (14/07/2020)

### Features:
-  [#3594](https://github.com/telstra/open-kilda/pull/3594) Feature to filter down/Degraded flows and re-route them from flow list (Issue: [#3584](https://github.com/telstra/open-kilda/issues/3584)) [**gui**]
-  [#3548](https://github.com/telstra/open-kilda/pull/3548) Design for Smart discovery feature [**docs**]
-  [#3583](https://github.com/telstra/open-kilda/pull/3583) check status_info field in func tests [**tests**]

### Improvements:
-  [#3597](https://github.com/telstra/open-kilda/pull/3597) Add test for partial update of 'pinned' field [**tests**]
-  [#3599](https://github.com/telstra/open-kilda/pull/3599) Change format of History records in DB from ISO to epoch
-  [#3600](https://github.com/telstra/open-kilda/pull/3600) fix FlowDiversitySpec according to #3591 [**tests**]
-  [#3606](https://github.com/telstra/open-kilda/pull/3606) ignore test related to server42+opentsdb [**tests**]
-  [#3518](https://github.com/telstra/open-kilda/pull/3518) Suppress stale discovery requests on FL side [**floodlight**]

### Other changes:
-  [#3586](https://github.com/telstra/open-kilda/pull/3586) Use logstash for test logs [**tests**]
-  [#3587](https://github.com/telstra/open-kilda/pull/3587) Added max_count parameter into flow history API [**api**][**northbound**][**storm-topologies**]
-  [#3089](https://github.com/telstra/open-kilda/pull/3089) Do not emit reroute commands for path, where edge switches are inactive [**storm-topologies**]
-  [#3607](https://github.com/telstra/open-kilda/pull/3607) Add an ability to remove pop [**northbound**][**storm-topologies**]
-  [#3608](https://github.com/telstra/open-kilda/pull/3608) Add func tests for history max_count [**tests**]
-  [#3622](https://github.com/telstra/open-kilda/pull/3622) Feature/down flows report (Issue: [#3585](https://github.com/telstra/open-kilda/issues/3585)) [**gui**]
-  [#3496](https://github.com/telstra/open-kilda/pull/3496) Revert "QinQ limit capabilities" [**floodlight**][**storm-topologies**]
-  [#3626](https://github.com/telstra/open-kilda/pull/3626) Added limits to migration 1.25 for performance

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.64.2...v1.65.0).

### Affected Components:
reroute, fl, neo4j, swmanager, flow-hs, nb, nbworker, gui

### Upgrade notes:
Consider using the following migration scripts to update db:

- [1.25 migration-script](https://github.com/telstra/open-kilda/blob/v1.65.0/services/src/neo4j/migrations/1.25-change-type-of-flow-event-timestamp/1-change-type-of-history-timestamps-form-iso-to-epoch.xml)


In case of issues these rollback scripts should be executed:

- [1.25 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.65.0/services/src/neo4j/migrations/1.25-change-type-of-flow-event-timestamp/rollback.cql)

---

## v1.64.2 (10/07/2020)

### Bug Fixes:
-  [#3598](https://github.com/telstra/open-kilda/pull/3598) On reroute failure retry it with ignore bw [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.64.1...v1.64.2).

### Affected Components:
flow-hs, reroute

---

## v1.64.1 (08/07/2020)

### Bug Fixes:
-  [#3612](https://github.com/telstra/open-kilda/pull/3612) Hotfix for issue in flow list to get and display inventory flows (Issue: [#3611](https://github.com/telstra/open-kilda/issues/3611)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.64.0...v1.64.1).

### Affected Components:
gui

---

## v1.64.0 (06/07/2020)

### Features:
-  [#3589](https://github.com/telstra/open-kilda/pull/3589) Added `location` field to Switch DTO (Issue: [#3571](https://github.com/telstra/open-kilda/issues/3571)) [**northbound**]
-  [#3595](https://github.com/telstra/open-kilda/pull/3595) Added PATCH Switch operation for `location` and `pop` fields. (Issue: [#3572](https://github.com/telstra/open-kilda/issues/3572)) [**northbound**][**storm-topologies**]
-  [#3542](https://github.com/telstra/open-kilda/pull/3542) Feature to display isl and switch maintenance mode on list pages (Issues: [#3532](https://github.com/telstra/open-kilda/issues/3532) [#3533](https://github.com/telstra/open-kilda/issues/3533)) [**gui**]
-  [#3579](https://github.com/telstra/open-kilda/pull/3579) Add `status_info` field to Flow (Issue: [#3570](https://github.com/telstra/open-kilda/issues/3570))

### Bug Fixes:
-  [#3593](https://github.com/telstra/open-kilda/pull/3593) Fix merge pr issues
-  [#3541](https://github.com/telstra/open-kilda/pull/3541) Skip reroute requests for flows without affected path segment (Issue: [#3390](https://github.com/telstra/open-kilda/issues/3390)) [**storm-topologies**]
-  [#3562](https://github.com/telstra/open-kilda/pull/3562) Fix for authentication Xss and permission access issue  (Issue: [#3553](https://github.com/telstra/open-kilda/issues/3553)) [**gui**]
-  [#3574](https://github.com/telstra/open-kilda/pull/3574) One switch flows update status on switch events (Issue: [#1464](https://github.com/telstra/open-kilda/issues/1464)) [**storm-topologies**]
-  [#3578](https://github.com/telstra/open-kilda/pull/3578) Handle case when trying to update available bandwidth of non existent ISL (Issue: [#3255](https://github.com/telstra/open-kilda/issues/3255))

### Improvements:
-  [#3590](https://github.com/telstra/open-kilda/pull/3590) Add `pinned` flag to FlowPatchDto [**northbound**][**storm-topologies**]
-  [#3591](https://github.com/telstra/open-kilda/pull/3591) Forbid intentional reroutes for pinned flows [**storm-topologies**]
-  [#3537](https://github.com/telstra/open-kilda/pull/3537) Switch validation cleanup [**storm-topologies**]
-  [#3550](https://github.com/telstra/open-kilda/pull/3550) remove TODOs related to mapping [**tests**]
-  [#3556](https://github.com/telstra/open-kilda/pull/3556) minor improvements/fixes for func tests [**tests**]
-  [#3561](https://github.com/telstra/open-kilda/pull/3561) Add more tests to smoke_switches suite [**tests**]
-  [#3568](https://github.com/telstra/open-kilda/pull/3568) Add test that tries to create a single-sw flow with periodic pings [**tests**]
-  [#3581](https://github.com/telstra/open-kilda/pull/3581) Add group-id to history flow dump objects [**northbound**]
-  [#3582](https://github.com/telstra/open-kilda/pull/3582) Don't drop group id when updating flow without diverse_flowid param

### Other changes:
-  [#3588](https://github.com/telstra/open-kilda/pull/3588) Improve Delete ISL logs [**northbound**][**storm-topologies**]
-  [#3525](https://github.com/telstra/open-kilda/pull/3525) In functional tests where possible remove the VIRTUAL tag [**tests**]
-  [#3538](https://github.com/telstra/open-kilda/pull/3538) Server42 Part 3 Storm Control Topology [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.63.0...v1.64.0).

### Affected Components:
network, nb, swmanager, flow-hs, nbworker, gui, reroute

---

## v1.63.0 (24/06/2020)

### Bug Fixes:
-  [#3555](https://github.com/telstra/open-kilda/pull/3555) Raise moved discovery state priority [**storm-topologies**]

### Improvements:
-  [#3521](https://github.com/telstra/open-kilda/pull/3521) Repripritize reroute requests [**storm-topologies**]
-  [#3522](https://github.com/telstra/open-kilda/pull/3522) Fix log types for grpc speaker and server42
-  [#3526](https://github.com/telstra/open-kilda/pull/3526) Remove CopyField action from the VxLAN ingress rule. [**floodlight**][**storm-topologies**]
-  [#3529](https://github.com/telstra/open-kilda/pull/3529) Make COST_AND_AVAILABLE_BANDWIDTH a default PCE strategy.
-  [#3530](https://github.com/telstra/open-kilda/pull/3530) Added ping cache update for flow operations and check the `periodic_ping` flag for one switch flow. (Issue: [#3526](https://github.com/telstra/open-kilda/issues/3526))
-  [#3539](https://github.com/telstra/open-kilda/pull/3539) add assumeTrue for grpcStat test [**tests**]
-  [#3540](https://github.com/telstra/open-kilda/pull/3540) refactor depracated methods in func tests [**tests**]
-  [#3543](https://github.com/telstra/open-kilda/pull/3543) improve VxlanFlowV2Spec [**tests**]
-  [#3519](https://github.com/telstra/open-kilda/pull/3519) Bump kafka and storm client libs versions (Issue: [#3545](https://github.com/telstra/open-kilda/issues/3545)) [**configuration**][**northbound**][**storm-topologies**]

### Other changes:
-  [#3544](https://github.com/telstra/open-kilda/pull/3544) Minor stability adjustments in tests [**tests**]
-  [#3545](https://github.com/telstra/open-kilda/pull/3545) Revert "Bump kafka and storm client libs versions" (Issues: [#2977](https://github.com/telstra/open-kilda/issues/2977) [#3519](https://github.com/telstra/open-kilda/issues/3519)) [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.61.0...v1.63.0).

### Affected Components:
grpc, router, otsdb, connected, isllatency, stats, flow-hs, nbworker, stats-router, portstate, reroute, ping, swmanager, network, fl, nb

---

## v1.61.0 (05/06/2020)

### Features:
-  [#3500](https://github.com/telstra/open-kilda/pull/3500) Server42 Part 2 DPDK (Issue: [#3436](https://github.com/telstra/open-kilda/issues/3436))
-  [#3504](https://github.com/telstra/open-kilda/pull/3504) Server 42 RTT: Remove goto meter from server 42 ingress rule [**storm-topologies**]
-  [#3516](https://github.com/telstra/open-kilda/pull/3516) Server 42 RTT: enable on WB switches [**floodlight**]

### Bug Fixes:
-  [#3515](https://github.com/telstra/open-kilda/pull/3515) Remove undefined activeEnter action reference from IslFsm [**storm-topologies**]

### Improvements:
-  [#3520](https://github.com/telstra/open-kilda/pull/3520) Improve GRPC error messages
-  [#3527](https://github.com/telstra/open-kilda/pull/3527) fix qinq + vxlan [**tests**]
-  [#3465](https://github.com/telstra/open-kilda/pull/3465) Bump storm and kafka versions [**storm-topologies**]
-  [#3083](https://github.com/telstra/open-kilda/pull/3083) Prevent network topology from altering ISL status into offline region [**storm-topologies**]
-  [#3511](https://github.com/telstra/open-kilda/pull/3511) Delete Flow Topology (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291)) [**floodlight**][**storm-topologies**]
-  [#3512](https://github.com/telstra/open-kilda/pull/3512) Reduce logging level for unhandled input in Flow H&S Speaker Worker
-  [#3485](https://github.com/telstra/open-kilda/pull/3485) kildaGui security vulnerabilitiy fixes (Issue: [#3482](https://github.com/telstra/open-kilda/issues/3482)) [**gui**]

### Other changes:
-  [#3509](https://github.com/telstra/open-kilda/pull/3509) Increase reliability of OfFlowStatsMapper [**floodlight**]
-  [#3514](https://github.com/telstra/open-kilda/pull/3514) Adjustments to allow running tests in non-default situations [**tests**]
-  [#3517](https://github.com/telstra/open-kilda/pull/3517) Add mirror group validation [**floodlight**][**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.60.0...v1.61.0).

### Affected Components:
grpc, stats, network, router, flow, flow-hs, swmanager, fl, gui

---

## v1.60.0 (01/06/2020)

### Features:
-  [#3438](https://github.com/telstra/open-kilda/pull/3438) Introducing flow with 2 levels of VLAN tagging on endpoints [**floodlight**][**storm-topologies**]


### Improvements:
-  [#3494](https://github.com/telstra/open-kilda/pull/3494) Add tests for partial update v2 api [**tests**]
-  [#3499](https://github.com/telstra/open-kilda/pull/3499) Add proper history wait in tests after path swap [**tests**]
-  [#3505](https://github.com/telstra/open-kilda/pull/3505) Run checkstyle task in Travis builds
-  [#3506](https://github.com/telstra/open-kilda/pull/3506) add test to cover #3498 (Issue: [#3498](https://github.com/telstra/open-kilda/issues/3498)) [**tests**]
-  [#3510](https://github.com/telstra/open-kilda/pull/3510) fix tests according to 1.59.0 v [**tests**]
-  [#3484](https://github.com/telstra/open-kilda/pull/3484) Various test stability fixes [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.59.0...v1.60.0).

### Affected Components:
flow-hs, swmanager, fl

---

## v1.59.0 (26/05/2020)

### Features:
-  [#3462](https://github.com/telstra/open-kilda/pull/3462) Server 42 RTT: Added feature toggle (Issue: [#3454](https://github.com/telstra/open-kilda/issues/3454)) [**api**][**northbound**][**storm-topologies**]
-  [#3402](https://github.com/telstra/open-kilda/pull/3402) Server 42 RTT Ingress rule [**floodlight**]
-  [#3487](https://github.com/telstra/open-kilda/pull/3487) Server 42 RTT: Added single table mode (Issue: [#3462](https://github.com/telstra/open-kilda/issues/3462)) [**storm-topologies**]
-  [#3454](https://github.com/telstra/open-kilda/pull/3454) Server 42 RTT: push/pop vlan (Issue: [#3402](https://github.com/telstra/open-kilda/issues/3402)) [**floodlight**][**storm-topologies**]
-  [#3227](https://github.com/telstra/open-kilda/pull/3227) Model update for kilda applications
-  [#3490](https://github.com/telstra/open-kilda/pull/3490) Add "cost and available bandwidth" path computation strategy [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#3448](https://github.com/telstra/open-kilda/pull/3448) Increase accuracy extra OF commands produces for ingress flow segment (Issue: [#3446](https://github.com/telstra/open-kilda/issues/3446)) [**floodlight**]
-  [#3401](https://github.com/telstra/open-kilda/pull/3401) Allowed flow deletion when there are no flow encapsulation resources (Issue: [#3256](https://github.com/telstra/open-kilda/issues/3256))
-  [#3486](https://github.com/telstra/open-kilda/pull/3486) Disable port discovery race condition removal (Issue: [#3474](https://github.com/telstra/open-kilda/issues/3474)) [**storm-topologies**]
-  [#3237](https://github.com/telstra/open-kilda/pull/3237) Fixed allocation of a protected path when updating a flow via APIv2. (Issue: [#3033](https://github.com/telstra/open-kilda/issues/3033))
-  [#3501](https://github.com/telstra/open-kilda/pull/3501) Fixed unmapped props and buildIngressOnly method
-  [#3502](https://github.com/telstra/open-kilda/pull/3502) Add sub second timestamp support to FlowRttMetricGenBolt

### Improvements:
-  [#3456](https://github.com/telstra/open-kilda/pull/3456) Changed alert level of mapstruct warning "unmappedTargetField" from WARN to ERROR (Issue: [#3276](https://github.com/telstra/open-kilda/issues/3276)) [**floodlight**][**northbound**]
-  [#3459](https://github.com/telstra/open-kilda/pull/3459) Fix NPE in H&S reroute and update thrown for a flow with no paths (Issues: [#2954](https://github.com/telstra/open-kilda/issues/2954) [#3028](https://github.com/telstra/open-kilda/issues/3028) [#3467](https://github.com/telstra/open-kilda/issues/3467))
-  [#3473](https://github.com/telstra/open-kilda/pull/3473) Add fields to 'PATCH /flows/{flow-id}/' (Issue: [#3444](https://github.com/telstra/open-kilda/issues/3444)) [**northbound**][**storm-topologies**]
-  [#3495](https://github.com/telstra/open-kilda/pull/3495) Organize sw features detection tests [**tests**]
-  [#3432](https://github.com/telstra/open-kilda/pull/3432) Dispatch v1 flow create to flow hs [**api**][**northbound**]
-  [#3450](https://github.com/telstra/open-kilda/pull/3450) Minor housekeeping in network topology [**storm-topologies**]
-  [#3372](https://github.com/telstra/open-kilda/pull/3372) Flow HS changes for migrating swap endpoints (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291)) [**storm-topologies**]
-  [#3493](https://github.com/telstra/open-kilda/pull/3493) Northbound V1 requests redirected to FlowHSTopology (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291)) [**northbound**]
-  [#3498](https://github.com/telstra/open-kilda/pull/3498) Redirect reroute requests to FlowHSTopology (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291)) [**storm-topologies**]
-  [#3377](https://github.com/telstra/open-kilda/pull/3377) Added swap endpoints FSM in the Flow HS Topology (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291)) [**storm-topologies**]

### Other changes:
-  [#3503](https://github.com/telstra/open-kilda/pull/3503) Fix the way how lab service starts traffexam [**tests**]
-  [#3447](https://github.com/telstra/open-kilda/pull/3447) Refactor test that disconnects fl region [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.58.0...v1.59.0).

### Affected Components:
neo4j, flow-hs, reroute, nb, network, swmanager, flow, fl, nbworker

### Upgrade notes:
Consider using the following migration scripts to update db:

- [1.24 migration-script](https://github.com/telstra/open-kilda/blob/v1.59.0/services/src/neo4j/migrations/1.24-flow-applications-indexes/1-add-applications-index.xml)

In case of issues these rollback scripts should be executed:

- [1.24 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.59.0/services/src/neo4j/migrations/1.24-flow-applications-indexes/rollback.cql)

---

## v1.58.0 (25/05/2020)

### Features:
-  [#3394](https://github.com/telstra/open-kilda/pull/3394) Server 42 RTT: Added Turning Rule (Issue: [#3415](https://github.com/telstra/open-kilda/issues/3415)) [**floodlight**][**storm-topologies**]
-  [#3436](https://github.com/telstra/open-kilda/pull/3436) Server42 Part 1 Control and Stats apps [**storm-topologies**]
-  [#3415](https://github.com/telstra/open-kilda/pull/3415) Server 42 RTT: Added Input Rule (Issue: [#3409](https://github.com/telstra/open-kilda/issues/3409)) [**floodlight**]

### Bug Fixes:
-  [#3471](https://github.com/telstra/open-kilda/pull/3471) Do not take into account orphaned paths while returning flows for end… [**storm-topologies**]
-  [#3481](https://github.com/telstra/open-kilda/pull/3481) Add missing dependency to port state topo (Issue: [#3461](https://github.com/telstra/open-kilda/issues/3461)) [**storm-topologies**]

### Improvements:
-  [#3457](https://github.com/telstra/open-kilda/pull/3457) improve "Newly discovered link gets cost and max bandwidth from link props" [**tests**]
-  [#3215](https://github.com/telstra/open-kilda/pull/3215) Allow flowhs speaker request retry for all error codes
-  [#3441](https://github.com/telstra/open-kilda/pull/3441) add test for #3430  [**tests**]
-  [#3409](https://github.com/telstra/open-kilda/pull/3409) Renamed server_42 options to server42 [**storm-topologies**]

### Other changes:
-  [#3361](https://github.com/telstra/open-kilda/pull/3361) Add inner vlan tag field into APIv2 requests/responses [**northbound**][**storm-topologies**]
-  [#3476](https://github.com/telstra/open-kilda/pull/3476) Workaround issue #3474 in tests [**tests**]
-  [#3452](https://github.com/telstra/open-kilda/pull/3452) Add test for errors during flow delete [**tests**]
-  [#3421](https://github.com/telstra/open-kilda/pull/3421) Fix build, add dependency for python wheels lib

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.57.0...v1.58.0).

### Affected Components:
nbworker, portstate, fl, nb, swmanager, flow, flow-hs, stats

---

## v1.57.0 (12/05/2020)

### Bug Fixes:
-  [#3453](https://github.com/telstra/open-kilda/pull/3453) Fixed the issue for flow list in isl (Issue: [#3451](https://github.com/telstra/open-kilda/issues/3451)) [**gui**]

### Improvements:
-  [#3458](https://github.com/telstra/open-kilda/pull/3458) Use round trip latency data for ISL alive confirmation (Issues: [#3175](https://github.com/telstra/open-kilda/issues/3175) [#3378](https://github.com/telstra/open-kilda/issues/3378) [#3407](https://github.com/telstra/open-kilda/issues/3407)) [**floodlight**][**storm-topologies**]
-  [#3426](https://github.com/telstra/open-kilda/pull/3426) Introduce bitfield enum types invalid entry [**floodlight**]
-  [#3407](https://github.com/telstra/open-kilda/pull/3407) Prioritize BFD alive status over round trip status (Issues: [#2353](https://github.com/telstra/open-kilda/issues/2353) [#3430](https://github.com/telstra/open-kilda/issues/3430)) [**storm-topologies**]
-  [#3442](https://github.com/telstra/open-kilda/pull/3442) Improvement/add switch meter permission (Issues: [#3420](https://github.com/telstra/open-kilda/issues/3420) [#3455](https://github.com/telstra/open-kilda/issues/3455)) [**gui**]
-  [#3443](https://github.com/telstra/open-kilda/pull/3443) use signleTable by default for func tests [**tests**]
-  [#3445](https://github.com/telstra/open-kilda/pull/3445) fix cleanup in "Unable to create flow with conflicting vlans" test [**tests**]
-  [#3449](https://github.com/telstra/open-kilda/pull/3449) fix swap protected path in multiTableFlow spec [**tests**]

### Other changes:
-  [#3202](https://github.com/telstra/open-kilda/pull/3202) Update README with project reorganization changes. [**docs**]
-  [#3247](https://github.com/telstra/open-kilda/pull/3247) Fix and prevent dependency classes conflict in src-java

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.56.1...v1.57.0).

### Affected Components:
fl, gui, network

---

## v1.56.1 (06/05/2020)

### Features:
-  [#3427](https://github.com/telstra/open-kilda/pull/3427) Feature/port discovery packets (Issue: [#3043](https://github.com/telstra/open-kilda/issues/3043)) [**gui**]


### Improvements:
-  [#3428](https://github.com/telstra/open-kilda/pull/3428) group cookie classes in single package
-  [#3405](https://github.com/telstra/open-kilda/pull/3405) add tests for link-delete spec according to #3268 [**tests**]
-  [#3437](https://github.com/telstra/open-kilda/pull/3437) add tidy annotation for negative tests [**tests**]
-  [#3373](https://github.com/telstra/open-kilda/pull/3373) Protected Path Swap in Flow HS topology [**northbound**]

### Other changes:
-  [#3429](https://github.com/telstra/open-kilda/pull/3429) Remove some old tests and add references to #2954, #3028 [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.56.0...v1.56.1).

### Affected Components:
nb, gui, flow-hs

---

## v1.56.0 (27/04/2020)

### Features:
-  [#3331](https://github.com/telstra/open-kilda/pull/3331) OF metadata bit manipulation toolset [**floodlight**]
-  [#3408](https://github.com/telstra/open-kilda/pull/3408) Server 42 RTT: Added Output rules [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#3396](https://github.com/telstra/open-kilda/pull/3396) Fixed flow corruption by reroute after this flow has been updated. (Issue: [#3338](https://github.com/telstra/open-kilda/issues/3338))
-  [#3406](https://github.com/telstra/open-kilda/pull/3406) Fix/flow diversity update issue (Issue: [#3404](https://github.com/telstra/open-kilda/issues/3404)) [**gui**]
-  [#3418](https://github.com/telstra/open-kilda/pull/3418) Fix CTE exceptions introduced by merge [**storm-topologies**]
-  [#3388](https://github.com/telstra/open-kilda/pull/3388) Fixed response timeout for flow read ops. (Issue: [#3384](https://github.com/telstra/open-kilda/issues/3384)) [**storm-topologies**]

### Improvements:
-  [#3397](https://github.com/telstra/open-kilda/pull/3397) Do not build grpc-stub if no_grpc_stub is true
-  [#3268](https://github.com/telstra/open-kilda/pull/3268) Add ISL busy check when deleting ISL. [**northbound**][**storm-topologies**]
-  [#3109](https://github.com/telstra/open-kilda/pull/3109) Obviously report operation timeout into flow CRUD operations (Issue: [#3249](https://github.com/telstra/open-kilda/issues/3249))
-  [#3403](https://github.com/telstra/open-kilda/pull/3403) set Tidy annotation for grpc tests [**tests**]
-  [#3313](https://github.com/telstra/open-kilda/pull/3313) Toolset for cookie bit manipulations [**floodlight**][**storm-topologies**]
-  [#3416](https://github.com/telstra/open-kilda/pull/3416) SpeakerRequestBuildContext refactoring [**floodlight**]

### Other changes:
-  [#3399](https://github.com/telstra/open-kilda/pull/3399) Add test that blinks switch during reroute (Issue: [#3398](https://github.com/telstra/open-kilda/issues/3398)) [**tests**]
-  [#3414](https://github.com/telstra/open-kilda/pull/3414) Put test reports to 'build' instead of 'target' [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.55.1...v1.56.0).

### Affected Components:
nbworker, stats, gui, flow, flow-hs, fl, connected, nb

## v1.55.1 (21/04/2020)

### Features:
-  [#3366](https://github.com/telstra/open-kilda/pull/3366) Added GRPC service stub [**tests**]
-  [#3371](https://github.com/telstra/open-kilda/pull/3371) Neo4j changes for migrating swap endpoints. (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291))

### Bug Fixes:
-  [#3395](https://github.com/telstra/open-kilda/pull/3395) Fixed incorrect Connected devices packet logging [**floodlight**]

### Improvements:
-  [#3393](https://github.com/telstra/open-kilda/pull/3393) Expose discovery latency as separate log field [**floodlight**]
-  [#3234](https://github.com/telstra/open-kilda/pull/3234) Make parallelism level configurable


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.55.0...v1.55.1).

### Affected Components:
neo4j, grpc, fl

---

## v1.55.0 (16/04/2020)

### Features:
-  [#3382](https://github.com/telstra/open-kilda/pull/3382) Added switch properties for server 42 Flow RTT feature [**storm-topologies**]
-  [#3383](https://github.com/telstra/open-kilda/pull/3383) Adding feature to display number of flows on switch list and port list (Issue: [#3368](https://github.com/telstra/open-kilda/issues/3368)) [**gui**]

### Bug Fixes:
-  [#3386](https://github.com/telstra/open-kilda/pull/3386) Fixed incorrect NoviflowSpecificFeature methods [**floodlight**]

### Improvements:
-  [#3364](https://github.com/telstra/open-kilda/pull/3364) Add test that reveals #3341 [**tests**]
-  [#3238](https://github.com/telstra/open-kilda/pull/3238) Cleanup outdated template variables
-  [#3273](https://github.com/telstra/open-kilda/pull/3273) [Snyk] Security upgrade urllib3 from 1.25.3 to 1.25.8
-  [#3385](https://github.com/telstra/open-kilda/pull/3385) Add retries due to #3384. Refactor an autoreroute test [**tests**]
-  [#3387](https://github.com/telstra/open-kilda/pull/3387) add missing import and fix knockoutSwitch operation in AutoRerouteV2Spec [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.54.0...v1.55.0).

### Affected Components:
gui, nbworker

---

## v1.54.0 (13/04/2020)

### Features:
-  [#3375](https://github.com/telstra/open-kilda/pull/3375) Feature/flow connected devices (Issue: [#3044](https://github.com/telstra/open-kilda/issues/3044)) [**gui**]
-  [#3359](https://github.com/telstra/open-kilda/pull/3359) add test for targetPathComputationStrategy and partialUpdate [**tests**]

### Bug Fixes:
-  [#3376](https://github.com/telstra/open-kilda/pull/3376) Remove potential reordering of OF events (Issue: [#3369](https://github.com/telstra/open-kilda/issues/3369)) [**floodlight**]

### Improvements:
-  [#3360](https://github.com/telstra/open-kilda/pull/3360) minor fixes in DefaultRulesSpec [**tests**]
-  [#3295](https://github.com/telstra/open-kilda/pull/3295) Flow reroute retry [**northbound**][**storm-topologies**]
-  [#3315](https://github.com/telstra/open-kilda/pull/3315) Extract ethernet packet body extractor into separate tool [**floodlight**]

### Other changes:
-  [#3332](https://github.com/telstra/open-kilda/pull/3332) Add ability to operate with qinq traffic into lab-service [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.53.0...v1.54.0).

### Affected Components:
fl, reroute, gui, nb, flow-hs

---

## v1.53.0 (10/04/2020)

### Features:
-  [#3205](https://github.com/telstra/open-kilda/pull/3205) TraffExam update for apps scenario
-  [#3340](https://github.com/telstra/open-kilda/pull/3340) add tests for grpc stats in opentsdb [**tests**]
-  [#3346](https://github.com/telstra/open-kilda/pull/3346) Added feature toggle for GRPC stats [**storm-topologies**]
-  [#3154](https://github.com/telstra/open-kilda/pull/3154) add tests for pop and protected path [**tests**]

### Bug Fixes:
-  [#3093](https://github.com/telstra/open-kilda/pull/3093) Fix the mess with flow endpoints during the flow update
-  [#3355](https://github.com/telstra/open-kilda/pull/3355) Added termination awaiting into GRPC session
-  [#3357](https://github.com/telstra/open-kilda/pull/3357) Get GRPC stats from Active switches [**storm-topologies**]
-  [#3362](https://github.com/telstra/open-kilda/pull/3362) Fix flow patch issues (Issues: [#3351](https://github.com/telstra/open-kilda/issues/3351) [#3354](https://github.com/telstra/open-kilda/issues/3354)) [**northbound**]
-  [#3363](https://github.com/telstra/open-kilda/pull/3363) Fix Toggle for GRPC PacketInOut stats [**storm-topologies**]
-  [#3370](https://github.com/telstra/open-kilda/pull/3370) Fix issue in dependency version issue in gui build [**gui**]
-  [#3378](https://github.com/telstra/open-kilda/pull/3378) Revert "Use round trip latency data for ISL alive confirmation" (Issue: [#3175](https://github.com/telstra/open-kilda/issues/3175)) [**storm-topologies**]

### Improvements:
-  [#3287](https://github.com/telstra/open-kilda/pull/3287) extract knockout/reviveSwitch into switchHelper (Issue: [#3244](https://github.com/telstra/open-kilda/issues/3244)) [**tests**]
-  [#3358](https://github.com/telstra/open-kilda/pull/3358) Updated multi-table pipeline spec
-  [#3301](https://github.com/telstra/open-kilda/pull/3301) minor fixes in README files according to new structure [**docs**]
-  [#3175](https://github.com/telstra/open-kilda/pull/3175) Use round trip latency data for ISL alive confirmation [**floodlight**][**storm-topologies**]

### Other changes:
-  [#3343](https://github.com/telstra/open-kilda/pull/3343) Adding feature to display sum of flows on port and switch list (Issues: [#3306](https://github.com/telstra/open-kilda/issues/3306) [#3307](https://github.com/telstra/open-kilda/issues/3307)) [**gui**]
-  [#3345](https://github.com/telstra/open-kilda/pull/3345) Improve test to reproduce race more often [**tests**]
-  [#3347](https://github.com/telstra/open-kilda/pull/3347) Rework makePathMorePreferable method to not conflict with protected paths [**tests**]
-  [#3348](https://github.com/telstra/open-kilda/pull/3348) Fix instabilities in time comparisons in ConnectedDevicesSpec [**tests**]
-  [#3349](https://github.com/telstra/open-kilda/pull/3349) Generate openflowj resourses inside docker with python2.7 [**configuration**]
-  [#3353](https://github.com/telstra/open-kilda/pull/3353) Fix perfomance tests run via gradle [**tests**]
-  [#3293](https://github.com/telstra/open-kilda/pull/3293) Implement flow read in v2 over nbworker (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291)) [**api**][**northbound**][**storm-topologies**]
-  [#3365](https://github.com/telstra/open-kilda/pull/3365) Feature/flows history and flow list updates [**gui**]
-  [#3367](https://github.com/telstra/open-kilda/pull/3367) Improvement in flow diversity graph to display current flow in it (Issue: [#3265](https://github.com/telstra/open-kilda/issues/3265)) [**gui**]
-  [#3314](https://github.com/telstra/open-kilda/pull/3314) Swap endpoints V2 design. (Issue: [#3291](https://github.com/telstra/open-kilda/issues/3291)) [**docs**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.52.0...v1.53.0).

### Affected Components:
flow-hs, network, grpc, fl, nbworker, stats, nb

---

## v1.52.0 (31/03/2020)

### Features:
-  [#3334](https://github.com/telstra/open-kilda/pull/3334) Added PacketInOutStats to stats Topology [**storm-topologies**]
-  [#3070](https://github.com/telstra/open-kilda/pull/3070) Design for Multiple ISLs on the same port (VLANs). (Issue: [#3053](https://github.com/telstra/open-kilda/issues/3053)) [**docs**]

### Bug Fixes:
-  [#3324](https://github.com/telstra/open-kilda/pull/3324) Improve get Flow DB requests for Connected Devices Service
-  [#3325](https://github.com/telstra/open-kilda/pull/3325) Ignore unmapped target properties in RequestedFlow

### Improvements:
-  [#3330](https://github.com/telstra/open-kilda/pull/3330) add missed else statement [**tests**]
-  [#3333](https://github.com/telstra/open-kilda/pull/3333) GRPC speaker messaging refactor
-  [#3231](https://github.com/telstra/open-kilda/pull/3231) Control docker-compose and makefile from confd [**configuration**]
-  [#3310](https://github.com/telstra/open-kilda/pull/3310) minor improvements in MetersSpec [**tests**]
-  [#3319](https://github.com/telstra/open-kilda/pull/3319) improve selecting swPair with diverse path in MultitableFlowsSpec [**tests**]
-  [#3320](https://github.com/telstra/open-kilda/pull/3320) improve grpc related tests [**tests**]
-  [#3352](https://github.com/telstra/open-kilda/pull/3352) Added extra checks for the null values in SimpleConversionCallback

### Other changes:
-  [#3336](https://github.com/telstra/open-kilda/pull/3336) Adjust tests to workaround issue #3335 [**tests**]
-  [#3337](https://github.com/telstra/open-kilda/pull/3337) Fix cleanups to always delete flows before bringing ports up [**tests**]
-  [#3342](https://github.com/telstra/open-kilda/pull/3342) Add comment in test regarding issue #3341 [**tests**]
-  [#3036](https://github.com/telstra/open-kilda/pull/3036) Support path computation strategy param in partial flow update (Issue: [#2894](https://github.com/telstra/open-kilda/issues/2894)) [**northbound**][**storm-topologies**]
-  [#3317](https://github.com/telstra/open-kilda/pull/3317) Teach lockkeeper to add latency between FL and switches [**tests**]
-  [#3323](https://github.com/telstra/open-kilda/pull/3323) Reduce trace size for PreviousTestFailedError [**tests**]
-  [#3327](https://github.com/telstra/open-kilda/pull/3327) Reorganize gui project (Issue: [#1137](https://github.com/telstra/open-kilda/issues/1137)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.51.2...v1.52.0).

### Affected Components:
connected, gui, nb, stats, nbworker, grpc, flow-hs

---

## v1.51.2 (23/03/2020)

### Features:
-  [#3308](https://github.com/telstra/open-kilda/pull/3308) Added PacketInOutStats to GRPC API

### Bug Fixes:
-  [#3305](https://github.com/telstra/open-kilda/pull/3305) Hotfix to display sum of flow ( sum of bandwidth of flows) on a port (Issue: [#3298](https://github.com/telstra/open-kilda/issues/3298)) [**gui**]

### Improvements:
-  [#3289](https://github.com/telstra/open-kilda/pull/3289) check traffic during rerouteV2 in multiTable mode [**tests**]

### Other changes:
-  [#3139](https://github.com/telstra/open-kilda/pull/3139) Add test for breaking protected path during swap (Issue: [#3140](https://github.com/telstra/open-kilda/issues/3140)) [**tests**]
-  [#3303](https://github.com/telstra/open-kilda/pull/3303) Minor tests adjustments [**tests**]
-  [#3304](https://github.com/telstra/open-kilda/pull/3304) Multiple updates to EnduranceV2Spec [**tests**]
-  [#3275](https://github.com/telstra/open-kilda/pull/3275) Unignore some functional tests due to related issues being fixed [**tests**]
-  [#3253](https://github.com/telstra/open-kilda/pull/3253) Skip unit tests in travis build if the sonar step is run.

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.51.1...v1.51.2).

### Affected Components:
grpc, gui

---

## v1.51.1 (17/03/2020)

### Bug Fixes:
-  [#3269](https://github.com/telstra/open-kilda/pull/3269) Fix the appearance of excess rules when flow update V2. (Issue: [#3266](https://github.com/telstra/open-kilda/issues/3266))
-  [#3271](https://github.com/telstra/open-kilda/pull/3271) Fixed PCE for diverse flow with not enough bandwidth ISL [**storm-topologies**]
-  [#3279](https://github.com/telstra/open-kilda/pull/3279) Fix NPE on response to unclosed FL session (Issue: [#3278](https://github.com/telstra/open-kilda/issues/3278)) [**floodlight**]
-  [#3280](https://github.com/telstra/open-kilda/pull/3280) Added configuration parameter `statistics.interval`. [**storm-topologies**]
-  [#3284](https://github.com/telstra/open-kilda/pull/3284) [Issue 3277] Fix V2 Flow Reroute after multiTable mode switching (Issue: [#3277](https://github.com/telstra/open-kilda/issues/3277))
-  [#3290](https://github.com/telstra/open-kilda/pull/3290) Do not ignore multiTable flags during RequestedFlow mapping [**storm-topologies**]
-  [#3186](https://github.com/telstra/open-kilda/pull/3186) Fixed resource allocation. (Issue: [#3047](https://github.com/telstra/open-kilda/issues/3047)) [**storm-topologies**]
-  [#3001](https://github.com/telstra/open-kilda/pull/3001) Fixed NB default password variable in NB confd template [**configuration**]
-  [#3258](https://github.com/telstra/open-kilda/pull/3258) Fixed switch sync during switch props update (Issue: [#3059](https://github.com/telstra/open-kilda/issues/3059)) [**storm-topologies**]

### Improvements:
-  [#3276](https://github.com/telstra/open-kilda/pull/3276) Clean up compile time warnings for kilda
-  [#3281](https://github.com/telstra/open-kilda/pull/3281) Minor adjustments in ConnectedDevicesSpec [**tests**]
-  [#3282](https://github.com/telstra/open-kilda/pull/3282) improve defaultFlowSpecs [**tests**]
-  [#3283](https://github.com/telstra/open-kilda/pull/3283) Fix call super hash codes and equals
-  [#3292](https://github.com/telstra/open-kilda/pull/3292) Add db indexes for flow history objects (Issue: [#3288](https://github.com/telstra/open-kilda/issues/3288))
-  [#3233](https://github.com/telstra/open-kilda/pull/3233) Remove unused code [**storm-topologies**]
-  [#3117](https://github.com/telstra/open-kilda/pull/3117) move verifyBurstSizeIsCorrect into switchHelper [**tests**]
-  [#3182](https://github.com/telstra/open-kilda/pull/3182) Add make target for creating virtual test topology [**tests**]

### Other changes:
-  [#2946](https://github.com/telstra/open-kilda/pull/2946) Updated migration steps for multi-table switch pipelines
-  [#3165](https://github.com/telstra/open-kilda/pull/3165) Rework unit-tests for flow H&S services [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.51.0...v1.51.1).

### Affected Components:
flow, nbworker, stats, router, fl, flow-hs

### Upgrade notes:
Consider using the following migration scripts to update db:

- [1.23 migration-script](https://github.com/telstra/open-kilda/blob/v1.51.1/services/src/neo4j/migrations/1.23-flow-history-indexes/1-add-flow-history-index.xml)

In case of issues these rollback scripts should be executed:

- [1.23 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.51.1/services/src/neo4j/migrations/1.23-flow-history-indexes/rollback.cql)

---

## v1.51.0 (11/03/2020)

### Features:
-  [#3203](https://github.com/telstra/open-kilda/pull/3203) ARP Part 6: Added shared rule (Issue: [#3118](https://github.com/telstra/open-kilda/issues/3118))
-  [#3142](https://github.com/telstra/open-kilda/pull/3142) ARP Part 4: Added ARP support into Floodlight (Issue: [#3118](https://github.com/telstra/open-kilda/issues/3118)) [**floodlight**]
-  [#3143](https://github.com/telstra/open-kilda/pull/3143) Enhancement/topology maintenance isl (Issue: [#3136](https://github.com/telstra/open-kilda/issues/3136)) [**gui**]
-  [#3144](https://github.com/telstra/open-kilda/pull/3144) ARP Part 5: Connected devices topology (Issue: [#3118](https://github.com/telstra/open-kilda/issues/3118)) [**floodlight**]
-  [#3217](https://github.com/telstra/open-kilda/pull/3217) Added ARP support to traff gens [**tests**]
-  [#3226](https://github.com/telstra/open-kilda/pull/3226) Add func tests for ARP connected devices [**tests**]
-  [#3177](https://github.com/telstra/open-kilda/pull/3177) Updated SwitchConnectedDevice db model
-  [#3119](https://github.com/telstra/open-kilda/pull/3119) ARP Part 1: Add ARP connected devices models (Issue: [#3118](https://github.com/telstra/open-kilda/issues/3118)) [**floodlight**][**northbound**]
-  [#3124](https://github.com/telstra/open-kilda/pull/3124) ARP Part 2: Update Switch Properties (Issue: [#3118](https://github.com/telstra/open-kilda/issues/3118)) [**storm-topologies**]
-  [#3187](https://github.com/telstra/open-kilda/pull/3187) Feature to add and update isl BFD flag (Issues: [#2883](https://github.com/telstra/open-kilda/issues/2883) [#2884](https://github.com/telstra/open-kilda/issues/2884)) [**gui**]
-  [#3188](https://github.com/telstra/open-kilda/pull/3188) Add flow reroute retry design [**docs**]
-  [#3129](https://github.com/telstra/open-kilda/pull/3129) Added LLDP shared rule V2 removing and installation (Issue: [#3056](https://github.com/telstra/open-kilda/issues/3056))
-  [#3134](https://github.com/telstra/open-kilda/pull/3134) ARP Part 3: Switch rules (Issue: [#3118](https://github.com/telstra/open-kilda/issues/3118)) [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#3272](https://github.com/telstra/open-kilda/pull/3272) Do not install ARP rules on WB switches [**floodlight**]
-  [#3209](https://github.com/telstra/open-kilda/pull/3209) Fix flow endpoints update via APIv2. (Issue: [#3049](https://github.com/telstra/open-kilda/issues/3049))
-  [#3016](https://github.com/telstra/open-kilda/pull/3016) Fix error message when switch not found (Issue: [#2906](https://github.com/telstra/open-kilda/issues/2906)) [**storm-topologies**]
-  [#3274](https://github.com/telstra/open-kilda/pull/3274) Added default value for detect connected devices in V2 API [**northbound**]
-  [#3224](https://github.com/telstra/open-kilda/pull/3224) Fixed display of `diverse_with` field in response via APIv2. (Issue: [#2701](https://github.com/telstra/open-kilda/issues/2701))
-  [#3243](https://github.com/telstra/open-kilda/pull/3243) Fix incorrect switch validation log message [**storm-topologies**]
-  [#3248](https://github.com/telstra/open-kilda/pull/3248) Fix data points duplicates for switch statistics (Issue: [#2801](https://github.com/telstra/open-kilda/issues/2801)) [**floodlight**]
-  [#3183](https://github.com/telstra/open-kilda/pull/3183) Issue 2885: Fixed getting Flows by Endpoint (Issue: [#2885](https://github.com/telstra/open-kilda/issues/2885)) [**storm-topologies**]
-  [#3259](https://github.com/telstra/open-kilda/pull/3259) Fixed creating/updating flow using the `max_latency` strategy. (Issue: [#3254](https://github.com/telstra/open-kilda/issues/3254)) [**storm-topologies**]

### Improvements:
-  [#3267](https://github.com/telstra/open-kilda/pull/3267) Fix MetersSpec to properly expect default meters in multitable mode [**tests**]
-  [#3141](https://github.com/telstra/open-kilda/pull/3141) LLDP cleanup: Remove unused methods, renamed SwitchLldpInfoData [**floodlight**][**storm-topologies**]
-  [#3082](https://github.com/telstra/open-kilda/pull/3082) Forbid to turn off multiTable property if there are flows with LLDP enabled [**storm-topologies**]
-  [#3041](https://github.com/telstra/open-kilda/pull/3041) Removed constraints and indexes for old flow connected devices model
-  [#3235](https://github.com/telstra/open-kilda/pull/3235) Make FlowThrottlingBolt stateless for storm. [**storm-topologies**]
-  [#2665](https://github.com/telstra/open-kilda/pull/2665) Use multi-region floodlight for local build. Update lock-keeper to use iptables [**tests**]
-  [#3250](https://github.com/telstra/open-kilda/pull/3250) improve SwitchValidationSpec(wait for meter) [**tests**]
-  [#3251](https://github.com/telstra/open-kilda/pull/3251) Make packet loss test hw-only again [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.50.0...v1.51.0).

### Affected Components:
nb, connected, fl, stats-router, flow-hs, swmanager, reroute, flow, neo4j, nbworker

### Upgrade notes:

Consider using the following migration scripts to update db:

- [1.21 migration-script](https://github.com/telstra/open-kilda/blob/v1.51.0/services/neo4j/migrations/1.21-remove-flow-connected-devices/1-remove-flow-connected-devices-index-and-constraint.xml)
- [1.22 migration-script](https://github.com/telstra/open-kilda/blob/v1.51.0/services/neo4j/migrations/1.22-switch-connected-devices-arp-indexes/1-add-switch-connected-devices-arp-index.xml)

In case of issues these rollback scripts should be executed:

- [1.22 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.51.0/services/neo4j/migrations/1.22-switch-connected-devices-arp-indexes/rollback.cql)
- [1.21 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.51.0/services/neo4j/migrations/1.21-remove-flow-connected-devices/rollback.cql)

---

## v1.50.0 (04/03/2020)

### Features:
-  [#3072](https://github.com/telstra/open-kilda/pull/3072) Add default meters validation (Issues: [#2969](https://github.com/telstra/open-kilda/issues/2969) [#3152](https://github.com/telstra/open-kilda/issues/3152)) [**floodlight**][**storm-topologies**]
-  [#3149](https://github.com/telstra/open-kilda/pull/3149) Added a reroute call when updating the maxLatency flow field. [**storm-topologies**]
-  [#3135](https://github.com/telstra/open-kilda/pull/3135) Added MAX_LATENCY PCE strategy.

### Bug Fixes:
-  [#3220](https://github.com/telstra/open-kilda/pull/3220) Added a filter in SwitchManagerTopology to check only the paths contained in flow. (Issue: [#3090](https://github.com/telstra/open-kilda/issues/3090)) [**storm-topologies**]

### Improvements:
-  [#3246](https://github.com/telstra/open-kilda/pull/3246) refactor tests according to 1.49 release [**tests**]
-  [#3221](https://github.com/telstra/open-kilda/pull/3221) improve MultitableFlowsSpec (Issue: [#3218](https://github.com/telstra/open-kilda/issues/3218)) [**tests**]
-  [#3092](https://github.com/telstra/open-kilda/pull/3092) Minor tweaks in tests according to default meters validation feature [**tests**]
-  [#3223](https://github.com/telstra/open-kilda/pull/3223) improve protectedPath specs [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.49.0...v1.50.0).

### Affected Components:
router, swmanager, fl, nbworker

---

## v1.49.0 (28/02/2020)

### Features:
-  [#3075](https://github.com/telstra/open-kilda/pull/3075) [Server 42] Design for Round Trip Ping rules [**docs**]
-  [#3121](https://github.com/telstra/open-kilda/pull/3121) Design for ARP connected devices on switch (Issue: [#3118](https://github.com/telstra/open-kilda/issues/3118)) [**docs**]
-  [#2876](https://github.com/telstra/open-kilda/pull/2876) Design for LLDP connected devices on switch (Issue: [#2917](https://github.com/telstra/open-kilda/issues/2917)) [**docs**]

### Bug Fixes:
-  [#3088](https://github.com/telstra/open-kilda/pull/3088) Fix the responses in swagger docs. (Issues: [#2382](https://github.com/telstra/open-kilda/issues/2382) [#2765](https://github.com/telstra/open-kilda/issues/2765)) [**northbound**]
-  [#3039](https://github.com/telstra/open-kilda/pull/3039) Fix MeterVerifyCommand to handle inaccurate meter bandwidth and burst (Issue: [#3027](https://github.com/telstra/open-kilda/issues/3027)) [**floodlight**]
-  [#3174](https://github.com/telstra/open-kilda/pull/3174) Correct handle empty affected isl set in reroute throttling [**storm-topologies**]
-  [#3055](https://github.com/telstra/open-kilda/pull/3055) Fixed issue in flow path stats graph  (Issue: [#3052](https://github.com/telstra/open-kilda/issues/3052)) [**gui**]
-  [#3069](https://github.com/telstra/open-kilda/pull/3069) Fixed flow ping for VXLAN flows [**floodlight**]

### Improvements:
-  [#3208](https://github.com/telstra/open-kilda/pull/3208) improve building procedure for kilda-base-lab-service image
-  [#3084](https://github.com/telstra/open-kilda/pull/3084) Fixed unit test for multi table [**tests**]
-  [#3153](https://github.com/telstra/open-kilda/pull/3153) Get rid of flow wrappers in Ping topology. [**storm-topologies**]
-  [#3155](https://github.com/telstra/open-kilda/pull/3155) Added a status tag to the flow.latency metric in the Ping topology. [**storm-topologies**]
-  [#3030](https://github.com/telstra/open-kilda/pull/3030) Imrovement for gzip compression in UI and browser (Issue: [#3029](https://github.com/telstra/open-kilda/issues/3029)) [**gui**]
-  [#3160](https://github.com/telstra/open-kilda/pull/3160) Return empty list instead of null on dumpMeters request [**floodlight**]
-  [#3161](https://github.com/telstra/open-kilda/pull/3161) Get rid of the FlowPair wrapper. [**storm-topologies**]
-  [#3163](https://github.com/telstra/open-kilda/pull/3163) Get rid of UnidirectionalFlow wrapper. [**storm-topologies**]
-  [#3169](https://github.com/telstra/open-kilda/pull/3169) Move transit table_id to 5 [**floodlight**]
-  [#3112](https://github.com/telstra/open-kilda/pull/3112) improvement: generate topology with one-way link only [**tests**]
-  [#3191](https://github.com/telstra/open-kilda/pull/3191) Expose OF transaction id into speaker logs [**floodlight**]
-  [#3198](https://github.com/telstra/open-kilda/pull/3198) test improvements [**tests**]

### Other changes:
-  [#3211](https://github.com/telstra/open-kilda/pull/3211) Fix running Storm topology locally (in dev environment)
-  [#3091](https://github.com/telstra/open-kilda/pull/3091) Now verify cleanup per feature rather than per spec [**tests**]
-  [#3222](https://github.com/telstra/open-kilda/pull/3222) Docker base image for Lab-Api bumped to Ubuntu Eoan
-  [#3192](https://github.com/telstra/open-kilda/pull/3192) Fix running func-tests and perf-tests in IDEA.
-  [#3190](https://github.com/telstra/open-kilda/pull/3190) Fix python3 dependency list [**docs**]
-  [#3193](https://github.com/telstra/open-kilda/pull/3193) Fix build.gradle - proper task reference in buildAndCopyArtifacts.
-  [#3066](https://github.com/telstra/open-kilda/pull/3066) Fixed security vulnerability in kildagui [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.48.2...v1.49.0).

### Affected Components:
nb, fl, gui, flow, reroute, nbworker, ping

---

## v1.48.1 (24/02/2020)

### Bug Fixes:
-  [#3204](https://github.com/telstra/open-kilda/pull/3204) Downgrade Kafka and Spring dependencies to pre-restructuring versions

### Improvements:
-  [#3213](https://github.com/telstra/open-kilda/pull/3213) Extend detaild of swmanager log messages
-  [#3219](https://github.com/telstra/open-kilda/pull/3219) Expose processed kafka record reference
-  [#3225](https://github.com/telstra/open-kilda/pull/3225) Accept custom Floodlight and Loxigen Git repositoies
-  [#3229](https://github.com/telstra/open-kilda/pull/3229) Lower the log level for unhandled tuples in H&S Flow topology

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.48.0...v1.48.1).

### Affected Components:
nb, swmanager

---

## v1.48.0 (13/02/2020)

### Bug Fixes:
-  [#3181](https://github.com/telstra/open-kilda/pull/3181) Restore RollbacksSpec [**tests**]
-  [#3195](https://github.com/telstra/open-kilda/pull/3195) Fix elasticsearch image build

### Improvements:
-  [#3178](https://github.com/telstra/open-kilda/pull/3178) reorganize-project-change-docker-context: Change docker build context…
-  [#3180](https://github.com/telstra/open-kilda/pull/3180) Test/fixes and improvements rebase [**tests**]
-  [#3095](https://github.com/telstra/open-kilda/pull/3095) Reorganize the project (Issue: [#1137](https://github.com/telstra/open-kilda/issues/1137))

### Other changes:
-  [#3172](https://github.com/telstra/open-kilda/pull/3172) Add gradle compile params
-  [#3189](https://github.com/telstra/open-kilda/pull/3189) Copy log config to lab-api container [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.47.2...v1.48.0).

---

## v1.47.2 (05/02/2020)

### Bug Fixes:
-  [#3162](https://github.com/telstra/open-kilda/pull/3162) Add reroute retries when transit rules can't be installed or verified (Issue: [#3128](https://github.com/telstra/open-kilda/issues/3128))
-  [#3156](https://github.com/telstra/open-kilda/pull/3156) Dump all switches to return visible switches [**floodlight**]
-  [#3132](https://github.com/telstra/open-kilda/pull/3132) Add test for #3128 (Issue: [#3128](https://github.com/telstra/open-kilda/issues/3128)) [**tests**]
-  [#3167](https://github.com/telstra/open-kilda/pull/3167) Fix reroute request filling in Flow H&S topology. (Issue: [#3128](https://github.com/telstra/open-kilda/issues/3128))


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.47.1...v1.47.2).

### Affected Components:
fl, flow-hs

---

## v1.47.1 (03/02/2020)

### Bug Fixes:
-  [#3158](https://github.com/telstra/open-kilda/pull/3158) Skip corrupted flow while doing periodic pings invalidation. [**storm-topologies**]
-  [#3133](https://github.com/telstra/open-kilda/pull/3133) Reroute affected flows on switch up event. (Issue: [#3131](https://github.com/telstra/open-kilda/issues/3131)) [**storm-topologies**]
-  [#3159](https://github.com/telstra/open-kilda/pull/3159) Fixed incorrect log message in case of v2 FlowDelete. [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.47.0...v1.47.1).

### Affected Components:
ping, reroute, network

---

## v1.47.0 (29/01/2020)

### Bug Fixes:
-  [#3148](https://github.com/telstra/open-kilda/pull/3148) Periodic pings perf [**northbound**][**storm-topologies**]
-  [#3006](https://github.com/telstra/open-kilda/pull/3006) Fix periodic pings (Issue: [#2873](https://github.com/telstra/open-kilda/issues/2873)) [**storm-topologies**]

### Improvements:
-  [#3138](https://github.com/telstra/open-kilda/pull/3138) Introduce PoP for the switch and take it into account in pce [**pce**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v.1.46.0...v1.47.0).

### Affected Components:
ping, nbworker, flow, nb, flow-hs

---

## v1.46.0 (24/01/2020)

### Features:
-  [#3022](https://github.com/telstra/open-kilda/pull/3022) Server42 Control application initial commit (Issues: [#1137](https://github.com/telstra/open-kilda/issues/1137) [#2998](https://github.com/telstra/open-kilda/issues/2998))
-  [#3024](https://github.com/telstra/open-kilda/pull/3024) Feature: LLDP on switch (Issue: [#2917](https://github.com/telstra/open-kilda/issues/2917)) [**floodlight**][**northbound**][**storm-topologies**]
-  [#2911](https://github.com/telstra/open-kilda/pull/2911) Switch LLDP 4: Added flow rules (Issues: [#2779](https://github.com/telstra/open-kilda/issues/2779) [#2827](https://github.com/telstra/open-kilda/issues/2827) [#2876](https://github.com/telstra/open-kilda/issues/2876) [#2917](https://github.com/telstra/open-kilda/issues/2917) [#2927](https://github.com/telstra/open-kilda/issues/2927)) [**floodlight**][**storm-topologies**]
-  [#2914](https://github.com/telstra/open-kilda/pull/2914) Switch LLDP 3: catching on ISL ports (Issues: [#2917](https://github.com/telstra/open-kilda/issues/2917) [#2918](https://github.com/telstra/open-kilda/issues/2918)) [**floodlight**]
-  [#2916](https://github.com/telstra/open-kilda/pull/2916) Switch LLDP 1: Isl rules (Issue: [#2917](https://github.com/telstra/open-kilda/issues/2917)) [**floodlight**][**northbound**]
-  [#2918](https://github.com/telstra/open-kilda/pull/2918) Switch LLDP 2: switch connected device models (Issues: [#2916](https://github.com/telstra/open-kilda/issues/2916) [#2917](https://github.com/telstra/open-kilda/issues/2917))

### Bug Fixes:
-  [#3014](https://github.com/telstra/open-kilda/pull/3014) Fix NullPointerException [**storm-topologies**]
-  [#2952](https://github.com/telstra/open-kilda/pull/2952) Fix validation of encapsulation_type in v2 FlowUpdate API (Issue: [#2937](https://github.com/telstra/open-kilda/issues/2937)) [**northbound**][**storm-topologies**]
-  [#3025](https://github.com/telstra/open-kilda/pull/3025) Fix removing customer port rule when flow H&S delete. (Issue: [#2971](https://github.com/telstra/open-kilda/issues/2971)) [**floodlight**]
-  [#2937](https://github.com/telstra/open-kilda/pull/2937) Handle invalid encapsulation_type in v2 FlowCreate API (Issues: [#2650](https://github.com/telstra/open-kilda/issues/2650) [#2952](https://github.com/telstra/open-kilda/issues/2952)) [**northbound**][**storm-topologies**]
-  [#3002](https://github.com/telstra/open-kilda/pull/3002) Fix error message when switch not exist (Issue: [#2905](https://github.com/telstra/open-kilda/issues/2905)) [**storm-topologies**]
-  [#3067](https://github.com/telstra/open-kilda/pull/3067) Fix fails of PacketServiceTest [**tests**]
-  [#3068](https://github.com/telstra/open-kilda/pull/3068) Fixed missing @Ignore import [**tests**]

### Improvements:
-  [#2886](https://github.com/telstra/open-kilda/pull/2886) refactor tets with getSwithcFlow to cover #2885 (Issue: [#2885](https://github.com/telstra/open-kilda/issues/2885)) [**tests**]
-  [#3023](https://github.com/telstra/open-kilda/pull/3023) Extend log message for missing rules [**floodlight**]
-  [#3026](https://github.com/telstra/open-kilda/pull/3026) minor improvements in tests [**tests**]
-  [#2986](https://github.com/telstra/open-kilda/pull/2986) Add test for devices interaction with default flow [**tests**]
-  [#3054](https://github.com/telstra/open-kilda/pull/3054) add tests for 3049 issue (Issue: [#3049](https://github.com/telstra/open-kilda/issues/3049)) [**tests**]
-  [#2927](https://github.com/telstra/open-kilda/pull/2927) Add func tests for detecting lldp connected devices per-switch (Issue: [#2914](https://github.com/telstra/open-kilda/issues/2914)) [**tests**]
-  [#3058](https://github.com/telstra/open-kilda/pull/3058) add test "Flow ping can detect a broken path for a vxlan flow on an intermediate switch" (Issue: [#3069](https://github.com/telstra/open-kilda/issues/3069)) [**tests**]
-  [#3060](https://github.com/telstra/open-kilda/pull/3060) ignore tests according to #3059 (Issue: [#3059](https://github.com/telstra/open-kilda/issues/3059)) [**tests**]
-  [#3061](https://github.com/telstra/open-kilda/pull/3061) delete FlowPriorityRerouteSpec [**tests**]
-  [#3063](https://github.com/telstra/open-kilda/pull/3063) improve flowHistorySpec, covers: 3031,3038 (Issues: [#3031](https://github.com/telstra/open-kilda/issues/3031) [#3038](https://github.com/telstra/open-kilda/issues/3038)) [**tests**]

### Other changes:
-  [#2951](https://github.com/telstra/open-kilda/pull/2951) Limit number of tables for ovs switches [**floodlight**]
-  [#3017](https://github.com/telstra/open-kilda/pull/3017) Ignore port history tests due to #3007 [**tests**]
-  [#3018](https://github.com/telstra/open-kilda/pull/3018) Add comment that single-switch flow pings are actually useless [**tests**]
-  [#3042](https://github.com/telstra/open-kilda/pull/3042) Minor fixes in functional tests [**tests**]
-  [#3057](https://github.com/telstra/open-kilda/pull/3057) Add more stats verifications to existing tests [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.45.2...v1.46.0).

### Affected Components:
router, nb, neo4j, flow-hs, nbworker, flow, fl, ping, connected

### Upgrade notes:

Consider using the following migration scripts to update db:

- [1.19 migration-script](https://github.com/telstra/open-kilda/blob/v1.46.0/services/neo4j/migrations/1.19-switch-lldp-property/1-set-switch-lldp-switchproperty-flag.xml)
- [1.20 migration-script](https://github.com/telstra/open-kilda/blob/v1.46.0/services/neo4j/migrations/1.20-switch-connected-devices/1-add-switch-connected-devices-index-and-constraint.xml)

In case of issues these rollback scripts should be executed:

- [1.20 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.46.0/services/neo4j/migrations/1.20-switch-connected-devices/rollback.cql)
- [1.19 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.46.0/services/neo4j/migrations/1.19-switch-lldp-property/rollback.cql)

---

## v1.45.2 (13/01/2020)

### Bug Fixes:
-  [#3107](https://github.com/telstra/open-kilda/pull/3107) Avoid reseting in progress flow status [**storm-topologies**]
-  [#3108](https://github.com/telstra/open-kilda/pull/3108) Fix double network failure handling [**northbound**][**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.45.1...v1.45.2).

### Affected Components:
nb, reroute, flow-hs

---

## v1.45.1 (30/12/2019)

### Bug Fixes:
-  [#2785](https://github.com/telstra/open-kilda/pull/2785) Reroute topology updates flow status for flows (Issue: [#2781](https://github.com/telstra/open-kilda/issues/2781)) [**storm-topologies**]
-  [#3086](https://github.com/telstra/open-kilda/pull/3086) Decrease parallelism for reply kafka spouts in flr [**storm-topologies**]
-  [#3062](https://github.com/telstra/open-kilda/pull/3062) Fix rollback in flow reroute [**storm-topologies**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.45.0...v1.45.1).

### Affected Components:
flow-hs, router, reroute

---

## v1.45.0 (10/12/2019)

### Features:
-  [#3013](https://github.com/telstra/open-kilda/pull/3013) Feature/switch delete [**gui**]
-  [#2910](https://github.com/telstra/open-kilda/pull/2910) Rework PCE (Issue: [#2894](https://github.com/telstra/open-kilda/issues/2894)) [**docs**][**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#3012](https://github.com/telstra/open-kilda/pull/3012) Change goToTable instruction for customer multi table flow (Issue: [#3010](https://github.com/telstra/open-kilda/issues/3010)) [**floodlight**]
-  [#2966](https://github.com/telstra/open-kilda/pull/2966) Fix northbound logging after update log4j to 2.11.0 [**storm-topologies**]
-  [#3020](https://github.com/telstra/open-kilda/pull/3020) Add DB migration for reworked PCE (Issue: [#2894](https://github.com/telstra/open-kilda/issues/2894))

### Improvements:
-  [#2985](https://github.com/telstra/open-kilda/pull/2985) Add cleanup verifier to check basic factors of a clean env [**tests**]
-  [#2990](https://github.com/telstra/open-kilda/pull/2990) Refactor IslCostSpec to test 'unstable isl' behavior [**tests**]
-  [#2958](https://github.com/telstra/open-kilda/pull/2958) add test for flows_reroute_using_default_encap_type feature toogle (Issue: [#2955](https://github.com/telstra/open-kilda/issues/2955)) [**tests**]
-  [#2926](https://github.com/telstra/open-kilda/pull/2926) Fix performance degradation in FL Kafka Producer [**floodlight**]
-  [#2992](https://github.com/telstra/open-kilda/pull/2992) Refactor multi-reroute spec to work with bigger amount of flows [**tests**]
-  [#2994](https://github.com/telstra/open-kilda/pull/2994) Update all func tests to use v2 API wherever possible (Issue: [#2921](https://github.com/telstra/open-kilda/issues/2921)) [**tests**]
-  [#2961](https://github.com/telstra/open-kilda/pull/2961) update See annotation for syncSwitch spec [**tests**]
-  [#2999](https://github.com/telstra/open-kilda/pull/2999) Remove outdated/unused config option floodlight.request.timeout
-  [#2997](https://github.com/telstra/open-kilda/pull/2997) ignore fucn tests according to bugs [**tests**]
-  [#3004](https://github.com/telstra/open-kilda/pull/3004) Revise flow priority reroute test with respect to h&s [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.44.1...v1.45.0).

### Affected Components:
flow-hs, fl, reroute, gui, flow, nbworker, nb

### Upgrade notes:
Consider using the following migration scripts to update db:

- [1.18 migration-script](https://github.com/telstra/open-kilda/blob/v1.45.0/services/neo4j/migrations/1.18-path-computation-strategy/1-path-computation-strategy.xml)

In case of issues these rollback scripts should be executed:

- [1.18 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.45.0/services/neo4j/migrations/1.18-path-computation-strategy/rollback.cql)

---

## v1.44.1 (09/12/2019)

### Bug Fixes:
-  [#3005](https://github.com/telstra/open-kilda/pull/3005) Fix flow segment validation relaxing set field action match [**floodlight**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.44.0...v1.44.1).

### Affected Components:
fl

---

## v1.44.0 (04/12/2019)

### Features:
-  [#2818](https://github.com/telstra/open-kilda/pull/2818) Add Server 42 Design [**api**][**docs**]
-  [#2900](https://github.com/telstra/open-kilda/pull/2900) add tests for multiTable feature [**tests**]

### Bug Fixes:
-  [#2963](https://github.com/telstra/open-kilda/pull/2963) Fix for single switch flow (Issue: [#2947](https://github.com/telstra/open-kilda/issues/2947)) [**storm-topologies**]
-  [#2964](https://github.com/telstra/open-kilda/pull/2964) Fix for parse error in switch props update (Issue: [#2957](https://github.com/telstra/open-kilda/issues/2957)) [**storm-topologies**]
-  [#2991](https://github.com/telstra/open-kilda/pull/2991) Fix flow segment verify issue on OF1.2 switches [**floodlight**]
-  [#2993](https://github.com/telstra/open-kilda/pull/2993) Fix false-negative meter's validation [**floodlight**]

### Improvements:
-  [#2945](https://github.com/telstra/open-kilda/pull/2945) Valdate Switch Props against supported features (Issues: [#2932](https://github.com/telstra/open-kilda/issues/2932) [#2941](https://github.com/telstra/open-kilda/issues/2941)) [**storm-topologies**]
-  [#2826](https://github.com/telstra/open-kilda/pull/2826) Extend local execution time for storm topologies [**storm-topologies**]
-  [#2774](https://github.com/telstra/open-kilda/pull/2774) Extend speaker commands [**floodlight**]
-  [#2967](https://github.com/telstra/open-kilda/pull/2967) Improve error handling and logging in H&S FSMs
-  [#2835](https://github.com/telstra/open-kilda/pull/2835) Update some configuration for local setup to speed up test execution [**configuration**][**docs**][**tests**]
-  [#2965](https://github.com/telstra/open-kilda/pull/2965) Minor stability tweaks in functional tests [**tests**]
-  [#2973](https://github.com/telstra/open-kilda/pull/2973) Minor stability fixes in functional tests [**tests**]
-  [#2978](https://github.com/telstra/open-kilda/pull/2978) Fix stability of port history test [**tests**]
-  [#2984](https://github.com/telstra/open-kilda/pull/2984) Ignore test that fails due to #2983 [**tests**]

### Other changes:
-  [#2970](https://github.com/telstra/open-kilda/pull/2970) Minor test updates related to V2 migration [**tests**]
-  [#2979](https://github.com/telstra/open-kilda/pull/2979) Ignore port stats test as being unstable [**tests**]
-  [#2929](https://github.com/telstra/open-kilda/pull/2929) Feature/flow diversity and network path (Issues: [#2283](https://github.com/telstra/open-kilda/issues/2283) [#2371](https://github.com/telstra/open-kilda/issues/2371) [#2373](https://github.com/telstra/open-kilda/issues/2373)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.43.1...v1.44.0).

### Affected Components:
flow-hs, fl, swmanager, nbworker

---

## v1.43.1 (03/12/2019)

### Bug Fixes:
-  [#2974](https://github.com/telstra/open-kilda/pull/2974) Handles reroute failures caused by over-provisioning  (Issue: [#2925](https://github.com/telstra/open-kilda/issues/2925))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.43.0...v1.43.1).

### Affected Components:
flow-hs

---

## v1.43.0 (27/11/2019)

### Features:
-  [#2892](https://github.com/telstra/open-kilda/pull/2892) Implement H&S update (Issue: [#2869](https://github.com/telstra/open-kilda/issues/2869)) [**storm-topologies**]
-  [#2869](https://github.com/telstra/open-kilda/pull/2869) Implement H&S delete [**storm-topologies**]

### Bug Fixes:
-  [#2923](https://github.com/telstra/open-kilda/pull/2923) PCE bug fix (Issue: [#2904](https://github.com/telstra/open-kilda/issues/2904)) [**storm-topologies**]
-  [#2931](https://github.com/telstra/open-kilda/pull/2931) Fix stability of SwitchPropertiesSpec [**tests**]
-  [#2936](https://github.com/telstra/open-kilda/pull/2936) Improve switch properties validation (Issue: [#2889](https://github.com/telstra/open-kilda/issues/2889)) [**northbound**][**storm-topologies**]

### Improvements:
-  [#2944](https://github.com/telstra/open-kilda/pull/2944) Allow some tests to be marked as those with perfect cleanup (Issue: [#2943](https://github.com/telstra/open-kilda/issues/2943)) [**tests**]
-  [#2950](https://github.com/telstra/open-kilda/pull/2950) minor improve in flowCrud specs [**tests**]
-  [#2953](https://github.com/telstra/open-kilda/pull/2953) improve swapEndpointSpec [**tests**]
-  [#2959](https://github.com/telstra/open-kilda/pull/2959) Added envs for regions and roles for FL log files in JSON [**configuration**]
-  [#2903](https://github.com/telstra/open-kilda/pull/2903) check that system doesn't ignore encapsulationType when ignoreBandwidth=true [**tests**]
-  [#2909](https://github.com/telstra/open-kilda/pull/2909) FloodlightRouter parallelism tune [**storm-topologies**]
-  [#2920](https://github.com/telstra/open-kilda/pull/2920) Mark v1 specs that have v2 alternative as low priority [**tests**]
-  [#2872](https://github.com/telstra/open-kilda/pull/2872) improve portHistoryspec [**tests**]
-  [#2940](https://github.com/telstra/open-kilda/pull/2940) improve checking of the lastUpdated field [**tests**]
-  [#2942](https://github.com/telstra/open-kilda/pull/2942) Define disruptor configuration for local environment [**configuration**][**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.42.1...v1.43.0).

### Affected Components:
swmanager, reroute, flow-hs, ping, connected, nbworker, router, nb, flow, network

---

## v1.42.1 (21/11/2019)

### Improvements:
-  [#2939](https://github.com/telstra/open-kilda/pull/2939) Temporary disable changing of `enable_bfd` flag [**northbound**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.42.0...v1.42.1).

### Affected Components:
nb

---

## v1.42.0 (19/11/2019)

### Features:
-  [#2843](https://github.com/telstra/open-kilda/pull/2843) Isl rules for switch [**tests**]

### Improvements:
-  [#2930](https://github.com/telstra/open-kilda/pull/2930) improve SwitchPropertiesSpec [**tests**]

### Other changes:
-  [#2913](https://github.com/telstra/open-kilda/pull/2913) Adding improvement in topology  screen to icon menu (Issue: [#2912](https://github.com/telstra/open-kilda/issues/2912)) [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.41.2...v1.42.0).

### Affected Components:
gui, neo4j

### Upgrade notes:

Consider using the following migration scripts to update db:

- [1.17 migration-script.xml](https://github.com/telstra/open-kilda/blob/develop/services/neo4j/migrations/1.17-config-multi-table/1-config-add-multi-table-flag.xml)

In case of issues these rollback scripts should be executed:

- [1.17 rollback.cql](https://github.com/telstra/open-kilda/blob/develop/services/neo4j/migrations/1.17-config-multi-table/rollback.cql)

---

## v1.41.2 (14/11/2019)

### Bug Fixes:
-  [#2919](https://github.com/telstra/open-kilda/pull/2919) Fix flow ping ethernet header [**floodlight**]

### Improvements:
-  [#2915](https://github.com/telstra/open-kilda/pull/2915) Make http async timeout for NB configurable [**northbound**]
-  [#2922](https://github.com/telstra/open-kilda/pull/2922) Fix OOM in Neo4jPersistenceManager caused by ClassGraph [**storm-topologies**]
-  [#2896](https://github.com/telstra/open-kilda/pull/2896) Add stability hotfixes and temporary ignore some tests [**tests**]
-  [#2899](https://github.com/telstra/open-kilda/pull/2899) Fix/security issues lodash [**gui**]
-  [#2902](https://github.com/telstra/open-kilda/pull/2902) Renamed org.openkilda.converter package [**floodlight**]

### Other changes:
-  [#2895](https://github.com/telstra/open-kilda/pull/2895) Update design doc for PCE (Issue: [#2894](https://github.com/telstra/open-kilda/issues/2894)) [**docs**]
-  [#2870](https://github.com/telstra/open-kilda/pull/2870) Remove outdated code [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.41.1...v1.41.2).

### Affected Components:
nb, nbworker, swmanager, fl, flow-hs, network, reroute, flow, gui

---

## v1.41.1 (07/11/2019)

### Bug Fixes:
-  [#2908](https://github.com/telstra/open-kilda/pull/2908) Fix incorrect output port for single-switch-single-port flows [**floodlight**]

### Affected Components:
fl

---

## v1.41.0 (06/11/2019)

### Features:
-  [#2243](https://github.com/telstra/open-kilda/pull/2243) Extend flow validation with meter validation. (Issue: [#1249](https://github.com/telstra/open-kilda/issues/1249)) [**floodlight**][**storm-topologies**]
-  [#2845](https://github.com/telstra/open-kilda/pull/2845) add tests for v1/config API [**tests**]

### Bug Fixes:
-  [#2887](https://github.com/telstra/open-kilda/pull/2887) Added noviflow virtual switch checks to FeatureDetectorService [**floodlight**]
-  [#2898](https://github.com/telstra/open-kilda/pull/2898) Fix flow validation for Centec and E switches. [**storm-topologies**]
-  [#2901](https://github.com/telstra/open-kilda/pull/2901) Fix flow validation for Accton switches. [**floodlight**][**storm-topologies**]

### Improvements:
-  [#2880](https://github.com/telstra/open-kilda/pull/2880) improve checks for installed rules in vxlanFlowSpec [**tests**]
-  [#2854](https://github.com/telstra/open-kilda/pull/2854) refactor "System takes isl time_unstable info into account while creating a flow" [**tests**]
-  [#2663](https://github.com/telstra/open-kilda/pull/2663) Log message if ISL has negative cost (Issue: [#2319](https://github.com/telstra/open-kilda/issues/2319)) [**storm-topologies**]
-  [#2891](https://github.com/telstra/open-kilda/pull/2891) Increase PortHistorySpec stability [**tests**]
-  [#2482](https://github.com/telstra/open-kilda/pull/2482) Move flow validation to Nbworker topology. (Issue: [#1442](https://github.com/telstra/open-kilda/issues/1442)) [**floodlight**][**northbound**][**storm-topologies**]
-  [#2680](https://github.com/telstra/open-kilda/pull/2680) Extend network topology dashboard logger (Issue: [#2659](https://github.com/telstra/open-kilda/issues/2659)) [**floodlight**][**storm-topologies**]
-  [#2299](https://github.com/telstra/open-kilda/pull/2299) Make meter modify logic using the H&S approach. (Issue: [#2298](https://github.com/telstra/open-kilda/issues/2298)) [**floodlight**][**storm-topologies**]
-  [#2877](https://github.com/telstra/open-kilda/pull/2877) Fix minor sonar issues [**floodlight**][**storm-topologies**]
-  [#2846](https://github.com/telstra/open-kilda/pull/2846) add test System does not create a flow when bandwidth is not the same on the ISL [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.40.0...v1.41.0).

### Affected Components:
flow, nbworker, nb, network, fl, flow-hs

---

## v1.40.0 (28/10/2019)

### Features:
-  [#2867](https://github.com/telstra/open-kilda/pull/2867) Feature/switch flows (Issue: [#2768](https://github.com/telstra/open-kilda/issues/2768)) [**gui**]

### Bug Fixes:
-  [#2871](https://github.com/telstra/open-kilda/pull/2871) Fixed BFD feature detector [**floodlight**]
-  [#2875](https://github.com/telstra/open-kilda/pull/2875) Fix db migrations versioning

### Improvements:
-  [#2848](https://github.com/telstra/open-kilda/pull/2848) add narrative annotation into SwitchPropertiesSpec [**tests**]
-  [#2853](https://github.com/telstra/open-kilda/pull/2853) Fix exception handling in northbound worker (Issue: [#2847](https://github.com/telstra/open-kilda/issues/2847)) [**storm-topologies**]
-  [#2863](https://github.com/telstra/open-kilda/pull/2863) minor fix in FlowCrudV2Spec [**tests**]
-  [#2864](https://github.com/telstra/open-kilda/pull/2864) Update jackson lib version [**storm-topologies**][**tests**]
-  [#2866](https://github.com/telstra/open-kilda/pull/2866) minor changes in vxlanFlow v1/v2 specs [**tests**]

### Other changes:
-  [#2699](https://github.com/telstra/open-kilda/pull/2699) Multitable switch isl lcm [**northbound**][**storm-topologies**]
-  [#2862](https://github.com/telstra/open-kilda/pull/2862) Fix major sonar issues [**floodlight**][**northbound**][**storm-topologies**][**tests**]
-  [#2868](https://github.com/telstra/open-kilda/pull/2868) Bumped versions of hbase, kafka, opentsdb, storm and zookeeper [**storm-topologies**]
-  [#2879](https://github.com/telstra/open-kilda/pull/2879) Revert "Bumped versions of hbase, kafka, opentsdb, storm and zookeeper" (Issue: [#2868](https://github.com/telstra/open-kilda/issues/2868))

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.39.1...v1.40.0).

### Affected Components:
ping, flow-hs, flow, neo4j, nb, swmanager, nbworker, fl

### Upgrade notes:

Related to [#2699](https://github.com/telstra/open-kilda/pull/2699)

Also please consider using following migration scripts to update db:
- [1.16 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.40.0/services/neo4j/migrations/1.16-multi-table-flag/1-add-multi-table-flag.xml)

In case of issues these rollback scripts should be executed:
- [1.16 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.40.0/services/neo4j/migrations/1.16-multi-table-flag/rollback.cql)

---

## v1.39.1 (17/10/2019)

### Bug Fixes:
-  [#2850](https://github.com/telstra/open-kilda/pull/2850) Replaced oraclejdk8 to openjdk8 [**tests**]

### Improvements:
-  [#2664](https://github.com/telstra/open-kilda/pull/2664) Create SimpleSwitchRule class for switch rules validation. (Issue: [#1442](https://github.com/telstra/open-kilda/issues/1442)) [**storm-topologies**]
-  [#2849](https://github.com/telstra/open-kilda/pull/2849) Improvement/controller filter default (Issues: [#2787](https://github.com/telstra/open-kilda/issues/2787) [#2803](https://github.com/telstra/open-kilda/issues/2803)) [**gui**]
-  [#2841](https://github.com/telstra/open-kilda/pull/2841) Allow to use traffexam on python-3.5.2 [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.39.0...v1.39.1).

### Affected Components:
gui

---

## v1.39.0 (07/10/2019)

### Features:
-  [#2693](https://github.com/telstra/open-kilda/pull/2693) Added multitable support into floodlight logic [**floodlight**][**storm-topologies**]
-  [#2745](https://github.com/telstra/open-kilda/pull/2745) Add switch connection port info. Fix #2681 (Issue: [#2681](https://github.com/telstra/open-kilda/issues/2681)) [**northbound**][**storm-topologies**]
-  [#2812](https://github.com/telstra/open-kilda/pull/2812) Port history antiflap stats (Issue: [#2718](https://github.com/telstra/open-kilda/issues/2718)) [**storm-topologies**]
-  [#2814](https://github.com/telstra/open-kilda/pull/2814) Add disable port discovery feature (Issue: [#2794](https://github.com/telstra/open-kilda/issues/2794)) [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#2830](https://github.com/telstra/open-kilda/pull/2830) Fixed different timeFirstSeen and TimeLastSeen for Connected Devices
-  [#2836](https://github.com/telstra/open-kilda/pull/2836) Delete switch properties when switch is deleted [**storm-topologies**]
-  [#2840](https://github.com/telstra/open-kilda/pull/2840) Fix log message in LinkOperationService. [**storm-topologies**]
-  [#2842](https://github.com/telstra/open-kilda/pull/2842) Fix kafka test config [**tests**]

### Improvements:
-  [#2624](https://github.com/telstra/open-kilda/pull/2624) OF cookie management cleanup [**floodlight**][**storm-topologies**]
-  [#2823](https://github.com/telstra/open-kilda/pull/2823) Add notice for autogenerated files
-  [#2831](https://github.com/telstra/open-kilda/pull/2831) Tag more low-value tests as LOW_PRIORITY [**tests**]
-  [#2832](https://github.com/telstra/open-kilda/pull/2832) Removed migration 1.13 artifact
-  [#2833](https://github.com/telstra/open-kilda/pull/2833) improve waiting in MflStatSpec [**tests**]
-  [#2767](https://github.com/telstra/open-kilda/pull/2767) extend statistic test coverage for  different type of flow [**tests**]
-  [#2837](https://github.com/telstra/open-kilda/pull/2837) Moved initialization of connectedDevicesService to setup method [**floodlight**]
-  [#2838](https://github.com/telstra/open-kilda/pull/2838) Add perf test for verifying switch validation with a lot of flows [**tests**]
-  [#2658](https://github.com/telstra/open-kilda/pull/2658) Added helper code to move flow validation. (Issue: [#1442](https://github.com/telstra/open-kilda/issues/1442)) [**floodlight**][**storm-topologies**]
-  [#2796](https://github.com/telstra/open-kilda/pull/2796) add flowHistory test for v2 [**tests**]
-  [#2798](https://github.com/telstra/open-kilda/pull/2798) Extend Endurance test with a 'break isl' event [**tests**]
-  [#2799](https://github.com/telstra/open-kilda/pull/2799) Disable port discovery feature design (Issue: [#2794](https://github.com/telstra/open-kilda/issues/2794)) [**docs**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.38.0...v1.39.0).

### Affected Components:
flow, flow-hs, network, router, fl, stats, neo4j, connected, nb, nbworker, swmanager

### Upgrade notes:

Related to [#2814](https://github.com/telstra/open-kilda/pull/2814)

Also please consider using following migration scripts to update db:
- [1.15 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.39.0/services/neo4j/migrations/1.15-port-properties/1-update-constraints.xml)

In case of issues these rollback scripts should be executed:
- [1.15 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.39.0/services/neo4j/migrations/1.15-port-properties/rollback.cql)

---

## v1.38.0 (30/09/2019)

### Features:
-  [#2693](https://github.com/telstra/open-kilda/pull/2693) Added multitable support into floodlight logic [**floodlight**][**storm-topologies**]
-  [#2745](https://github.com/telstra/open-kilda/pull/2745) Add switch connection port info. Fix #2681 (Issue: [#2681](https://github.com/telstra/open-kilda/issues/2681)) [**northbound**][**storm-topologies**]
-  [#2812](https://github.com/telstra/open-kilda/pull/2812) Port history antiflap stats (Issue: [#2718](https://github.com/telstra/open-kilda/issues/2718)) [**storm-topologies**]

### Bug Fixes:
-  [#2830](https://github.com/telstra/open-kilda/pull/2830) Fixed different timeFirstSeen and TimeLastSeen for Connected Devices

### Improvements:
-  [#2624](https://github.com/telstra/open-kilda/pull/2624) OF cookie management cleanup [**floodlight**][**storm-topologies**]
-  [#2796](https://github.com/telstra/open-kilda/pull/2796) add flowHistory test for v2 [**tests**]
-  [#2831](https://github.com/telstra/open-kilda/pull/2831) Tag more low-value tests as LOW_PRIORITY [**tests**]
-  [#2832](https://github.com/telstra/open-kilda/pull/2832) Removed migration 1.13 artifact
-  [#2767](https://github.com/telstra/open-kilda/pull/2767) extend statistic test coverage for  different type of flow [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.37.0...v1.38.0).

### Affected Components:
swmanager, network, stats, fl, flow-hs, nbworker, flow, nb, connected

---

## v1.37.0 (26/09/2019)

### Features:
-  [#2756](https://github.com/telstra/open-kilda/pull/2756) Added catching of LLDP by Floodlight (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**floodlight**]
-  [#2697](https://github.com/telstra/open-kilda/pull/2697) Added instalation of LLDP rules (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**floodlight**][**storm-topologies**]
-  [#2704](https://github.com/telstra/open-kilda/pull/2704) Added switch validation of LLDP (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**storm-topologies**]
-  [#2643](https://github.com/telstra/open-kilda/pull/2643) Added ability to collect Connected Devices for flow (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**floodlight**][**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#2817](https://github.com/telstra/open-kilda/pull/2817) Fixed null LLDP meter (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**floodlight**]
-  [#2819](https://github.com/telstra/open-kilda/pull/2819) Do not catch LLDP if switch has only 1 OF table (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**floodlight**][**storm-topologies**]
-  [#2820](https://github.com/telstra/open-kilda/pull/2820) Validate LLDP meters with Noviflow burstsize limitations (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**storm-topologies**]
-  [#2825](https://github.com/telstra/open-kilda/pull/2825) Added LLDP cookies and meters to StatsTopology cache (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**storm-topologies**]
-  [#2810](https://github.com/telstra/open-kilda/pull/2810) Fixed handling of unexpected responses in SwitchManagerWorker in Network topology. (Issue: [#2809](https://github.com/telstra/open-kilda/issues/2809)) [**storm-topologies**]

### Improvements:
-  [#2816](https://github.com/telstra/open-kilda/pull/2816) Extend lldp tests with lldp+vxlan tests [**tests**]
-  [#2754](https://github.com/telstra/open-kilda/pull/2754) Add connected devices traffgen support to testing framework [**tests**]
-  [#2824](https://github.com/telstra/open-kilda/pull/2824) fix logic in makePathMorePreferable [**tests**]
-  [#2828](https://github.com/telstra/open-kilda/pull/2828) Update lldp tests to select switches for tests more granularly [**tests**]
-  [#2776](https://github.com/telstra/open-kilda/pull/2776) Add test for 'new switch connects' scenario [**tests**]
-  [#2782](https://github.com/telstra/open-kilda/pull/2782) add test "System doesn't reroute flow to a path with not enough bandwidth available" [**tests**]
-  [#2800](https://github.com/telstra/open-kilda/pull/2800) add waiter to openTsdbSpec [**tests**]
-  [#2808](https://github.com/telstra/open-kilda/pull/2808) Add test to verify single-port flow rules validation/synchronization [**tests**]
-  [#2739](https://github.com/telstra/open-kilda/pull/2739) Func tests for connected devices rules (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**tests**]
-  [#2807](https://github.com/telstra/open-kilda/pull/2807) Added event logging with getting statistics. (Issue: [#2801](https://github.com/telstra/open-kilda/issues/2801)) [**floodlight**][**storm-topologies**]
-  [#2815](https://github.com/telstra/open-kilda/pull/2815) Fixed 1.14 migration


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.36.1...v1.37.0).

### Affected Components:
stats, nbworker, swmanager, fl, nb, flow, network, neo4j

### Upgrade notes:

Related to [#2643](https://github.com/telstra/open-kilda/pull/2643)

Also please consider using following migration scripts to update db:
- [1.14 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.37.0/services/neo4j/migrations/1.14-connected-device-indexes/1-add-connected-device-index.xml)

In case of issues these rollback scripts should be executed:
- [1.14 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.37.0/services/neo4j/migrations/1.14-connected-device-indexes/rollback.cql)

## v1.36.1 (19/09/2019)

### Bug Fixes:
-  [#2813](https://github.com/telstra/open-kilda/pull/2813) Hotfix/isl round trip graph issue. [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.36.0...v1.36.1).

### Affected Components:
GUI

---

## v1.36.0 (18/09/2019)

### Features:
-  [#2760](https://github.com/telstra/open-kilda/pull/2760) Feature to enable search functionality to drop-downs (Issues: [#2648](https://github.com/telstra/open-kilda/issues/2648) [#2737](https://github.com/telstra/open-kilda/issues/2737)) [**gui**]
-  [#2698](https://github.com/telstra/open-kilda/pull/2698) Allocate LLDP resources for flows (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**storm-topologies**]
-  [#2726](https://github.com/telstra/open-kilda/pull/2726) Port history feature [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#2752](https://github.com/telstra/open-kilda/pull/2752) Add protected paths to flow cache in stats topology. Fix #2749 [**storm-topologies**]
-  [#2790](https://github.com/telstra/open-kilda/pull/2790) Fix the search for N network paths. (Issue: [#2789](https://github.com/telstra/open-kilda/issues/2789)) [**storm-topologies**]
-  [#2546](https://github.com/telstra/open-kilda/pull/2546) Force `WorkerBolt` to be more strict with stored data [**storm-topologies**]
-  [#2804](https://github.com/telstra/open-kilda/pull/2804) Fix for handling bfd response from speaker in network topology [**storm-topologies**]

### Improvements:
-  [#2696](https://github.com/telstra/open-kilda/pull/2696) Enable nested VLAN support into OVS into lab-service [**tests**]
-  [#2764](https://github.com/telstra/open-kilda/pull/2764) add test  system is able to reuse current protected path when can't find new protected path while intentional reroute (Issue: [#2762](https://github.com/telstra/open-kilda/issues/2762)) [**tests**]
-  [#2725](https://github.com/telstra/open-kilda/pull/2725) improve procedure of making path more preferable (Issue: [#2426](https://github.com/telstra/open-kilda/issues/2426)) [**tests**]
-  [#2792](https://github.com/telstra/open-kilda/pull/2792) minor fix in swapEndpointSpec [**tests**]
-  [#2793](https://github.com/telstra/open-kilda/pull/2793) Changed default broadcast address for kilda discovery [**floodlight**]
-  [#2802](https://github.com/telstra/open-kilda/pull/2802) Revert unhandledInput behaviour in worker bolts [**storm-topologies**]

### Other changes:
-  [#2757](https://github.com/telstra/open-kilda/pull/2757) Remove obsolete section [**docs**]
-  [#2586](https://github.com/telstra/open-kilda/pull/2586) Add QinQ support into traffexam [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.35.1...v1.36.0).

### Affected Components:
fl, neo4j, flow-hs, nb, flow, network, nbworker, stats

---

## v1.35.1 (11/09/2019)

### Features:
-  [#2753](https://github.com/telstra/open-kilda/pull/2753) Design for LLDP Connected devices feature (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**docs**]

### Bug Fixes:
-  [#2784](https://github.com/telstra/open-kilda/pull/2784) Fixed JSON deserialization when switch sync error. (Issue: [#2783](https://github.com/telstra/open-kilda/issues/2783)) [**northbound**][**storm-topologies**]

### Improvements:
-  [#2778](https://github.com/telstra/open-kilda/pull/2778) Change logic of verification duplicate isl in PCE
-  [#2775](https://github.com/telstra/open-kilda/pull/2775) add monitoring section in readme file for performance test [**tests**]


For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.35.0...v1.35.1).

### Affected Components:
network, swmanager, nb

---

## v1.35.0 (09/09/2019)

### Features:
-  [#2688](https://github.com/telstra/open-kilda/pull/2688) Added detectConnectedDevices fields to v1 Flow API (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**api**][**northbound**]
-  [#2703](https://github.com/telstra/open-kilda/pull/2703) Allocate LLDP resources (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**storm-topologies**]
-  [#2738](https://github.com/telstra/open-kilda/pull/2738) Add switch rules synchronization when switch activation. (Issue: [#2331](https://github.com/telstra/open-kilda/issues/2331)) [**storm-topologies**]

### Improvements:
-  [#2748](https://github.com/telstra/open-kilda/pull/2748) fix config for FL containers in docker-compose.yml [**configuration**]
-  [#2568](https://github.com/telstra/open-kilda/pull/2568) fix creating topology for performance test [**tests**]
-  [#2763](https://github.com/telstra/open-kilda/pull/2763) change flow description for auto tests [**tests**]
-  [#2766](https://github.com/telstra/open-kilda/pull/2766) Fix removing of unallocated resources on neo4j failure / constraint. [**storm-topologies**]
-  [#2771](https://github.com/telstra/open-kilda/pull/2771) Minor improvements in FlowSyncSpec [**tests**]
-  [#2773](https://github.com/telstra/open-kilda/pull/2773) Minor timeout increase for better test stability [**tests**]
-  [#2652](https://github.com/telstra/open-kilda/pull/2652) Propagate correlation id into completable future callbacks [**floodlight**]
-  [#2777](https://github.com/telstra/open-kilda/pull/2777) Fixed processing an activated switch if the meterEntry is null in the synchronization response. [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.34.0...v1.35.0).

### Affected Components:
nb, fl, network, flow, swmanager, flow-hs

### Upgrade notes:
If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j
before you deploy and start this version. You should execute migration scripts before starting of deployment:
 - [1.11 update-constraints-changelog.xml](https://github.com/telstra/open-kilda/blob/v1.35.0/services/neo4j/migrations/1.11-lldp-resources/1-update-constraints-changelog.xml)

In case of any issues you are able to rollback 1.8 changes using:
 - [1.11 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.35.0/services/neo4j/migrations/1.11-lldp-resources/rollback.cql)

---

## v1.34.0 (02/09/2019)

### Features:
-  [#2679](https://github.com/telstra/open-kilda/pull/2679) Expose switch features over rest api [**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#2730](https://github.com/telstra/open-kilda/pull/2730) Fix tag extension for cases when no IterationTags annotation present [**tests**]
-  [#2712](https://github.com/telstra/open-kilda/pull/2712) Fix switch rules synchronization. (Issues: [#2706](https://github.com/telstra/open-kilda/issues/2706) [#2707](https://github.com/telstra/open-kilda/issues/2707)) [**storm-topologies**]
-  [#2727](https://github.com/telstra/open-kilda/pull/2727) Split consumer groups for different regions [**floodlight**]

### Improvements:
-  [#2690](https://github.com/telstra/open-kilda/pull/2690) Switch Validation Refactoring (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582)) [**storm-topologies**]
-  [#2732](https://github.com/telstra/open-kilda/pull/2732) adjust flowCrud to work with not empty env [**tests**]
-  [#2733](https://github.com/telstra/open-kilda/pull/2733) Increase stats waiting timeout for better stability of test [**tests**]
-  [#2671](https://github.com/telstra/open-kilda/pull/2671) Produce LLDP packets by traffexam (Issue: [#2661](https://github.com/telstra/open-kilda/issues/2661))
-  [#2741](https://github.com/telstra/open-kilda/pull/2741) small improvements for tests [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.33.0...v1.34.0).

### Affected Components:
nbworker, neo4j, fl, swmanager, nb

### Upgrade notes:

Related to [#2679](https://github.com/telstra/open-kilda/pull/2679)

Also please consider using following migration scripts to update db:
- [1.0 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.34.0/services/neo4j/migrations/1.10-switch-properties/1-rename-switch-features-changelog.xml)

In case of issues these rollback scripts should be executed:
- [1.10 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.34.0/services/neo4j/migrations/1.10-switch-properties/rollback.cql)

---

## v1.33.0 (27/08/2019)

### Features:
-  [#2644](https://github.com/telstra/open-kilda/pull/2644) Add multitable flag for fl commands

### Bug Fixes:
-  [#2721](https://github.com/telstra/open-kilda/pull/2721) Fix CommandBuilder in the SwitchManager topology. [**storm-topologies**]
-  [#2724](https://github.com/telstra/open-kilda/pull/2724) Add retry when neo4j's ClientException is thrown [**storm-topologies**]
-  [#2729](https://github.com/telstra/open-kilda/pull/2729) Force stats topology cache sync to work with H&S requests [**storm-topologies**]

### Improvements:
-  [#2566](https://github.com/telstra/open-kilda/pull/2566) Minor change for which tests are tagged as SMOKE_SWITCHES [**tests**]
-  [#2711](https://github.com/telstra/open-kilda/pull/2711) Add 'purgeTopology' setup step in performance tests [**tests**]
-  [#2713](https://github.com/telstra/open-kilda/pull/2713) Adjust all tests to properly handle antiflap cooldown. [**tests**]
-  [#2653](https://github.com/telstra/open-kilda/pull/2653) extend vxlanFlow tests (APIv1) by checking rules [**tests**]
-  [#2723](https://github.com/telstra/open-kilda/pull/2723) add "See" annotation [**tests**]
-  [#2675](https://github.com/telstra/open-kilda/pull/2675) Get rid from ISL cost manipulation in DB. (Issue: [#2263](https://github.com/telstra/open-kilda/issues/2263)) [**storm-topologies**]
-  [#2687](https://github.com/telstra/open-kilda/pull/2687) refactor flowHelperV2,flowCrud,swapEndpoint files [**tests**]

### Other changes:
-  [#2715](https://github.com/telstra/open-kilda/pull/2715) Fixed RemoveFlow constructor error [**floodlight**]
-  [#2719](https://github.com/telstra/open-kilda/pull/2719) Revert "Take into account bugfix label while generating changelog"
-  [#2720](https://github.com/telstra/open-kilda/pull/2720) Take into account bugfix label while generating changelog
-  [#2722](https://github.com/telstra/open-kilda/pull/2722) Add missing flow reroute fail event in flow dashboard [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.32.0...v1.33.0).

### Affected Components:
flow-hs, swmanager, flow, fl, network, stats

---

## v1.32.0 (20/08/2019)

### Features:
-  [#2669](https://github.com/telstra/open-kilda/pull/2669) Added models and repos for Connected Devices (Issue: [#2582](https://github.com/telstra/open-kilda/issues/2582))

### Bug Fixes:
-  [#2596](https://github.com/telstra/open-kilda/pull/2596) Added udp dst port match into broadcast default rule. (Issue: [#2595](https://github.com/telstra/open-kilda/issues/2595)) [**floodlight**]
-  [#2695](https://github.com/telstra/open-kilda/pull/2695) Fix flow delete without resources +flow reroute v2 resource deallocation [**storm-topologies**]
-  [#2700](https://github.com/telstra/open-kilda/pull/2700) Update tests to workaround issue #2595 [**tests**]
-  [#2705](https://github.com/telstra/open-kilda/pull/2705) Hot fix wrong error message expected in func tests [**tests**]
-  [#2709](https://github.com/telstra/open-kilda/pull/2709) Hot fix wrong error message expected in func tests [**tests**]
-  [#2710](https://github.com/telstra/open-kilda/pull/2710) Do not match UDP port for broadcast rule on Centec [**floodlight**]

### Improvements:
-  [#2691](https://github.com/telstra/open-kilda/pull/2691) Increase wait after wfm finished for better test stability. [**tests**]
-  [#2668](https://github.com/telstra/open-kilda/pull/2668) Use hs auto-reroutes by default. Update waiters for path allocation [**tests**]
-  [#2673](https://github.com/telstra/open-kilda/pull/2673) Simplify unit test exec process
-  [#2647](https://github.com/telstra/open-kilda/pull/2647) add tests: vxlan for api v2 [**tests**]
-  [#2682](https://github.com/telstra/open-kilda/pull/2682) Added switch features to switch DB model [**storm-topologies**]
-  [#2686](https://github.com/telstra/open-kilda/pull/2686) Add the default rules to switch rules synchronization. [**floodlight**][**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.31.0...v1.32.0).

### Upgrade notes:
It is required to synchronize default rules on all switches.

Also please consider using following migration scripts to update db:
- [1.9 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.32.0/services/neo4j/migrations/1.9-connected-devices/1-update-constraints-changelog.xml)

In case of issues these rollback scripts should be executed:
- [1.9 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.32.0/services/neo4j/migrations/1.9-connected-devices/rollback.cql)

---

## v1.31.0 (14/08/2019)

### Bug Fixes:
-  [#2662](https://github.com/telstra/open-kilda/pull/2662) Fixed incorrect converting meter rate/burstsize from packets to kilobits [**floodlight**]
-  [#2685](https://github.com/telstra/open-kilda/pull/2685) Fix default flow creation in v2 +fixed resetting of FSM in case of retry [**northbound**]

### Improvements:
-  [#2672](https://github.com/telstra/open-kilda/pull/2672) Misc fixes in tests [**tests**]

### Other changes:
-  [#2637](https://github.com/telstra/open-kilda/pull/2637) Enable Vxlan support for v2 api [**floodlight**]
-  [#2683](https://github.com/telstra/open-kilda/pull/2683) Fix Kilda-FlowOperations-Filtered-Table Kibana search

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.30.1...v1.31.0).

### Affected Components:
flow-hs, nb, fl

---

## v1.30.1 (07/08/2019)

### Bug Fixes:
-  [#2670](https://github.com/telstra/open-kilda/pull/2670) Fix flows in DOWN state without flow paths [**northbound**][**storm-topologies**]
-  [#2677](https://github.com/telstra/open-kilda/pull/2677) Fix error code for flow validation if flow is in DOWN state [**northbound**]
-  [#2678](https://github.com/telstra/open-kilda/pull/2678) Fix failed flow creation/reroute without paths

### Other changes:
-  [#2674](https://github.com/telstra/open-kilda/pull/2674) Introduce FlowOperations Kibana dashboard [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.30.0...v1.30.1).

### Affected Components:
flow-hs, nb

---

## v1.30.0 (06/08/2019)

### Features:
-  [#2633](https://github.com/telstra/open-kilda/pull/2633) Adding feature to update isl bandwidth [**gui**]
-  [#2601](https://github.com/telstra/open-kilda/pull/2601) Introduce a custom dashboard logger for flow operations. [**storm-topologies**]

### Bug Fixes:
-  [#2645](https://github.com/telstra/open-kilda/pull/2645) Fix for installing meter for vxlan unicast (Issue: [#2635](https://github.com/telstra/open-kilda/issues/2635)) [**floodlight**]
-  [#2649](https://github.com/telstra/open-kilda/pull/2649) Fix: Ignore default rules when sync rules. [**storm-topologies**]
-  [#2655](https://github.com/telstra/open-kilda/pull/2655) Added go to table instruction to OF flow mapping (Issue: [#2375](https://github.com/telstra/open-kilda/issues/2375)) [**floodlight**]
-  [#2666](https://github.com/telstra/open-kilda/pull/2666) Fix flow create retries
-  [#2667](https://github.com/telstra/open-kilda/pull/2667) Added condition to skip cost reduction when link goes from state under maintenance and cost less than isl.cost.when.under.maintenance. (Issue: [#2319](https://github.com/telstra/open-kilda/issues/2319)) [**storm-topologies**]
-  [#2670](https://github.com/telstra/open-kilda/pull/2670) Fix flows in DOWN state without flow paths [**northbound**][**storm-topologies**]

### Improvements:
-  [#2626](https://github.com/telstra/open-kilda/pull/2626) test: "System recreates excess meter when flow is created with the same meterId" (Issue: [#2625](https://github.com/telstra/open-kilda/issues/2625)) [**tests**]
-  [#2631](https://github.com/telstra/open-kilda/pull/2631) Get rid of kafka breaker [**tests**]
-  [#2638](https://github.com/telstra/open-kilda/pull/2638) update meterSpec according to wb5164 switch [**tests**]
-  [#2516](https://github.com/telstra/open-kilda/pull/2516) add tests for checking the encapsulation-type field [**tests**]
-  [#2646](https://github.com/telstra/open-kilda/pull/2646) Add WB5164 support to tests as well as various misc fixes [**tests**]
-  [#2654](https://github.com/telstra/open-kilda/pull/2654) Refactor unit tests for install default rules [**floodlight**][**tests**]
-  [#2657](https://github.com/telstra/open-kilda/pull/2657) fix pinned flow spec [**tests**]
-  [#2602](https://github.com/telstra/open-kilda/pull/2602) Add ContentionSpec for v1 and v2 api [**tests**]
-  [#2612](https://github.com/telstra/open-kilda/pull/2612) add tests for pinned/protected/diverse/default flow via APIv2 (Issue: [#2575](https://github.com/telstra/open-kilda/issues/2575)) [**tests**]
-  [#2493](https://github.com/telstra/open-kilda/pull/2493) Repeat db transation on db locks (Issue: [#2391](https://github.com/telstra/open-kilda/issues/2391)) [**storm-topologies**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.29.0...v1.30.0).

### Affected Components:
nb, neo4j, swmanager, network, flow-hs, nbworker, reroute, fl, flow

---

## v1.29.1 (01/08/2019)

### Bug Fixes:
-  [#2656](https://github.com/telstra/open-kilda/pull/2656) Hotfix to add permissions on switch, flow inventory and flow contracts. [**gui**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.29.0...v1.29.1).

### Affected Components:
gui

---

## v1.29.0 (31/07/2019)

### Features:
-  [#2591](https://github.com/telstra/open-kilda/pull/2591) Fix sync rules in case VxLAN encapsulation. [**storm-topologies**]
-  [#2605](https://github.com/telstra/open-kilda/pull/2605) Add to FL getting of default rules such as they should be on the switch. [**floodlight**]
-  [#2611](https://github.com/telstra/open-kilda/pull/2611) Add the default rules validation in the switch validation. [**northbound**][**storm-topologies**]
-  [#2621](https://github.com/telstra/open-kilda/pull/2621) inPort and outPort are added as tags for the flow stats [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#2628](https://github.com/telstra/open-kilda/pull/2628) Fix properly picking free port in swap endpoints test
-  [#2632](https://github.com/telstra/open-kilda/pull/2632) Fix flow create v2 issues +retries enhancement (Issue: [#2575](https://github.com/telstra/open-kilda/issues/2575)) [**floodlight**]
-  [#2640](https://github.com/telstra/open-kilda/pull/2640) Make flow create retries configurable [**configuration**]
-  [#2608](https://github.com/telstra/open-kilda/pull/2608) Limit traffexam bandwidth when examing in parallel [**tests**]
-  [#2609](https://github.com/telstra/open-kilda/pull/2609) Move ExtensionModule file to 'main' to properly resolve in helpers [**tests**]
-  [#2616](https://github.com/telstra/open-kilda/pull/2616) Fixed feature detection for E switches with 500 software (Issue: [#2615](https://github.com/telstra/open-kilda/issues/2615)) [**floodlight**]
-  [#2618](https://github.com/telstra/open-kilda/pull/2618) Fix one of swap endpoints tests [**tests**]

### Improvements:
-  [#2627](https://github.com/telstra/open-kilda/pull/2627) Add workaround to switch validation logic regarding E-switches. (Issue: [#2562](https://github.com/telstra/open-kilda/issues/2562)) [**storm-topologies**]
-  [#2583](https://github.com/telstra/open-kilda/pull/2583) Upd EnduranceSpec, add FlowPinger, add Dice for calling events randomly [**tests**]
-  [#2606](https://github.com/telstra/open-kilda/pull/2606) add new endpoint for managing iptables rules on floodlight (Issue: [#1268](https://github.com/telstra/open-kilda/issues/1268)) [**tests**]
-  [#2545](https://github.com/telstra/open-kilda/pull/2545) Update H&S reroute with encapsulation implementations
-  [#2617](https://github.com/telstra/open-kilda/pull/2617) add virtualImpl for managing floodlight access [**tests**]
-  [#2623](https://github.com/telstra/open-kilda/pull/2623) Add default rules validation test and update existing validation tests [**tests**]
-  [#2216](https://github.com/telstra/open-kilda/pull/2216) Create FloodlightDashboardLogger for logging OF events [**floodlight**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.28.0...v1.29.0).

### Affected Components:
fl, nb, swmanager, router, stats, flow, flow-hs

---

## v1.28.0 (23/07/2019)

### Features:
-  [#2503](https://github.com/telstra/open-kilda/pull/2503) Fix flow validation in case VxLAN encapsulation. (Issue: [#647](https://github.com/telstra/open-kilda/issues/647)) [**floodlight**][**northbound**]

### Bug Fixes:
-  [#2603](https://github.com/telstra/open-kilda/pull/2603) Fix flow status of degraded flows w/ or w/o h&s flag [**tests**]
-  [#2607](https://github.com/telstra/open-kilda/pull/2607) Disable dumping table stats from OF 1.2 (Issue: [#2600](https://github.com/telstra/open-kilda/issues/2600)) [**floodlight**]

### Improvements:
-  [#2594](https://github.com/telstra/open-kilda/pull/2594) add test for a new vxlan default rule/meter [**tests**]
-  [#2571](https://github.com/telstra/open-kilda/pull/2571) Update SwapEndpointSpec with new tests and minor refactoring [**tests**]
-  [#2543](https://github.com/telstra/open-kilda/pull/2543) H&S reroute - minimize transaction contention and locks (Issue: [#2497](https://github.com/telstra/open-kilda/issues/2497)) [**storm-topologies**]
-  [#2579](https://github.com/telstra/open-kilda/pull/2579) add test 'System doesn't allow to create a one-switch flow on a DEACTIVATED switch' (Issue: [#2576](https://github.com/telstra/open-kilda/issues/2576)) [**tests**]
-  [#2589](https://github.com/telstra/open-kilda/pull/2589) Minor test updates for better stability [**tests**]

### Other changes:
-  [#2525](https://github.com/telstra/open-kilda/pull/2525) Get rid from event(wfm) topology [**storm-topologies**]
-  [#2184](https://github.com/telstra/open-kilda/pull/2184) Design for round trip latency (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**docs**]
-  [#2509](https://github.com/telstra/open-kilda/pull/2509) Configurable ping design (Issue: [#2542](https://github.com/telstra/open-kilda/issues/2542))
-  [#2517](https://github.com/telstra/open-kilda/pull/2517) Add logs for port update. [**floodlight**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.27.0...v1.28.0).

### Affected Components:
flow-hs, flow, reroute, neo4j, nb, nbworker, gui, fl, event

### Upgrade notes:
If you have an older version of Kilda installed, then you must migrate the data stored in Neo4j
before you deploy and start this version. You should execute migration scripts before starting of deployment:
 - [1.8 create-index-on-flow-meter-changelog.xml](https://github.com/telstra/open-kilda/blob/v1.28.0/services/neo4j/migrations/1.8-index-on-flow-meter/1-create-index-on-flow-meter-changelog.xml)
 - [1.8 migration-changelog.xml](https://github.com/telstra/open-kilda/blob/v1.28.0/services/neo4j/migrations/1.8-index-on-flow-meter/2-migration-changelog.xml)

In case of any issues you are able to rollback 1.8 changes using:
 - [1.8 rollback-indexes.cql](https://github.com/telstra/open-kilda/blob/v1.28.0/services/neo4j/migrations/1.8-index-on-flow-meter/rollback-indexes.cql)
 - [1.8 rollback-migration.cql](https://github.com/telstra/open-kilda/blob/v1.28.0/services/neo4j/migrations/1.8-index-on-flow-meter/rollback-migration.cql)

---

## v1.27.0 (11/07/2019)

### Features:
-  [#2592](https://github.com/telstra/open-kilda/pull/2592) Add table stats to opentsdb (#2574) (Issue: [#2574](https://github.com/telstra/open-kilda/issues/2574)) [**floodlight**][**storm-topologies**]
-  [#2560](https://github.com/telstra/open-kilda/pull/2560) Do not ignore Inactive ISLs in latency Cache (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**storm-topologies**]
-  [#2539](https://github.com/telstra/open-kilda/pull/2539) add test for checking "get all flows for a switch" [**tests**]

### Improvements:
-  [#2532](https://github.com/telstra/open-kilda/pull/2532) extend ProtectedPathSpec by checking the 'flowStatusDetails' filed [**tests**]
-  [#2567](https://github.com/telstra/open-kilda/pull/2567) Changed update latency intervals (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**configuration**]
-  [#2570](https://github.com/telstra/open-kilda/pull/2570) Add special test that generates topo.yaml based on what is discovered [**tests**]
-  [#2476](https://github.com/telstra/open-kilda/pull/2476) Do not treat all speaker as unavailable on floodlightrouter start (Issue: [#2456](https://github.com/telstra/open-kilda/issues/2456)) [**storm-topologies**]
-  [#2478](https://github.com/telstra/open-kilda/pull/2478) Shared bolt cappable to produce periodic time tuples [**storm-topologies**]
-  [#2553](https://github.com/telstra/open-kilda/pull/2553) Fix for vxlan unicast ping rule [**floodlight**][**northbound**]
-  [#2494](https://github.com/telstra/open-kilda/pull/2494) add tests for creating 2047 and 4094 flows [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.26.0...v1.27.0).

### Affected Components:
isllatency, fl, router, nb, ping, stats

---

## v1.26.0 (04/07/2019)

### Features:
-  [#2438](https://github.com/telstra/open-kilda/pull/2438) Excess rules and meters removing (Issues: [#2189](https://github.com/telstra/open-kilda/issues/2189) [#2215](https://github.com/telstra/open-kilda/issues/2215) [#2289](https://github.com/telstra/open-kilda/issues/2289)) [**api**][**floodlight**][**northbound**][**storm-topologies**]
-  [#2536](https://github.com/telstra/open-kilda/pull/2536) Add API to get all flows for a particular switch or endpoint. (Issue: [#2529](https://github.com/telstra/open-kilda/issues/2529)) [**api**][**northbound**][**storm-topologies**]

### Bug Fixes:
-  [#2569](https://github.com/telstra/open-kilda/pull/2569) Fix flow validation for swap endpoints functionality (for default flows) [**storm-topologies**]
-  [#2572](https://github.com/telstra/open-kilda/pull/2572) Do not use negative round trip latency for OpenTSDB
-  [#2573](https://github.com/telstra/open-kilda/pull/2573) Fix validation of switches that don't support meters (FLOW HS topology)
-  [#2577](https://github.com/telstra/open-kilda/pull/2577) Fix fsm flow create
-  [#2555](https://github.com/telstra/open-kilda/pull/2555) Fix occasionally failing SwapEndpoint test [**tests**]

### Improvements:
-  [#2528](https://github.com/telstra/open-kilda/pull/2528) extend FlowDiversitySpec by checking the "diverse_with" field [**tests**]
-  [#2561](https://github.com/telstra/open-kilda/pull/2561) add extra check to prevent fail on a small env [**tests**]
-  [#2564](https://github.com/telstra/open-kilda/pull/2564) Add test that reproduces #2563 [**tests**]
-  [#2535](https://github.com/telstra/open-kilda/pull/2535) [Network topo] Change logging level for port state events. [**storm-topologies**]
-  [#2506](https://github.com/telstra/open-kilda/pull/2506) Make most traffic examination to run in parallel in both directions [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.25.0...v1.26.0).

### Affected Components:
nb, flow, nbworker, swmanager, fl, network, router, flow-hs

---

## v1.25.0 (01/07/2019)

### Features:
-  [#2508](https://github.com/telstra/open-kilda/pull/2508) PCE takes into account encapsulation type [**storm-topologies**]
-  [#2518](https://github.com/telstra/open-kilda/pull/2518) Add protected path status to API. (Issue: [#2513](https://github.com/telstra/open-kilda/issues/2513)) [**northbound**][**storm-topologies**]
-  [#2521](https://github.com/telstra/open-kilda/pull/2521) Tunnel id match extended [**floodlight**][**storm-topologies**]
-  [#2392](https://github.com/telstra/open-kilda/pull/2392) Add functional tests for flow swap endpoint feature [**tests**]
-  [#2530](https://github.com/telstra/open-kilda/pull/2530) RTL Part 5: change handling of OpenTsdb records (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**storm-topologies**]
-  [#2533](https://github.com/telstra/open-kilda/pull/2533) RTL Part 6: Added One Way latency manipulation (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**floodlight**][**storm-topologies**]
-  [#2473](https://github.com/telstra/open-kilda/pull/2473) Return diverse group flows id in get flow response (Issue: [#2465](https://github.com/telstra/open-kilda/issues/2465)) [**api**][**northbound**][**storm-topologies**]
-  [#2485](https://github.com/telstra/open-kilda/pull/2485) Issue 647 add vxlan methods to fl rebased [**floodlight**][**northbound**][**storm-topologies**]
-  [#2486](https://github.com/telstra/open-kilda/pull/2486) Design for representing protected path status in API [**api**]
-  [#2292](https://github.com/telstra/open-kilda/pull/2292) Feature/580 round trip latency (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**floodlight**][**storm-topologies**]
-  [#2558](https://github.com/telstra/open-kilda/pull/2558) Changed 'isl.latency' to 'isl.rtt' metric in GUI (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**gui**]

### Bug Fixes:
-  [#2498](https://github.com/telstra/open-kilda/pull/2498) Minor fixes for nb correlation id check and logging [**northbound**]
-  [#2504](https://github.com/telstra/open-kilda/pull/2504) Fix TagExtension to properly count execution times for a tag [**tests**]
-  [#2507](https://github.com/telstra/open-kilda/pull/2507) Fix for separate functional test execution [**tests**]
-  [#2510](https://github.com/telstra/open-kilda/pull/2510) Fix NPE in SwManager RouterBolt (Issue: [#2219](https://github.com/telstra/open-kilda/issues/2219)) [**storm-topologies**]
-  [#2520](https://github.com/telstra/open-kilda/pull/2520) Fix log message for validate switch request. [**northbound**]
-  [#2463](https://github.com/telstra/open-kilda/pull/2463) Make FSM independent (network-topology) (Issue: [#2457](https://github.com/telstra/open-kilda/issues/2457)) [**storm-topologies**]
-  [#2541](https://github.com/telstra/open-kilda/pull/2541) Fix correlation id checks for swagger ui (Issue: [#2515](https://github.com/telstra/open-kilda/issues/2515)) [**northbound**]
-  [#2548](https://github.com/telstra/open-kilda/pull/2548) Data migration for supported_transit_encapsulation
-  [#2549](https://github.com/telstra/open-kilda/pull/2549) Fix for swagger correlation id header name [**northbound**]
-  [#2556](https://github.com/telstra/open-kilda/pull/2556) Fix for round trip latency  (Issues: [#2554](https://github.com/telstra/open-kilda/issues/2554) [#580](https://github.com/telstra/open-kilda/issues/580)) [**gui**]

### Improvements:
-  [#2500](https://github.com/telstra/open-kilda/pull/2500) extend default flow tests [**tests**]
-  [#2446](https://github.com/telstra/open-kilda/pull/2446) add tests to smoke iteration [**tests**]
-  [#2514](https://github.com/telstra/open-kilda/pull/2514) fix logging for the setLinkBfd method [**tests**]
-  [#2519](https://github.com/telstra/open-kilda/pull/2519) add smoke_switches tag, and mark needed tests [**tests**]
-  [#2404](https://github.com/telstra/open-kilda/pull/2404) add possibility to use the `verifyRulesOnProtectedFlow` method when we have 1+ flows (Issue: [#2282](https://github.com/telstra/open-kilda/issues/2282)) [**tests**]
-  [#2346](https://github.com/telstra/open-kilda/pull/2346) Ensure ISL use link props data (Issue: [#2220](https://github.com/telstra/open-kilda/issues/2220)) [**storm-topologies**]
-  [#2540](https://github.com/telstra/open-kilda/pull/2540) fix test "Able to swap main and protected paths manually" [**tests**]

### Other changes:
-  [#2496](https://github.com/telstra/open-kilda/pull/2496) Make 'ovs-meters-enabled' configurable [**configuration**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.24.0...v1.25.0).

### Affected Components:
fl, gui, network, neo4j, flow, nb, isllatency, swmanager

### Upgrade notes:
Use following migration scripts to update db:
- [1.6 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.25.0/services/neo4j/migrations/1.6-encapsulation-type/1-upper-case-flow-encapsulation-type-changelog.xml)
- [1.7 migration-script.xml](https://github.com/telstra/open-kilda/blob/v1.25.0/services/neo4j/migrations/1.7-supported-transit-encapsulation/1-supported-transit-encapsulation-to-switch-features-changelog.xml)

In case of issues these rollback scripts should be executed:
- [1.6 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.25.0/services/neo4j/migrations/1.6-encapsulation-type/rollback.cql)
- [1.7 rollback.cql](https://github.com/telstra/open-kilda/blob/v1.25.0/services/neo4j/migrations/1.7-supported-transit-encapsulation/rollback.cql)

---

## v1.24.0 (19/06/2019)

### Features:
-  [#2304](https://github.com/telstra/open-kilda/pull/2304) Swap endpoints for flow [**api**][**northbound**][**storm-topologies**]
-  [#2471](https://github.com/telstra/open-kilda/pull/2471) RTL Part 3: Handle RTL packet in Floodlight (Issue: [#580](https://github.com/telstra/open-kilda/issues/580)) [**floodlight**][**storm-topologies**]
-  [#2472](https://github.com/telstra/open-kilda/pull/2472) Storm side vxlan rules [**storm-topologies**]
-  [#2350](https://github.com/telstra/open-kilda/pull/2350) Add an ability to choose encapsulation type in the CRUD flow over REST API. (Issue: [#647](https://github.com/telstra/open-kilda/issues/647)) [**northbound**][**storm-topologies**]
-  [#2417](https://github.com/telstra/open-kilda/pull/2417) Allow both default and VLAN tagged flows for the same port (Issue: [#2411](https://github.com/telstra/open-kilda/issues/2411)) [**floodlight**][**storm-topologies**]

### Bug Fixes:
-  [#2499](https://github.com/telstra/open-kilda/pull/2499) Restored cookie mismatch debug log level [**floodlight**]
-  [#2464](https://github.com/telstra/open-kilda/pull/2464) Fix switch validate and rules sync on meters unsupported switches (Issue: [#2453](https://github.com/telstra/open-kilda/issues/2453)) [**floodlight**][**storm-topologies**]
-  [#2469](https://github.com/telstra/open-kilda/pull/2469) Propagate API_HOST from lab-api into lab-service [**tests**]
-  [#2480](https://github.com/telstra/open-kilda/pull/2480) Fix to protected path tests [**tests**]
-  [#2483](https://github.com/telstra/open-kilda/pull/2483) Fix assumeProfile failing if first feature is profile-dependent [**tests**]
-  [#2484](https://github.com/telstra/open-kilda/pull/2484) Fix: Remove the notification stream going to the HS kafka bolt. [**storm-topologies**]
-  [#2487](https://github.com/telstra/open-kilda/pull/2487) Fix functional test for pinned flow [**tests**]
-  [#2488](https://github.com/telstra/open-kilda/pull/2488) Fix functional test for ISL min port speed feature [**tests**]
-  [#2428](https://github.com/telstra/open-kilda/pull/2428) Fix switch status update logging [**storm-topologies**]
-  [#2512](https://github.com/telstra/open-kilda/pull/2512) Partially revert PR2212 - deduplicate port event notifications (Issue: [#2212](https://github.com/telstra/open-kilda/issues/2212)) [**storm-topologies**]

### Improvements:
-  [#2434](https://github.com/telstra/open-kilda/pull/2434) check that system allows to pass traffic via default and vlan flows when they are on the same port (Issue: [#2433](https://github.com/telstra/open-kilda/issues/2433)) [**tests**]
-  [#2502](https://github.com/telstra/open-kilda/pull/2502) ignore test, functionality is not implemented yet [**tests**]
-  [#2454](https://github.com/telstra/open-kilda/pull/2454) Improve sync rules test to use path with max amount of switches (Issue: [#2453](https://github.com/telstra/open-kilda/issues/2453)) [**tests**]
-  [#2212](https://github.com/telstra/open-kilda/pull/2212) Improvements of Network topology dashboard logger. (Issue: [#1157](https://github.com/telstra/open-kilda/issues/1157)) [**storm-topologies**]
-  [#2477](https://github.com/telstra/open-kilda/pull/2477) Add test that slowly discovers switches one by one [**tests**]
-  [#2489](https://github.com/telstra/open-kilda/pull/2489) Small refactoring of self-loop ISL test [**tests**]

For the complete list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.23.0...v1.24.0).

### Affected Components:
router, fl, flow, reroute, swmanager, network, nb

---

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
