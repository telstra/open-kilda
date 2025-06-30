package org.openkilda.functionaltests.spec.links

import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.link.LinkPropertiesNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.links.LinkPropsDto
import org.openkilda.testing.Constants
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_ISLS_PROPERTIES
import static org.openkilda.testing.Constants.WAIT_OFFSET

class LinkPropertiesSpec extends HealthCheckSpecification {
    @Autowired @Shared @Qualifier("northboundServiceImpl")
    NorthboundService northboundGlobal
    @Autowired
    CleanupManager cleanupManager
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
    def "Link props are created with empty values when sending an invalid link props key"() {
        when: "Send link property request with invalid character"
        def linkPropsCreate = [new LinkPropsDto("0f:00:00:00:00:00:00:01", 1,
                "0f:00:00:00:00:00:00:02", 1, ["`cost": "700"])]
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES,
                {northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))})
        def response = northbound.updateLinkProps(linkPropsCreate)

        then: "Response states that operation succeeded"
        response.successes == 1

        and: "Link props are created but with empty values"
        def linkProps = northboundGlobal.getLinkProps(null, 1, null, 1).findAll { it.srcSwitch.startsWith("0f") }
        linkProps.size() == 2
        linkProps.each { assert it.props.isEmpty() }
    }

    def "Unable to create link property with invalid switchId format"() {
        when: "Try creating link property with invalid switchId format"
        def linkProp = new LinkPropsDto("I'm invalid", 1, "0f:00:00:00:00:00:00:02", 1, [:])
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES,
                {northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))})
        def response = northbound.updateLinkProps([linkProp])

        then: "Response with error is received"
        response.failures == 1
        response.messages.first() == "Can not parse input string: \"${linkProp.srcSwitch}\""
    }

    def "Unable to create link property with non-numeric value for #key"() {
        when: "Try creating link property with non-numeric values"
        def nonNumericValue = "1000L"
        def linkProp = new LinkPropsDto("0f:00:00:00:00:00:00:01", 1, "0f:00:00:00:00:00:00:02", 1,
                [(key): nonNumericValue])
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES,
                {northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))})
        northbound.updateLinkProps([linkProp])

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LinkPropertiesNotUpdatedExpectedError(~/Bad ${key.replace("_"," ")} value \'$nonNumericValue\'/).matches(exc)

        where:
        key << ["cost", "max_bandwidth"]
    }

    def "Searching for link props with #data.descr"() {
        setup:"Preparing system for the following search"
        northbound.updateLinkProps(propsDataForSearch)

        when: "Get link properties with search query"
        def foundProps = northboundGlobal.getLinkProps(*data.params)
                .findAll { it.srcSwitch.startsWith("0f") } //exclude props from other labs

        then: "Returned props list match expected"
        foundProps.sort() == expected.sort()

        cleanup:
        northbound.deleteLinkProps(propsDataForSearch)
        Wrappers.wait(WAIT_OFFSET / 2) { assert northbound.getLinkProps(topology.isls).empty }

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

    def "Updating cost, max bandwidth and description via link props actually updates cost, max bandwidth and description on ISLs"() {
        given: "An active ISL"
        def isl = isls.all().first()
        def initialMaxBandwidth = isl.getNbDetails().maxBandwidth

        when: "Create link props of ISL to update cost and max bandwidth on the forward and reverse directions"
        def costValue = 12345
        def maxBandwidthValue = "54321"
        def descriptionValue = "Description test"
        def linkProps = [ "cost": costValue.toString(), "max_bandwidth": maxBandwidthValue, "description": descriptionValue ]
        isl.update(linkProps)
        assert isls.all().getProps().size() == 2

        then: "Cost on forward and reverse ISLs is really updated"
        isl.getCostFromDb() == costValue
        isl.reversed.getCostFromDb() == costValue

        and: "Max bandwidth and description on forward and reverse ISLs is really updated as well"
        def updatedLinks = northbound.getAllLinks()
        [isl.getInfo(updatedLinks, false), isl.getInfo(updatedLinks, true)].each { islDetails ->
            assert islDetails.maxBandwidth == maxBandwidthValue.toInteger()
            assert islDetails.description == descriptionValue
            assert islDetails.cost == costValue
        }

        when: "Update link props on the forward direction of ISL to update cost one more time"
        def newCostValue = 345
        isl.updateCost(newCostValue)

        then: "Forward and reverse directions of the link props are really updated"
        def islsProps = isls.all().getProps()
        assert islsProps.size() == 2
        islsProps.each {
            assert it.props.cost == newCostValue.toString()
        }

        and: "Cost on forward and reverse ISLs is really updated"
        isl.getCostFromDb() == newCostValue
        isl.reversed.getCostFromDb() == newCostValue

        when: "Delete link props"
        isl.deleteProps(linkProps)

        then: "Cost on ISLs is changed to the default value"
        isl.getCostFromDb() == Constants.DEFAULT_COST
        isl.reversed.getCostFromDb() == Constants.DEFAULT_COST

        and: "Max bandwidth and description on forward and reverse ISLs is changed to the initial value as well"
        def links = northbound.getAllLinks()
        [isl.getInfo(links, false), isl.getInfo(links, true)].each { islDetails ->
            assert islDetails.maxBandwidth == initialMaxBandwidth
            assert islDetails.description == null

        }
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Newly discovered link gets cost and max bandwidth from link props"() {
        given: "An active ISL"
        def isl = isls.all().first()

        and: "Bring port down on the source switch"
        isl.srcEndpoint.down()
        TimeUnit.SECONDS.sleep(2) //receive any in-progress disco packets
        Wrappers.wait(WAIT_OFFSET) {
            assert isl.getNbDetails().actualState == FAILED
            assert isl.reversed.getNbDetails().actualState == FAILED
        }

        and: "Delete the link"
        isl.delete()
        !isl.isPresent()

        and: "Set cost and max bandwidth on the deleted link via link props"
        def costValue = 12345
        def maxBandwidthValue = 54321
        def linkProps = ["cost": costValue.toString(), "max_bandwidth": maxBandwidthValue.toString()]
        isl.update(linkProps)

        when: "Bring port up on the source switch to discover the deleted link"
        isl.srcEndpoint.up()
        isl.waitForStatus(DISCOVERED, discoveryInterval + WAIT_OFFSET)

        then: "The discovered link gets cost from link props"
        isl.getCostFromDb() == costValue
        isl.reversed.getCostFromDb() == costValue

        and: "The discovered link gets max bandwidth from link props as well"
        def links = northbound.getAllLinks()
        isl.getInfo(links).maxBandwidth == maxBandwidthValue
        isl.reversed.getInfo(links).maxBandwidth == maxBandwidthValue
    }
}
