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

    @Unroll("Switch stats are being logged for metric:#metric, tags:#tags")
    def "Switch stats are being logged"(metric, tags) {
        expect: "At least 1 rx/tx result in the past 5 minutes"
        otsdb.query(5.minutes.ago, metric, tags).dps.size() > 0

        where:
        [metric, tags] << [["pen.switch.rx-bytes", "pen.switch.rx-bits", "pen.switch.rx-packets",
                            "pen.switch.tx-bytes", "pen.switch.tx-bits","pen.switch.tx-packets"],
                           getSwitchIdTags()].combinations()
    }

    def getSwitchIdTags() {
        topology.activeSwitches.unique{it.ofVersion}.collect {
            [switchid: it.dpId.toOtsdFormat()]
        }
    }
}
