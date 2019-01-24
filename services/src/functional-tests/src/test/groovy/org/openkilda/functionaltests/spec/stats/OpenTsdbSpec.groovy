package org.openkilda.functionaltests.spec.stats

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.Constants.DefaultRule

import groovy.time.TimeCategory
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Unroll
import spock.util.mop.Use

@Use(TimeCategory)
@Narrative("Verify that basic stats logging happens.")
class OpenTsdbSpec extends BaseSpecification {

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

    @Ignore("Not stable. Under investigation")
    @Unroll("Stats are being logged for metric:#metric, tags:#tags")
    def "Stats for default rules"(metric, tags) {
        expect: "At least 1 result in the past 2 minutes"
        otsdb.query(2.minutes.ago, metric, tags).dps.size() > 0

        where:
        [metric, tags] << ([
                ["pen.switch.flow.system.packets", "pen.switch.flow.system.bytes", "pen.switch.flow.system.bits"],
                [[cookieHex: DefaultRule.DROP_RULE.toHexString()],
                 [cookieHex: DefaultRule.VERIFICATION_BROADCAST_RULE.toHexString()],
                 [cookieHex: DefaultRule.VERIFICATION_UNICAST_RULE.toHexString()],
                 [cookieHex: DefaultRule.DROP_LOOP_RULE.toHexString()]]
        ].combinations())
    }
}
