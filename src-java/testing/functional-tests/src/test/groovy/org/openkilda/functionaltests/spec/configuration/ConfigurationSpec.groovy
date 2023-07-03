package org.openkilda.functionaltests.spec.configuration

import org.openkilda.functionaltests.error.NonExistingEncapsulationTypeExpectedError

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.EGRESS_RULE_MULTI_TABLE_ID
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""
Kilda configuration is a special lever that allows to change default flow encapsulation type while creating.
This spec assumes that 'transit_vlan' is always default type
""")
@Isolated //kilda config updates
class ConfigurationSpec extends HealthCheckSpecification {
    @Shared
    FlowEncapsulationType defaultEncapsulationType = FlowEncapsulationType.TRANSIT_VLAN

    @Tidy
    def "System takes into account default flow encapsulation type while creating a flow"() {
        when: "Create a flow without encapsulation type"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { swP ->
            [swP.src, swP.dst].every { sw -> switchHelper.isVxlanEnabled(sw.dpId) }
        }
        assumeTrue(switchPair != null, "Unable to find required switch pair in topology")
        def flow1 = flowHelperV2.randomFlow(switchPair)
        flow1.encapsulationType = null
        northboundV2.addFlow(flow1)

        then: "Flow is created with current default encapsulation type(transit_vlan)"
        northboundV2.getFlow(flow1.flowId).encapsulationType == defaultEncapsulationType.toString().toLowerCase()

        when: "Update default flow encapsulation type"
        def newFlowEncapsulationType = FlowEncapsulationType.VXLAN
        def updateResponse = northbound.updateKildaConfiguration(
                new KildaConfigurationDto(flowEncapsulationType: newFlowEncapsulationType))

        then: "Correct response is returned"
        updateResponse.flowEncapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        and: "Kilda configuration is really updated"
        northbound.getKildaConfiguration().flowEncapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        when: "Create a flow without encapsulation type"
        def flow2 = flowHelperV2.randomFlow(switchPair, false, [flow1])
        flow2.encapsulationType = null
        northboundV2.addFlow(flow2)

        then: "Flow is created with new default encapsulation type(vxlan)"
        northboundV2.getFlow(flow2.flowId).encapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        cleanup: "Restore default configuration and delete the flow"
        newFlowEncapsulationType && northbound.updateKildaConfiguration(
                new KildaConfigurationDto(flowEncapsulationType: defaultEncapsulationType))
        [flow1, flow2].each { it && flowHelper.deleteFlow(it.flowId) }
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System doesn't allow to update kilda configuration with wrong flow encapsulation type"() {
        when: "Try to set wrong flow encapsulation type"
        def incorrectValue = "TEST"
        northbound.updateKildaConfiguration(new KildaConfigurationDto(flowEncapsulationType: incorrectValue))

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new NonExistingEncapsulationTypeExpectedError(incorrectValue).matches(e)
        cleanup: "Restore default configuration"
        if (!e) {
            northbound.updateKildaConfiguration(
                    new KildaConfigurationDto(flowEncapsulationType: defaultEncapsulationType))
        }
    }

    @Tidy
    @Tags(VIRTUAL)
    def "System takes into account default multi table value while connecting a new switch"() {
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")

        expect: "Already added switch was discovered according to the multi table field in kilda configuration"
        def initConf = northbound.getKildaConfiguration()
        def sw = topology.activeSwitches.first()
        def isls = topology.getRelatedIsls(sw)
        //need to restore supportedTransitEncapsulation field after deleting sw from BD
        def initSwProps = switchHelper.getCachedSwProps(sw.dpId)
        assert northbound.getSwitchProperties(sw.dpId).multiTable == initConf.useMultiTable
        def islRules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
            new Cookie(it.cookie).getType() == CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES
        }
        with(islRules) { rules ->
            rules.size() == isls.size()
            rules*.instructions.goToTable.unique() == [(short) EGRESS_RULE_MULTI_TABLE_ID]  // 4 - egress table id
            islRules*.match.inPort.sort() == isls*.srcPort.collect { it.toString() }.sort()
        }

        when: "Disconnect one of the switches and remove it from DB. Pretend this switch never existed"
        //https://github.com/telstra/open-kilda/issues/4130
        withPool {
            isls.eachParallel { Isl isl ->
                northboundV2.updatePortProperties(isl.srcSwitch.dpId, isl.srcPort,
                        new PortPropertiesDto(discoveryEnabled: false))
                northboundV2.updatePortProperties(isl.dstSwitch.dpId,isl.dstPort,
                        new PortPropertiesDto(discoveryEnabled: false))
            }
        }
        def portDiscoveryIsEnabled = false
        def blockData = switchHelper.knockoutSwitch(sw, RW, true)
        isls.each { northbound.deleteLink(islUtils.toLinkParameters(it)) }
        wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            isls.each {
                assert !islUtils.getIslInfo(links, it).present
                assert !islUtils.getIslInfo(links, it.reversed).present
            }
        }
        northbound.deleteSwitch(sw.dpId, false)

        and: "Update the multi table field in kilda configuration"
        def newMultiTableValue = !initConf.useMultiTable
        northbound.updateKildaConfiguration(northbound.getKildaConfiguration().tap {
            it.useMultiTable = newMultiTableValue
        })

        and: "New switch connects"
        switchHelper.reviveSwitch(sw, blockData, false)
        withPool {
            isls.eachParallel { Isl isl ->
                northboundV2.updatePortProperties(isl.srcSwitch.dpId, isl.srcPort,
                        new PortPropertiesDto(discoveryEnabled: true))
                northboundV2.updatePortProperties(isl.dstSwitch.dpId,isl.dstPort,
                        new PortPropertiesDto(discoveryEnabled: true))
            }
        }
        wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            withPool {
                isls.eachParallel { Isl isl ->
                    assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
                    assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
                }
            }
        }
        def switchIsActivated = true
        portDiscoveryIsEnabled = true

        then: "Switch is added with switch property according to the kilda configuration"
        northbound.getSwitchProperties(sw.dpId).multiTable == newMultiTableValue

        cleanup: "Revert system to origin state"
        blockData && !switchIsActivated && switchHelper.reviveSwitch(sw, blockData, false)
        if (isls && !portDiscoveryIsEnabled) {
            withPool {
                isls.eachParallel { Isl isl ->
                    northboundV2.updatePortProperties(isl.srcSwitch.dpId, isl.srcPort,
                            new PortPropertiesDto(discoveryEnabled: true))
                    northboundV2.updatePortProperties(isl.dstSwitch.dpId,isl.dstPort,
                            new PortPropertiesDto(discoveryEnabled: true))
                }
            }
        }
        initConf && northbound.updateKildaConfiguration(initConf)
        initSwProps && switchHelper.updateSwitchProperties(sw, initSwProps)
        wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
        }
        wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
    }
}
