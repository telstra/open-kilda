# Kafka topics
All messages transferred via kafka are serialized into json format.

## `kilda-test`
This is most used kafka topic into kilda project. It used into communications between kilda components (northbound,
topology engine, topology engine REST, workflow manager).

We can say that message divided on two part - first one is transport (used to address destination point), second
one - payload.

Example message:
```json
{
  "type": "COMMAND",
  "timestamp": 1511365147,
  "destination": "WFM",
  "correlation_id": "admin-request",
  "payload": {
    "type-specific": "payload"
  }
}
```
### `kilda-test` usage
* (W) Topology engine REST
* (R/W) Topology engine
* (R/W) Northbound
* (R/W) workflow manager
  * (R) OFEventSplitterTopology - OFEventSplitterBolt
  * (R) FlowTopology - SplitterBolt
  * (W) FlowTopology - NorthboundReplyBolt
  * (R) FlowTopology - TopologyEngineBolt
  * (R) FlowTopology - SpeakerBolt
  * (W) FlowTopology -TransactionBolt
  * (R) StatsTopology - SpeakerBolt
  * (R/W) CacheTopology - CacheBolt
  * (R) IslStatsTopology - IslStatsBolt

## `kilda.wfm.topo.updown` usage
* (W) FlowTopology - CrudBolt
* (R) CacheTopology - CacheBolt

## `kilda.wfm.topo.dump` usage
* FlowTopology--SplitterBolt (R)
* CacheTopology--CacheBolt (W)

## `kilda.speaker` usage
## `kilda-simulator` usage

## `opentsdb-topic` usage
* (R) OpenTSDBTopology - OpenTSDBFilterBolt

## `kilda.health.check` usage
* (R/W) OFEventSplitterTopology - HealthCheckBolt
* (R/W) OFEventWFMTopology -HealthCheckBolt

## `speaker.info`
* (W) OFEventSplitterTopology - OFEventSplitterBolt

## `speaker.command` usage
* (W) OFEventSplitterTopology - OFEventSplitterBolt

## `speaker.other` usage
* (W) OFEventSplitterTopology - OFEventSplitterBolt

## `speaker.info.other` - usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt

## `speaker.info.switch` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt

## `speaker.info.switch.updown` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt
* (R) OFEventWFMTopology - OFESwitchBolt

## `speaker.info.switch.other` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt

## `speaker.info.port` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt

## `speaker.info.port.updown` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt
* (R) OFEventWFMTopology- OFEPortBolt

## `speaker.info.port.other` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt

## `speaker.info.isl` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt

## `speaker.info.isl.updown` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt
* (R) OFEventWFMTopology - OFELinkBolt

## `speaker.info.isl.other` usage
* (W) OFEventSplitterTopology - InfoEventSplitterBolt
