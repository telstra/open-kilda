package org.openkilda.functionaltests.spec

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.otsdb.OtsdbQueryService

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Unroll
import spock.util.mop.Use

@Use(TimeCategory)
class OpenTsdbSpec extends BaseSpecification {

    @Autowired
    OtsdbQueryService otsdb
    @Autowired
    TopologyDefinition topology

    @Unroll("Stats are being logged for metric:#metric, tags:#tags")
    def "Basic stats are being logged"(metric, tags) {
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
}
