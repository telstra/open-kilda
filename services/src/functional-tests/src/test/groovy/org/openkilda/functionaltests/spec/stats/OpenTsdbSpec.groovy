package org.openkilda.functionaltests.spec.stats

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.otsdb.OtsdbQueryService

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Unroll
import spock.util.mop.Use

@Use(TimeCategory)
@Narrative("Verify that basic stats logging happens.")
class OpenTsdbSpec extends BaseSpecification {
    private static final String DROP_RULE_COOKIE = "8000000000000001"
    private static final String VERIFICATION_BROADCAST_RULE_COOKIE = "8000000000000002"
    private static final String VERIFICATION_UNICAST_RULE_COOKIE = "8000000000000003"
    private static final String DROP_VERIFICATION_LOOP_RULE_COOKIE = "8000000000000004"

    @Autowired
    OtsdbQueryService otsdb
    @Autowired
    TopologyDefinition topology

    @Issue("https://github.com/telstra/open-kilda/issues/1434")
    @Unroll("Stats are being logged for metric:#metric, tags:#tags")
    def "Basic stats are being logged"(metric, tags) {
        requireProfiles("hardware") //due to #1434
        expect: "At least 1 result in the past 2 minutes"
        otsdb.query(2.minutes.ago, metric, tags).dps.size() > 0

        where:
        [metric, tags] << ([
                ["pen.switch.rx-bytes", "pen.switch.rx-bits", "pen.switch.rx-packets",
                 "pen.switch.tx-bytes", "pen.switch.tx-bits", "pen.switch.tx-packets"],
                uniqueSwitches.collect { [switchid: it.dpId.toOtsdFormat()] }].combinations()
                + [["pen.isl.latency"], uniqueSwitches.collect { [src_switch: it.dpId.toOtsdFormat()] }].combinations()
                + [["pen.isl.latency"], uniqueSwitches.collect { [dst_switch: it.dpId.toOtsdFormat()] }].combinations())
    }

    def getUniqueSwitches() {
        topology.activeSwitches.unique { it.ofVersion }
    }

    @Unroll("Stats are being logged for metric:#metric, tags:#tags")
    def "Stats for default rules"(metric, tags) {
        expect: "At least 1 result in the past 2 minutes"
        otsdb.query(2.minutes.ago, metric, tags).dps.size() > 0

        where:
        [metric, tags] << ([
                ["pen.switch.flow.system.packets", "pen.switch.flow.system.bytes", "pen.switch.flow.system.bits"],
                [[cookie: DROP_RULE_COOKIE],
                 [cookie: VERIFICATION_BROADCAST_RULE_COOKIE],
                 [cookie: VERIFICATION_UNICAST_RULE_COOKIE],
                 [cookie: DROP_VERIFICATION_LOOP_RULE_COOKIE]]
        ].combinations())
    }
}
