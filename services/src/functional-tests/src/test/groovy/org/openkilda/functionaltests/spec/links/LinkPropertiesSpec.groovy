package org.openkilda.functionaltests.spec.links

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.TestFixture
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.links.LinkPropsDto
import org.openkilda.testing.Constants

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Unroll

class LinkPropertiesSpec extends BaseSpecification {

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
        //make sure all costs are default
        database.resetCosts()
    }

    def "Empty list is returned if there are no properties"() {
        expect: "Get link properties is empty for no properties"
        northbound.getAllLinkProps().empty
    }

    @Ignore("This test is not valid in this implementation.")
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

        then: "Response with error is received"
        response.failures == 1
        response.messages.first() == "Can not parse input string: \"${linkProp.srcSwitch}\""
    }

    def "Unable to create link property with non-numeric values"() {
        when: "Try creating link property with non-numeric values"
        def linkProp = new LinkPropsDto("00:00:00:00:00:00:00:01", 1, "00:00:00:00:00:00:00:02", 1, ["cost": "1000L"])
        def response = northbound.updateLinkProps([linkProp])

        then: "Response with error is received"
        response.failures == 1
        response.messages.first() == "For input string: \"${linkProp.props["cost"]}\""
    }

    @Unroll
    @TestFixture(setup = "prepareLinkPropsForSearch", cleanup = "cleanLinkPropsAfterSearch")
    def "Searching for link props with #data.descr"() {
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

    def "Updating costs via link props actually updates costs on ISLs"() {
        given: "An ISL"
        def isl = topology.islsForActiveSwitches.first()

        when: "Update cost on ISL via link props"
        def cost = "12345"
        def linkProps = [islUtils.toLinkProps(isl, ["cost": cost])]
        northbound.updateLinkProps(linkProps)

        then: "Cost on ISL is really updated"
        database.getIslCost(isl) == cost.toInteger()

        and: "Cost on reverse ISL is not changed"
        database.getIslCost(isl.reversed) == Constants.DEFAULT_COST

        when: "Delete link props"
        northbound.deleteLinkProps(linkProps)

        then: "Cost on ISL is changed to the default value"
        database.getIslCost(isl) == Constants.DEFAULT_COST
    }

    def "Newly discovered link gets cost from link props"() {
        given: "An active link"
        def isl = topology.islsForActiveSwitches.first()

        and: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        and: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))
        northbound.deleteLink(islUtils.toLinkParameters(isl.reversed))
        assert !islUtils.getIslInfo(isl)
        assert !islUtils.getIslInfo(isl.reversed)

        and: "Set cost on the deleted link via link props"
        def cost = "12345"
        def linkProps = [islUtils.toLinkProps(isl, ["cost": cost])]
        northbound.updateLinkProps(linkProps)

        when: "Bring port up on the source switch to discover the deleted link"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        then: "The discovered link gets cost from link props"
        database.getIslCost(isl) == cost.toInteger()
        //TODO(ylobankov): Uncomment the check once issue #1954 is merged.
        //database.getIslCost(isl.reversed) == cost.toInteger()

        and: "Delete link props"
        northbound.deleteLinkProps(linkProps)
    }

    def prepareLinkPropsForSearch() {
        northbound.updateLinkProps(propsDataForSearch)
    }

    def cleanLinkPropsAfterSearch() {
        northbound.deleteLinkProps(propsDataForSearch)
    }
}
