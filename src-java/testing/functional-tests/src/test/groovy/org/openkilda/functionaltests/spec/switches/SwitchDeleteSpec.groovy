package org.openkilda.functionaltests.spec.switches

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
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.ArpData
import org.openkilda.testing.service.traffexam.model.LldpData
import org.openkilda.testing.tools.ConnectedDevice

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared

import javax.inject.Provider
import java.util.concurrent.TimeUnit


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
        def sw = switches.all().first()

        when: "Try to delete the switch"
        sw.delete(false)

        then: "Get 400 BadRequest error because the switch must be deactivated first"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '$sw.switchId': " +
                "'Switch '$sw.switchId' is in illegal state. " +
                "Switch '$sw.switchId' is in 'Active' state.'").matches(exc)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Unable to delete an inactive switch with active ISLs"() {
        given: "An inactive switch with ISLs"
        def sw = switches.all().first()
        def swIsls = topology.getRelatedIsls(sw.switchId)
        sw.knockout(RW)

        when: "Try to delete the switch"
        sw.delete(false)

        then: "Get 400 BadRequest error because the switch has ISLs"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '${sw.switchId}': " +
                "'Switch '${sw.switchId}' is in illegal state. " +
                "Switch '${sw.switchId}' has ${swIsls.size() * 2} active links. Unplug and remove them first.'").matches(exc)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Unable to delete an inactive switch with inactive ISLs (ISL ports are down)"() {
        given: "An inactive switch with ISLs"
        def sw = switches.all().first()
        def swIsls = topology.getRelatedIsls(sw.switchId)
        islHelper.breakIsls(swIsls)
        sw.knockout(RW, false)

        when: "Try to delete the switch"
        sw.delete(false)

        then: "Get 400 BadRequest error because the switch has ISLs"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '${sw.switchId}': " +
                "'Switch '${sw.switchId}' is in illegal state. " +
                "Switch '${sw.switchId}' has ${swIsls.size() * 2} inactive links. Remove them first.'").matches(exc)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    @IterationTags([@IterationTag(tags = [LOW_PRIORITY], take = 1)])
    def "Unable to delete an inactive switch with a #flowType flow assigned"() {
        given: "A flow going through a switch"
        flow.create()

        when: "Deactivate the switch"
        def swToDeactivate = switches.all().findSpecific(flow.source.switchId)
        swToDeactivate.knockout(RW)

        and: "Try to delete the switch"
        swToDeactivate.delete(false)

        then: "Got 400 BadRequest error because the switch has the flow assigned"
        def exc = thrown(HttpClientErrorException)
        new SwitchIsInIllegalStateExpectedError("Could not delete switch '${swToDeactivate.switchId}': " +
                "'Switch '${swToDeactivate.switchId}' is in illegal state. " +
                "Switch '${swToDeactivate.switchId}' has 1 assigned flows: [${flow.flowId}].'").matches(exc)

        where:
        flowType        | flow
        "single-switch" | flowFactory.getBuilder(switchPairs.singleSwitch().random()).build()
        "casual"        | flowFactory.getBuilder(switchPairs.all().neighbouring().random()).build()
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Able to delete an inactive switch without any ISLs"() {
        given: "An inactive switch without any ISLs"
        def sw = switches.all().first()
        //need to restore supportedTransitEncapsulation field after deleting sw
        def initSwProps = sw.getProps()
        def swIsls = topology.getRelatedIsls(sw.switchId)
        // port down on all active ISLs on switch
        islHelper.breakIsls(swIsls)
        // delete all ISLs on switch
        swIsls.each { northbound.deleteLink(islUtils.toLinkParameters(it)) }
        // deactivate switch
        sw.knockoutWithoutLinksCheckWhenRecover(RW)

        when: "Try to delete the switch"
        cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES, { northbound.updateSwitchProperties(sw.switchId, initSwProps) })
        def response = sw.delete(false)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.allSwitches.find { it.switchId == sw.switchId } }
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Able to delete an inactive switch with connected devices"() {
        given: "An inactive switch without any ISLs but with connected devices"
        def sw = switches.all().withTraffGens().first()
        TraffGen tg = topology.getTraffGen(sw.switchId)

        //need to restore supportedTransitEncapsulation field after deleting sw
        def initSwProps = sw.getCashedProps()
        def swIsls = topology.getRelatedIsls(sw.switchId)

        // enable connected devices on switch
        sw.updateProperties(initSwProps.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        // send LLDP and ARP packets
        def lldpData = LldpData.buildRandom()
        def arpData = ArpData.buildRandom()
        cleanupManager.addAction(OTHER, {database.removeConnectedDevices(sw.switchId)})
        new ConnectedDevice(traffExamProvider.get(), tg, [777]).withCloseable {
            it.sendLldp(lldpData)
            it.sendArp(arpData)
        }

        // LLDP and ARP connected devices are recognized and saved"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.getConnectedDevices().ports) {
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
        sw.knockoutWithoutLinksCheckWhenRecover(RW)

        when: "Try to delete the switch"
        def response = sw.delete(false)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.allSwitches.find { it.switchId == sw.switchId } }
    }

    def "Able to delete an active switch with active ISLs if using force delete"() {
        given: "An active switch with active ISLs"
        def sw = switches.all().first()
        //need to restore supportedTransitEncapsulation field after deleting sw
        def initSwProps = sw.getProps()
        def swIsls = topology.getRelatedIsls(sw.switchId)

        when: "Try to force delete the switch"
        def response = sw.delete(true)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) {
            assert !northbound.allSwitches.find{ it.switchId == sw.switchId }

            def links = northbound.getAllLinks()
            swIsls.each {
                assert !islUtils.getIslInfo(links, it)
                assert !islUtils.getIslInfo(links, it.reversed)
            }
        }

        cleanup: "Restore the switch, ISLs and reset costs"
        // restore switch
        def blockData = sw.knockout(RW)
        Wrappers.wait(WAIT_OFFSET) {
            flHelper.getFlsByMode(RW).each { fl ->
                assert !fl.floodlightService.getSwitches().find { it.switchId == sw.switchId }
            }
        }
        sw.revive(blockData)
        // restore ISLs
        swIsls.each { antiflap.portDown(it.srcSwitch.dpId, it.srcPort) }
        TimeUnit.SECONDS.sleep(antiflapMin)
        islHelper.restoreIsls(swIsls)
        initSwProps && northbound.updateSwitchProperties(sw.switchId, initSwProps)
        database.resetCosts(topology.isls)
    }
}
