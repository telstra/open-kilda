package org.openkilda.functionaltests.spec.grpc

import spock.lang.Unroll

class GrpcCommonSpec extends GrpcBaseSpecification {
    @Unroll
    def "Able to get switch status on the #sw.switchId switch"() {
        when: "Get switch status"
        def response = grpc.getSwitchStatus(sw.address)

        then: "Response is not null and needed fields are returned"
        response.serialNumber
        response.uptime
        response.kernel
        response.memUsage != null
        response.ssdUsage != null
        response.ethLinks
        response.builds
        response.cpuPercentage != null

        where:
        sw << getNoviflowSwitches("6.4")
    }
}
