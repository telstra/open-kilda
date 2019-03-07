package org.openkilda.functionaltests.spec.grpc

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.grpc.speaker.model.LicenseDto
import org.openkilda.messaging.error.MessageError

import groovy.util.logging.Slf4j
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("""NoviWare software license file is used to activate the basic and licensed features.
If you want to test full functionality then you have to perform the following manual tests:
    - set license by 'file name'. File name is a file with license on switch.
    - set license by 'license data' """)
class LicenseSpec extends BaseSpecification {
    @Shared
    String switchIp

    def setupOnce() {
        requireProfiles("hardware")
        def nFlowSwitch = northbound.activeSwitches.find { it.description =~ /NW[0-9]+.[0-9].[0-9]/ }
        def pattern = /(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\-){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/
        switchIp = (nFlowSwitch.address =~ pattern)[0].replaceAll("-", ".")
    }

    def "Not able to set incorrect license"() {
        when: "Try to set incorrect license key"
        String licenseFileName = "incorrectLicenseFileName.key"
        String incorrectLicense = "incorrect license data"
        grpc.setLicenseForSwitch(switchIp, new LicenseDto(incorrectLicense, licenseFileName))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == "Invalid license key."
    }
}
