package org.openkilda.functionaltests.spec.links

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.fixture.TestFixture
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.links.LinkPropsDto
import org.openkilda.testing.Constants
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared

import java.util.concurrent.TimeUnit

class LinkPropertiesSpec extends HealthCheckSpecification {
    @Autowired @Shared @Qualifier("northboundServiceImpl")
    NorthboundService northboundGlobal

    @Shared
    def propsDataForSearch = [
            new LinkPropsDto("0f:00:00:00:00:00:00:01", 1, "0f:00:00:00:00:00:00:02", 1, [:]),
            new LinkPropsDto("0f:00:00:00:00:00:00:01", 2, "0f:00:00:00:00:00:00:02", 1, [:]),
            new LinkPropsDto("0f:00:00:00:00:00:00:01", 2, "0f:00:00:00:00:00:00:02", 2, [:]),
            new LinkPropsDto("0f:00:00:00:00:00:00:03", 3, "0f:00:00:00:00:00:00:03", 3, [:]),
            new LinkPropsDto("0f:00:00:00:00:00:00:02", 1, "0f:00:00:00:00:00:00:01", 1, [:])
    ]

    //TODO(ylobankov): Actually this is abnormal behavior and we should have an error. But this test is aligned
    // with the current system behavior to verify that system is not hanging. Need to rework the test when system
    // behavior is fixed.
    @Tidy
    def "Link props are created with empty values when sending an invalid link props key"() {
        when: "Send link property request with invalid character"
        def linkPropsCreate = [new LinkPropsDto("0f:00:00:00:00:00:00:01", 1,
                "0f:00:00:00:00:00:00:02", 1, ["`cost": "700"])]
        def response = northbound.updateLinkProps(linkPropsCreate)

        then: "Response states that operation succeeded"
        response.successes == 1

        and: "Link props are created but with empty values"
        def linkProps = northboundGlobal.getLinkProps(null, 1, null, 1).findAll { it.srcSwitch.startsWith("0f") }
        linkProps.size() == 2
        linkProps.each { assert it.props.isEmpty() }

        cleanup: "Delete created link props"
        northbound.deleteLinkProps(linkPropsCreate)
    }

    @Tidy
    def "Unable to create link property with invalid switchId format"() {
        when: "Try creating link property with invalid switchId format"
        def linkProp = new LinkPropsDto("I'm invalid", 1, "0f:00:00:00:00:00:00:02", 1, [:])
        def response = northbound.updateLinkProps([linkProp])

        then: "Response with error is received"
        response.failures == 1
        response.messages.first() == "Can not parse input string: \"${linkProp.srcSwitch}\""
    }

    @Tidy
    def "Unable to create link property with non-numeric value for #key"() {
        when: "Try creating link property with non-numeric values"
        def nonNumericValue = "1000L"
        def linkProp = new LinkPropsDto("0f:00:00:00:00:00:00:01", 1, "0f:00:00:00:00:00:00:02", 1,
                [(key): nonNumericValue])
        northbound.updateLinkProps([linkProp])

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Can't create/update link props"
        errorDetails.errorDescription == "Bad ${key.replace("_"," ")} value '$nonNumericValue'"

        where:
        key << ["cost", "max_bandwidth"]
    }

