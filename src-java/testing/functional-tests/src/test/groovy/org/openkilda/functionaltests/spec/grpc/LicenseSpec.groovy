package org.openkilda.functionaltests.spec.grpc

import org.openkilda.functionaltests.error.WrongLicenseKeyExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.grpc.speaker.model.LicenseDto
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE

@Narrative("""NoviWare software license file is used to activate the basic and licensed features.
If you want to test full functionality then you have to perform the following manual tests:
    - set license by 'file name'. File name is a file with license on switch.
    - set license by 'license data' """)
class LicenseSpec extends GrpcBaseSpecification {
    @Tags(HARDWARE)
    @Ignore("https://github.com/telstra/open-kilda/issues/4592")
    def "Not able to set incorrect license on #sw.hardware-#sw.software"() {
        when: "Try to set incorrect license key"
        String licenseFileName = "incorrectLicenseFileName.key"
        String incorrectLicense = "incorrect license data"
        grpc.setLicenseForSwitch(sw.address, new LicenseDto(incorrectLicense, licenseFileName))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new WrongLicenseKeyExpectedError().matches(exc)

        where:
        sw << getNoviflowSwitches()
    }
}
