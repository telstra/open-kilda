package org.openkilda.functionaltests.spec.northbound

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired

class LinkPropertiesSpec extends BaseSpecification {
    @Autowired
    NorthboundService northbound

    def setupOnce() {
        //clear any existing properties before test starts
        def allLinkProps = northbound.getAllLinkProps()
        northbound.deleteLinkProps(allLinkProps)
    }

    def "Empty list is returned if no properties"() {
        expect: "Get link properties is empty for no properties"
        northbound.getAllLinkProps().empty
    }
}