    @Tidy
    @TestFixture(setup = "prepareLinkPropsForSearch", cleanup = "cleanLinkPropsAfterSearch")
    def "Searching for link props with #data.descr"() {
        when: "Get link properties with search query"
        def foundProps = northboundGlobal.getLinkProps(*data.params)
                .findAll { it.srcSwitch.startsWith("0f") } //exclude props from other labs

        then: "Returned props list match expected"
        foundProps.sort() == expected.sort()

        where:
        data << [
                [
                        descr : "src switch and src port (single result)",
                        params: [new SwitchId("0f:00:00:00:00:00:00:01"), 1, null, null],
                ],
                [
                        descr : "src switch and src port (multiple results)",
                        params: [new SwitchId("0f:00:00:00:00:00:00:01"), 2, null, null],
                ],
                [
                        descr : "dst switch and dst port (single result)",
                        params: [null, null, new SwitchId("0f:00:00:00:00:00:00:02"), 2]
                ],
                [
                        descr : "dst switch and dst port (multiple results)",
                        params: [null, null, new SwitchId("0f:00:00:00:00:00:00:02"), 1]
                ],
                [
                        descr : "src switch and src port (no results)",
                        params: [new SwitchId("0f:00:00:00:00:00:00:03"), 2, null, null]
                ],
                [
                        descr : "dst switch and dst port (no results)",
                        params: [null, null, new SwitchId("0f:00:00:00:00:00:00:03"), 2]
                ],
                [
                        descr : "src and dst switch (different switches)",
                        params: [new SwitchId("0f:00:00:00:00:00:00:01"), null,
                                 new SwitchId("0f:00:00:00:00:00:00:02"), null]
                ],
                [
                        descr : "src and dst switch (same switch)",
                        params: [new SwitchId("0f:00:00:00:00:00:00:03"), null,
                                 new SwitchId("0f:00:00:00:00:00:00:03"), null]
                ],
                [
                        descr : "src and dst port",
                        params: [null, 2, null, 1]
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

    @Tidy
    def "Updating cost, max bandwidth and description via link props actually updates cost, max bandwidth and description on ISLs"() {
        given: "An active ISL"
        def isl = topology.islsForActiveSwitches.first()
        def initialMaxBandwidth = islUtils.getIslInfo(isl).get().maxBandwidth

        when: "Create link props of ISL to update cost and max bandwidth on the forward and reverse directions"
        def costValue = "12345"
        def maxBandwidthValue = "54321"
        def descriptionValue = "Description test"
        def linkProps = [islUtils.toLinkProps(isl, [
                "cost": costValue,
                "max_bandwidth": maxBandwidthValue,
                "description": descriptionValue
        ])]
        northbound.updateLinkProps(linkProps)
        assert northbound.getLinkProps(topology.isls).size() == 2

        then: "Cost on forward and reverse ISLs is really updated"
        database.getIslCost(isl) == costValue.toInteger()
        database.getIslCost(isl.reversed) == costValue.toInteger()

        and: "Max bandwidth on forward and reverse ISLs is really updated as well"
        def updatedLinks = northbound.getAllLinks()
        islUtils.getIslInfo(updatedLinks, isl).get().maxBandwidth == maxBandwidthValue.toInteger()
        islUtils.getIslInfo(updatedLinks, isl.reversed).get().maxBandwidth == maxBandwidthValue.toInteger()

        and: "Description on forward and reverse ISLs is really updated as well"
        islUtils.getIslInfo(updatedLinks, isl).get().description == descriptionValue
        islUtils.getIslInfo(updatedLinks, isl.reversed).get().description == descriptionValue

        when: "Update link props on the forward direction of ISL to update cost one more time"
        def newCostValue = "345"
        northbound.updateLinkProps([islUtils.toLinkProps(isl, ["cost": newCostValue])])

        then: "Forward and reverse directions of the link props are really updated"
        northbound.getLinkProps(topology.isls).each {
            assert it.props.cost == newCostValue
        }

        and: "Cost on forward and reverse ISLs is really updated"
        database.getIslCost(isl) == newCostValue.toInteger()
        database.getIslCost(isl.reversed) == newCostValue.toInteger()

        when: "Delete link props"
        northbound.deleteLinkProps(linkProps)
        def linkPropsAreDeleted = true

        then: "Cost on ISLs is changed to the default value"
        database.getIslCost(isl) == Constants.DEFAULT_COST
        database.getIslCost(isl.reversed) == Constants.DEFAULT_COST

        and: "Max bandwidth on forward and reverse ISLs is changed to the initial value as well"
        def links = northbound.getAllLinks()
        islUtils.getIslInfo(links, isl).get().maxBandwidth == initialMaxBandwidth
        islUtils.getIslInfo(links, isl.reversed).get().maxBandwidth == initialMaxBandwidth

        and: "Description on forward and reverse ISLs are removed"
        islUtils.getIslInfo(links, isl).get().description == null
        islUtils.getIslInfo(links, isl.reversed).get().description == null

        cleanup:
        !linkPropsAreDeleted && northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    @Tags(SMOKE)
    def "Newly discovered link gets cost and max bandwidth from link props"() {
        given: "An active ISL"
        def isl = topology.islsForActiveSwitches.first()

        and: "Bring port down on the source switch"
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        TimeUnit.SECONDS.sleep(2) //receive any in-progress disco packets
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLink(isl).actualState == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).actualState == IslChangeType.FAILED
        }

        and: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        and: "Set cost and max bandwidth on the deleted link via link props"
        def costValue = "12345"
        def maxBandwidthValue = "54321"
        def linkProps = [islUtils.toLinkProps(isl, ["cost": costValue, "max_bandwidth": maxBandwidthValue])]
        northbound.updateLinkProps(linkProps)

        when: "Bring port up on the source switch to discover the deleted link"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
        def islIsUp = true

        then: "The discovered link gets cost from link props"
        database.getIslCost(isl) == costValue.toInteger()
        database.getIslCost(isl.reversed) == costValue.toInteger()

        and: "The discovered link gets max bandwidth from link props as well"
        def links = northbound.getAllLinks()
        islUtils.getIslInfo(links, isl).get().maxBandwidth == maxBandwidthValue.toInteger()
        islUtils.getIslInfo(links, isl.reversed).get().maxBandwidth == maxBandwidthValue.toInteger()

        cleanup: "Delete link props"
        if (!islIsUp) {
            antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
            Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
                assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
            }
        }
        //DELETE /link/props deletes props for both directions, even if only one direction specified
        linkProps && northbound.deleteLinkProps(linkProps)
    }

    def prepareLinkPropsForSearch() {
        northbound.updateLinkProps(propsDataForSearch)
    }

    def cleanLinkPropsAfterSearch() {
        northbound.deleteLinkProps(propsDataForSearch)
        Wrappers.wait(WAIT_OFFSET / 2) { assert northbound.getLinkProps(topology.isls).empty }
    }
}
