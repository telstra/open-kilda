package org.openkilda.functionaltests.spec.northbound.links

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.TestFixture
import org.openkilda.model.SwitchId
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
            new LinkPropsDto("00:00:00:00:00:00:00:01", 1, "00:00:00:00:00:00:00:02", 1, [:]),
            new LinkPropsDto("00:00:00:00:00:00:00:01", 2, "00:00:00:00:00:00:00:02", 1, [:]),
            new LinkPropsDto("00:00:00:00:00:00:00:01", 2, "00:00:00:00:00:00:00:02", 2, [:]),
            new LinkPropsDto("00:00:00:00:00:00:00:03", 3, "00:00:00:00:00:00:00:03", 3, [:])
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

    def "Valid error response is returned when sending invalid char in link props key"() {
        when: "Send link property request with invalid character"
        def response = northbound.updateLinkProps([new LinkPropsDto("00:00:00:00:00:00:00:01", 1,
                "00:00:00:00:00:00:00:02", 1, ['`cost': "700"])])

        then: "Response states that operation failed"
        response.failures == 1
        response.messages.first() == "Invalid request"
    }

    def "Unable to create link property with invalid switchId format"() {
        when: "Try creating link property with invalid switchId format"
        def linkProp = new LinkPropsDto("I'm invalid", 1, "00:00:00:00:00:00:00:02", 1, [:])
        def response = northbound.updateLinkProps([linkProp])

        then: "Kilda responds with error"
        response.failures == 1
        response.messages.first() == "Can not parse input string: \"${linkProp.srcSwitch}\""
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
                        params: [new SwitchId("00:00:00:00:00:00:00:01"), 1, null, null],
                ],
                [
                        descr : "src switch and src port (multiple results)",
                        params: [new SwitchId("00:00:00:00:00:00:00:01"), 2, null, null],
                ],
                [
                        descr : "dst switch and dst port",
                        params: [null, null, new SwitchId("00:00:00:00:00:00:00:02"), 2]
                ],
                [
                        descr : "dst switch and dst port (no results)",
                        params: [null, null, new SwitchId("00:00:00:00:00:00:00:03"), 2]
                ],
                [
                        descr : "src and dst switch",
                        params: [new SwitchId("00:00:00:00:00:00:00:01"), null,
                                 new SwitchId("00:00:00:00:00:00:00:02"), null]
                ],
                [
                        descr : "src and dst switch (same switch)",
                        params: [new SwitchId("00:00:00:00:00:00:00:03"), null,
                                 new SwitchId("00:00:00:00:00:00:00:03"), null]
                ],
                [
                        descr : "src and dst port",
                        params: [null, 2, null, 1]
                ],
                [
                        descr : "src and dst port (same port)",
                        params: [null, 1, null, 1]
                ],
                [
                        descr : "full match",
                        params: [new SwitchId(propsDataForSearch[0].srcSwitch), propsDataForSearch[0].srcPort,
                                 new SwitchId(propsDataForSearch[0].dstSwitch), propsDataForSearch[0].dstPort]
                ]
        ]
        expected = propsDataForSearch.findAll {
            (!data.params[0] || it.srcSwitch == data.params[0].toString()) &&
                    (!data.params[1] || it.srcPort == data.params[1]) &&
                    (!data.params[2] || it.dstSwitch == data.params[2].toString()) &&
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
