package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.info.event.SwitchInfoData

import groovy.transform.Memoized

@Tags(HARDWARE)
class GrpcBaseSpecification extends BaseSpecification {
    @Memoized
    List<SwitchInfoData> getNoviflowSwitches() {
        northbound.activeSwitches.findAll {
            // it is not working properly if version <= 6.4
            def matcher = it.description =~ /NW[0-9]+.([0-9].[0-9])/
            return matcher && matcher[0][1] > "6.4"
        }
    }
}
