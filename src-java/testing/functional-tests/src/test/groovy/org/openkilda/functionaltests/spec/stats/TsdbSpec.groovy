package org.openkilda.functionaltests.spec.stats

import groovy.transform.Memoized
import org.openkilda.functionaltests.model.stats.SwitchStats
import org.openkilda.functionaltests.model.stats.SwitchStatsMetric
import org.openkilda.functionaltests.model.stats.SystemStats
import org.openkilda.model.SwitchId
import org.springframework.beans.factory.annotation.Autowired

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags

import groovy.time.TimeCategory
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll
import spock.util.mop.Use

import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_ACTION_SET_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_APPLY_ACTION_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_GROUP_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_INVALID_TTL_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_NO_MATCH_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_PACKET_OUT_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_TOTAL_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_IN_TOTAL_PACKETS_DATAPLANE
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_OUT_ETH_0
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_OUT_TOTAL_PACKETS_DATAPLANE
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.PACKET_OUT_TOTAL_PACKETS_HOST
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.STATE
import static org.openkilda.functionaltests.model.stats.SystemStatsMetric.FLOW_SYSTEM_METER_BITS
import static org.openkilda.functionaltests.model.stats.SystemStatsMetric.FLOW_SYSTEM_METER_BYTES
import static org.openkilda.functionaltests.model.stats.SystemStatsMetric.FLOW_SYSTEM_METER_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.RX_BITS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.RX_BYTES
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.RX_PACKETS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.TX_BITS
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.TX_BYTES
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.TX_PACKETS
import static org.openkilda.testing.Constants.DefaultRule.VERIFICATION_BROADCAST_RULE


@Use(TimeCategory)
@Narrative("Verify that basic stats logging happens.")
@Tags([SMOKE_SWITCHES])
class TsdbSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    SwitchStats switchStats
    @Shared
    HashMap<SwitchId, SwitchStats> statsMap;
    @Shared
    @Autowired
    SystemStats systemStats
    @Shared
    List<SwitchStatsMetric> hardwareOnlySwitchStats = [PACKET_IN_TOTAL_PACKETS,
                                   PACKET_IN_TOTAL_PACKETS_DATAPLANE,
                                   PACKET_IN_NO_MATCH_PACKETS,
                                   PACKET_IN_APPLY_ACTION_PACKETS,
                                   PACKET_IN_INVALID_TTL_PACKETS,
                                   PACKET_IN_ACTION_SET_PACKETS,
                                   PACKET_IN_GROUP_PACKETS,
                                   PACKET_IN_PACKET_OUT_PACKETS,
                                   PACKET_OUT_TOTAL_PACKETS_DATAPLANE,
                                   PACKET_OUT_TOTAL_PACKETS_HOST,
                                   PACKET_OUT_ETH_0]

    @Override
    def setupSpec() {
        withPool {
            statsMap = uniqueSwitches.collectParallel
            {[it, switchStats.of(it, SwitchStatsMetric.values() as List - STATE - hardwareOnlySwitchStats, 15)]}
                    .collectEntries{[(it[0]): it[1]]}
        }
    }

    @Tidy
    @Unroll("Stats are being logged for TX/RX metric:#metric")
    @Tags([TOPOLOGY_DEPENDENT, SMOKE])
    def "Basic stats are being logged"(switchId, metric) {
        expect: "At least 1 result in the past 15 minutes"
        assert !statsMap.get(switchId).get(metric).getDataPoints().isEmpty()

        where:
        [metric, switchId] << (
        [[RX_PACKETS,
         RX_BYTES,
         RX_BITS,
         TX_PACKETS,
         TX_BYTES,
         TX_BITS], getUniqueSwitches()].combinations())
    }

    @Tidy
    @Unroll("Stats are being logged for verification broadcast rule metric:#metric")
    @Tags([TOPOLOGY_DEPENDENT, SMOKE])
    def "Basic stats are being logged"(switchId, metric) {
        expect: "At least 1 result in the past 15 minutes"
        assert !statsMap.get(switchId).get(metric, VERIFICATION_BROADCAST_RULE.toHexString()).getDataPoints().isEmpty()

        where:
        [metric, switchId] << (
                [[SwitchStatsMetric.FLOW_SYSTEM_PACKETS,
                  SwitchStatsMetric.FLOW_SYSTEM_BYTES,
                  SwitchStatsMetric.FLOW_SYSTEM_BITS], getUniqueSwitches()].combinations())
    }

    @Tidy
    @Tags(HARDWARE)
    @Unroll("Stats are being logged for metric:#metric")
    def "Basic stats are being logged (10min interval)"(metric) {
        expect: "At least 1 result in the past 15 minutes"
        assert !systemStats.of(metric).get(VERIFICATION_BROADCAST_RULE.toHexString()).getDataPoints().isEmpty()

        where:
        metric << [FLOW_SYSTEM_METER_PACKETS, FLOW_SYSTEM_METER_BYTES, FLOW_SYSTEM_METER_BITS]

    }

    @Tidy
    @Unroll("GRPC stats are being logged for metric:#metric, sw:#sw.hwSwString")
    @Tags([HARDWARE])
    def "GRPC stats are being logged"(metric, sw) {
        assumeTrue(northbound.getFeatureToggles().collectGrpcStats,
"This test is skipped because 'collectGrpcStats' is disabled")
        expect: "At least 1 result in the past 15 minutes"
        assert !switchStats.of(sw.getDpId(), [metric], 15).get(metric).getDataPoints().isEmpty()

        where:
        [metric, sw] << ([hardwareOnlySwitchStats, getNoviflowSwitches()].combinations())
    }

    @Memoized
    def getUniqueSwitches() {
        topology.activeSwitches.unique { it.hwSwString }.collect {it.getDpId()}
    }

    @Memoized
    def getNoviflowSwitches() {
        topology.activeSwitches.findAll { it.noviflow }.unique { it.hwSwString }
    }
}
