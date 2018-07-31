package org.openkilda.functionaltests.spec.northbound

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.TestFixture
import org.openkilda.northbound.dto.links.LinkPropsDto
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared
import spock.lang.Unroll

class LinkPropertiesSpec extends BaseSpecification {
    @Autowired
    NorthboundService northbound

    @Shared
    def propsDataForSearch = [
            new LinkPropsDto("sw1", 1, "sw2", 1, [:]),
            new LinkPropsDto("sw1", 2, "sw2", 1, [:]),
            new LinkPropsDto("sw1", 2, "sw2", 2, [:]),
            new LinkPropsDto("sw3", 3, "sw3", 3, [:])
    ]

    def setupOnce() {
        //clear any existing properties before tests start
        def allLinkProps = northbound.getAllLinkProps()
        northbound.deleteLinkProps(allLinkProps)
    }

    def "Empty list is returned if no properties"() {
        expect: "Get link properties is empty for no properties"
        northbound.getAllLinkProps().empty
    }

    @Unroll
    @TestFixture(setup = "prepareLinkPropsForSearch", cleanup = "cleanLinkPropsAfterSearch")
    def "Test searching for link props with #data.descr"() {
        when: "Get link properties with search query"
        def foundProps = northbound.getLinkProps(*data.params)

        then: "Returned props list match expected"
        foundProps.sort() == expected.sort()

        where:
        data << [
                [
                        descr : "src switch and src port (single result)",
                        params: ["sw1", 1, null, null],
                ],
                [
                        descr : "src switch and src port (multiple results)",
                        params: ["sw1", 2, null, null],
                ],
                [
                        descr : "dst switch and dst port",
                        params: [null, null, "sw2", 2]
                ],
                [
                        descr : "dst switch and dst port (no results)",
                        params: [null, null, "sw3", 2]
                ],
                [
                        descr : "src and dst switch",
                        params: ["sw1", null, "sw2", null]
                ],
                [
                        descr : "src and dst switch (same switch)",
                        params: ["sw3", null, "sw3", null]
                ],
                [
                        descr : "src and dst port",
                        params: [null, 2, null, 1]
                ],
                [
                        descr : "src and dst port (same port)",
                        params: [null, 1, null, 1]
                ]
        ]
        expected = propsDataForSearch.findAll {
            (!data.params[0] || it.srcSwitch == data.params[0]) &&
                    (!data.params[1] || it.srcPort == data.params[1]) &&
                    (!data.params[2] || it.dstSwitch == data.params[2]) &&
                    (!data.params[3] || it.dstPort == data.params[3])
        }
    }

    def prepareLinkPropsForSearch() {
        northbound.updateLinkProps(propsDataForSearch)
    }

    def cleanLinkPropsAfterSearch() {
        northbound.deleteLinkProps(propsDataForSearch)
    }
}
