package org.openkilda.functionaltests.model.stats

enum SwitchStatsMetric {
    FLOW_SYSTEM_PACKETS("flow.system.packets"),
    FLOW_SYSTEM_BYTES("flow.system.bytes"),
    FLOW_SYSTEM_BITS("flow.system.bits"),
    RX_PACKETS("rx-packets"),
    RX_BYTES("rx-bytes"),
    RX_BITS("rx-bits"),
    TX_PACKETS("tx-packets"),
    TX_BYTES("tx-bytes"),
    TX_BITS("tx-bits"),
    PACKET_IN_TOTAL_PACKETS("packet-in.total-packets"),
    PACKET_IN_TOTAL_PACKETS_DATAPLANE("packet-in.total-packets.dataplane"),
    PACKET_IN_NO_MATCH_PACKETS("packet-in.no-match.packets"),
    PACKET_IN_APPLY_ACTION_PACKETS("packet-in.apply-action.packets"),
    PACKET_IN_INVALID_TTL_PACKETS("packet-in.invalid-ttl.packets"),
    PACKET_IN_ACTION_SET_PACKETS("packet-in.action-set.packets"),
    PACKET_IN_GROUP_PACKETS("packet-in.group.packets"),
    PACKET_IN_PACKET_OUT_PACKETS("packet-in.packet-out.packets"),
    PACKET_OUT_TOTAL_PACKETS_DATAPLANE("packet-out.total-packets.dataplane"),
    PACKET_OUT_TOTAL_PACKETS_HOST("packet-out.total-packets.host"),
    PACKET_OUT_ETH_0("packet-out.eth0-interface-up"),
    STATE("state")

    final String metric;
    public final static String prefix = "switch."

    SwitchStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + this.metric
    }
}
