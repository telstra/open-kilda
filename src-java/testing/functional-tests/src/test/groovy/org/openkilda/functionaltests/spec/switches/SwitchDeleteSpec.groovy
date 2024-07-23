package org.openkilda.functionaltests.spec.switches

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_SWITCH_PROPERTIES
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.SwitchIsInIllegalStateExpectedError
import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.ArpData
import org.openkilda.testing.service.traffexam.model.LldpData
import org.openkilda.testing.tools.ConnectedDevice

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared

import java.util.concurrent.TimeUnit
import jakarta.inject.Provider


class SwitchDeleteSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired
    @Shared
    CleanupManager cleanupManager
    @Autowired
    @Shared
    FlowFactory flowFactory


    def "Unable to delete a nonexistent switch"() {
        when: "Try to delete a nonexistent switch"
        northbound.deleteSwitch(NON_EXISTENT_SWITCH_ID, false)

        then: "Get 404 NotFound error"
        def exc = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError("Could not delete switch '$NON_EXISTENT_SWITCH_ID': 'Switch $NON_EXISTENT_SWITCH_ID not found.'",
                ~/Switch is not found./).matches(exc)
    }

    @Tags(SMOKE)
    def "Unable to delete an active switch"() {
        given: "An active switch"
        def switchId = topology.getActiveSwitches()[0].dpId

        when: "Try to delete the switch"
        northbound.deleteSwitch(switchId, false)

        then: "Get 400 BadRequest error because the switch must be deactivated first"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '$switchId': " +
                "'Switch '$switchId' is in illegal state. " +
                "Switch '$switchId' is in 'Active' state.'").matches(exc)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Unable to delete an inactive switch with active ISLs"() {
        given: "An inactive switch with ISLs"
        def sw = topology.getActiveSwitches()[0]
        def swIsls = topology.getRelatedIsls(sw)
        switchHelper.knockoutSwitch(sw, RW)

        when: "Try to delete the switch"
        northbound.deleteSwitch(sw.dpId, false)

        then: "Get 400 BadRequest error because the switch has ISLs"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '${sw.dpId}': " +
                "'Switch '${sw.dpId}' is in illegal state. " +
                "Switch '${sw.dpId}' has ${swIsls.size() * 2} active links. Unplug and remove them first.'").matches(exc)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Unable to delete an inactive switch with inactive ISLs (ISL ports are down)"() {
        given: "An inactive switch with ISLs"
        def sw = topology.getActiveSwitches()[0]
        def swIsls = topology.getRelatedIsls(sw)
        islHelper.breakIsls(swIsls)
        switchHelper.knockoutSwitch(sw, RW, false)

        when: "Try to delete the switch"
        northbound.deleteSwitch(sw.dpId, false)

        then: "Get 400 BadRequest error because the switch has ISLs"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '${sw.dpId}': " +
                "'Switch '${sw.dpId}' is in illegal state. " +
                "Switch '${sw.dpId}' has ${swIsls.size() * 2} inactive links. Remove them first.'").matches(exc)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    @IterationTags([@IterationTag(tags = [LOW_PRIORITY], take = 1)])
    def "Unable to delete an inactive switch with a #flowType flow assigned"() {
        given: "A flow going through a switch"
        flow.create()

        when: "Deactivate the switch"
        def swToDeactivate = topology.switches.find { it.dpId == flow.source.switchId }
        switchHelper.knockoutSwitch(swToDeactivate, RW)

        and: "Try to delete the switch"
        northbound.deleteSwitch(flow.source.switchId, false)

        then: "Got 400 BadRequest error because the switch has the flow assigned"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '${swToDeactivate.dpId}': " +
                "'Switch '${swToDeactivate.dpId}' is in illegal state. " +
                "Switch '${swToDeactivate.dpId}' has 1 assigned flows: [${flow.flowId}].'").matches(exc)

        where:
        flowType        | flow
        "single-switch" | flowFactory.getBuilder(switchPairs.singleSwitch().random()).build()
        "casual"        | flowFactory.getBuilder(switchPairs.all().neighbouring().random()).build()
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Able to delete an inactive switch without any ISLs"() {
        given: "An inactive switch without any ISLs"
        def sw = topology.getActiveSwitches()[0]
        //need to restore supportedTransitEncapsulation field after deleting sw
        def initSwProps = switchHelper.getCachedSwProps(sw.dpId)
        def swIsls = topology.getRelatedIsls(sw)
        // port down on all active ISLs on switch
        islHelper.breakIsls(swIsls)
        // delete all ISLs on switch
        swIsls.each { northbound.deleteLink(islUtils.toLinkParameters(it)) }
        // deactivate switch
        switchHelper.knockoutSwitch(sw, RW)

        when: "Try to delete the switch"
        cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES, {switchHelper.updateSwitchProperties(sw, initSwProps)})
        def response = northbound.deleteSwitch(sw.dpId, false)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.allSwitches.any { it.switchId == sw.dpId } }
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Able to delete an inactive switch with connected devices"() {
        given: "An inactive switch without any ISLs but with connected devices"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")
        def tg = topology.activeTraffGens[0]
        def sw = tg.switchConnected

        //need to restore supportedTransitEncapsulation field after deleting sw
        def initSwProps = switchHelper.getCachedSwProps(sw.dpId)
        def swIsls = topology.getRelatedIsls(sw)

        // enable connected devices on switch
        switchHelper.updateSwitchProperties(sw, initSwProps.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        // send LLDP and ARP packets
        def lldpData = LldpData.buildRandom()
        def arpData = ArpData.buildRandom()
        cleanupManager.addAction(OTHER, {database.removeConnectedDevices(sw.dpId)})
        new ConnectedDevice(traffExamProvider.get(), topology.getTraffGen(sw.dpId), [777]).withCloseable {
            it.sendLldp(lldpData)
            it.sendArp(arpData)
        }

        // LLDP and ARP connected devices are recognized and saved"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northboundV2.getConnectedDevices(sw.dpId).ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.size() == 1
                it[0].arp.size() == 1
            }
        }

        // port down on all active ISLs on switch
        islHelper.breakIsls(swIsls)
        // delete all ISLs on switch
        swIsls.each { northbound.deleteLink(islUtils.toLinkParameters(it)) }
        // deactivate switch
        switchHelper.knockoutSwitch(sw, RW)

        when: "Try to delete the switch"
        cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES, {switchHelper.updateSwitchProperties(sw, initSwProps)})
        def response = northbound.deleteSwitch(sw.dpId, false)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.allSwitches.any { it.switchId == sw.dpId } }
    }

    def "Able to delete an active switch with active ISLs if using force delete"() {
        given: "An active switch with active ISLs"
        def sw = topology.getActiveSwitches()[0]
        //need to restore supportedTransitEncapsulation field after deleting sw
        def initSwProps = switchHelper.getCachedSwProps(sw.dpId)
        def swIsls = topology.getRelatedIsls(sw)

        when: "Try to force delete the switch"
        def response = northbound.deleteSwitch(sw.dpId, true)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) {
            assert !northbound.allSwitches.any { it.switchId == sw.dpId }

            def links = northbound.getAllLinks()
            swIsls.each {
                assert !islUtils.getIslInfo(links, it)
                assert !islUtils.getIslInfo(links, it.reversed)
            }
        }

        cleanup: "Restore the switch, ISLs and reset costs"
        // restore switch
        def blockData = lockKeeper.knockoutSwitch(sw, RW)
        Wrappers.wait(WAIT_OFFSET) {
            flHelper.getFlsByMode(RW).each { fl ->
                assert fl.floodlightService.getSwitches().every { it.switchId != sw.dpId }
            }
        }
        switchHelper.reviveSwitch(sw, blockData)
        // restore ISLs
        swIsls.each { antiflap.portDown(it.srcSwitch.dpId, it.srcPort) }
        TimeUnit.SECONDS.sleep(antiflapMin)
        islHelper.restoreIsls(swIsls)
        initSwProps && switchHelper.updateSwitchProperties(sw, initSwProps)
        database.resetCosts(topology.isls)
    }
}
