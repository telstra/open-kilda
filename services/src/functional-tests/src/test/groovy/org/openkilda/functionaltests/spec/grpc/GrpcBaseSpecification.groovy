package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.info.event.SwitchInfoData

import groovy.transform.Memoized

@Tags(HARDWARE)
class GrpcBaseSpecification extends BaseSpecification {

    /**
     * Get all noviflow switches based on minimal firmware version required.
     * 
     * @param minVersion include only numeric part, e.g 6.4, 6.5, 6.6
     */
    @Memoized
    List<SwitchInfoData> getNoviflowSwitches(Float minVersion) {
        northbound.activeSwitches.findAll {
            def matcher = it.description =~ /NW[0-9]+\.([0-9]\.[0-9])/
            return matcher && matcher[0][1].toFloat() > minVersion
        }
    }
}
