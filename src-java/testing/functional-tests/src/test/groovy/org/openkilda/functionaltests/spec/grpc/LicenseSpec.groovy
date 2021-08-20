package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE

import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.grpc.speaker.model.LicenseDto
import org.openkilda.messaging.error.MessageError

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Narrative("""NoviWare software license file is used to activate the basic and licensed features.
If you want to test full functionality then you have to perform the following manual tests:
    - set license by 'file name'. File name is a file with license on switch.
    - set license by 'license data' """)
class LicenseSpec extends GrpcBaseSpecification {
    @Tidy
    @Tags(HARDWARE)
    def "Not able to set incorrect license on #sw.hwSwString"() {
        when: "Try to set incorrect license key"
        String licenseFileName = "incorrectLicenseFileName.key"
        String incorrectLicense = "incorrect license data"
        grpc.setLicenseForSwitch(sw.address, new LicenseDto(incorrectLicense, licenseFileName))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == "Invalid license key."

        where:
        sw << getNoviflowSwitches()
    }
}
