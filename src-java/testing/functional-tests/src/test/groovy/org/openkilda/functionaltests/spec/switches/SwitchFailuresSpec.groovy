package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotValidatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""
This spec verifies different situations when Kilda switches suddenly disconnect from the controller.
Note: For now it is only runnable on virtual env due to no ability to disconnect hardware switches
""")

@Tags(SWITCH_RECOVER_ON_FAIL)
class SwitchFailuresSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    CleanupManager cleanupManager
    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags([SMOKE, SMOKE_SWITCHES, LOCKKEEPER])
    def "ISL is still able to properly fail even if switches have reconnected"() {
        given: "A flow"
        def isl = isls.all().withASwitch().first()
        def srcSw = switches.all().findSpecific(isl.srcSwId)
        def dstSw = switches.all().findSpecific(isl.dstSwId)
        def flow = flowFactory.getRandom(srcSw, dstSw)

        when: "Two neighbouring switches of the flow go down simultaneously"
        def srcBlockData = srcSw.knockoutWithoutLinksCheckWhenRecover(RW)
        def timeSwitchesBroke = System.currentTimeMillis()
        def dstBlockData = dstSw.knockoutWithoutLinksCheckWhenRecover(RW)
        def untilIslShouldFail = { timeSwitchesBroke + discoveryTimeout * 1000 - System.currentTimeMillis() }

        and: "ISL between those switches looses connection"
        cleanupManager.addAction(OTHER, {northbound.synchronizeSwitch(isl.srcSwId, true)})
        cleanupManager.addAction(OTHER, {northbound.synchronizeSwitch(isl.dstSwId, true)})
        aSwitchFlows.removeFlows([isl.getASwitch()])

        and: "Switches go back up"
        lockKeeper.reviveSwitch(srcSw.sw, srcBlockData)
        lockKeeper.reviveSwitch(dstSw.sw, dstBlockData)

        then: "ISL still remains up right before discovery timeout should end"
        sleep(untilIslShouldFail() - 2500)
        isl.getNbDetails().state == IslChangeType.DISCOVERED

        and: "ISL fails after discovery timeout"
        isl.waitForStatus(IslChangeType.FAILED, untilIslShouldFail() / 1000 + WAIT_OFFSET)

        //depends whether there are alt paths available
        and: "The flow goes down OR changes path to avoid failed ISL after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            def currentIsls = isls.all().findInPath(flow.retrieveAllEntityPaths())
            def pathChanged = !isl.isIncludedInPath(currentIsls)
            assert pathChanged || (flow.retrieveFlowStatus().status == FlowState.DOWN &&
                    flow.retrieveFlowHistory().getEntriesByType(FlowActionType.REROUTE_FAILED).find {
                        it.taskId =~ (/.+ : retry #1 ignore_bw true/)
                    }?.payload?.last()?.action == FlowActionType.REROUTE_FAILED.payloadLastAction)
        }
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/3398")
    def "System is able to finish the reroute if switch blinks in the middle of it"() {
        given: "A flow"
        def swPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getRandom(swPair)

        when: "Current path breaks and reroute starts"
        swPair.dst.shapeTraffic(new TrafficControlData(3000))
        def islToBreak = flow.retrieveAllEntityPaths().getInvolvedIsls().first()
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        and: "Switch reconnects in the middle of reroute"
        Wrappers.wait(WAIT_OFFSET, 0) {
            def reroute = flow.retrieveFlowHistory().getEntriesByType(FlowActionType.REROUTE).first()
            assert reroute.payload.last().action == "Started validation of installed non ingress rules"
        }
        swPair.src.revive(lockKeeper.knockoutSwitch(swPair.src.sw, RW))

        then: "Flow reroute is successful"
        Wrappers.wait(PATH_INSTALLATION_TIME * 2) { //double timeout since rerouted is slowed by delay
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.retrieveFlowHistory().getEntriesByType(FlowActionType.REROUTE).last().payload.last().action == FlowActionType.REROUTE.payloadLastAction
        }

        and: "Blinking switch has no rule anomalies"
        !swPair.src.validateAndCollectFoundDiscrepancies().isPresent()

        and: "Flow validation is OK"
        flow.validateAndCollectDiscrepancies().isEmpty()
    }

    def "System can handle situation when switch reconnects while flow is being created"() {
        when: "Start creating a flow between switches and lose connection to src before rules are set"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = switches.all().getListOfSwitches()
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch).build().sendCreateRequest()
        sleep(50)
        def blockData = srcSwitch.knockout(RW)

        then: "Flow eventually goes DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            def flowInfo = flow.retrieveDetails()
            assert flowInfo.status == FlowState.DOWN
            assert flowInfo.statusInfo == "Failed to create flow $flow.flowId"
        }

        and: "Flow has no path associated"
        with(flow.retrieveAllEntityPaths()) {
            getPathNodes(Direction.FORWARD).empty
            getPathNodes(Direction.REVERSE).empty
        }

        and: "Dst switch validation shows no missing rules"
        !dstSwitch.validateAndCollectFoundDiscrepancies().isPresent()

        when: "Try to validate flow"
        flow.validate()

        then: "Error is returned, explaining that this is impossible for DOWN flows"
        def e = thrown(HttpClientErrorException)
        new FlowNotValidatedExpectedError(~/Could not validate flow: Flow $flow.flowId is in DOWN state/).matches(e)
        when: "Switch returns back UP"
        srcSwitch.revive(blockData)

        then: "Flow is still down, because ISLs had not enough time to fail, so no ISLs are discovered and no reroute happen"
        flow.retrieveFlowStatus().status == FlowState.DOWN

        when: "Reroute the flow"
        def rerouteResponse = flow.reroute()

        then: "Flow is rerouted and in UP state"
        rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) { flow.retrieveFlowStatus().status == FlowState.UP }

        and: "Has a path now"
        with(flow.retrieveAllEntityPaths()) {
            !getPathNodes(Direction.FORWARD).empty
            !getPathNodes(Direction.REVERSE).empty
        }

        and: "Can be validated"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Flow can be removed"
        flow.delete()
    }

}
