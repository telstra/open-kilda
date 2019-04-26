package org.openkilda.functionaltests.spec.grpc

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.info.event.SwitchInfoData

import spock.lang.Shared

class GrpcBaseSpecification extends BaseSpecification{
    @Shared
    String switchIp
    @Shared
    SwitchInfoData nFlowSwitch

    def setupOnce() {
        requireProfiles("hardware")
        nFlowSwitch = northbound.activeSwitches.find { it.description =~ /NW[0-9]+.[0-9].[0-9]/ }
        switchIp = nFlowSwitch.address
    }
}
