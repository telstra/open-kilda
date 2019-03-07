package org.openkilda.functionaltests.spec.grpc

import org.openkilda.functionaltests.BaseSpecification

import groovy.util.logging.Slf4j
import spock.lang.Shared

@Slf4j
class GrpcCommonSpec extends BaseSpecification {
    @Shared
    String switchIp

    def setupOnce() {
        requireProfiles("hardware")
        def nFlowSwitch = northbound.activeSwitches.find { it.description =~ /NW[0-9]+.[0-9].[0-9]/ }
        def pattern = /(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\-){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/
        switchIp = (nFlowSwitch.address =~ pattern)[0].replaceAll("-", ".")
    }

    def "Able to get switch status"() {
        when: "Get switch status"
        def r = grpc.getSwitchStatus(switchIp)

        then: "Response is not null and needed fields are returned"
        r.serialNumber
        r.uptime
        r.kernel
        r.memUsage
        r.ssdUsage
        r.ethLinks
        r.builds
        r.cpuPercentage
    }
}
