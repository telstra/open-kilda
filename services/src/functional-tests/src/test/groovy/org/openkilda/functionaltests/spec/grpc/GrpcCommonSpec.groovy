package org.openkilda.functionaltests.spec.grpc

import groovy.util.logging.Slf4j

@Slf4j
class GrpcCommonSpec extends GrpcBaseSpecification {
    def "Able to get switch status"() {
        when: "Get switch status"
        def response = grpc.getSwitchStatus(switchIp)

        then: "Response is not null and needed fields are returned"
        response.serialNumber
        response.uptime
        response.kernel
        response.memUsage != null
        response.ssdUsage != null
        response.ethLinks
        response.builds
        response.cpuPercentage != null
    }
}
