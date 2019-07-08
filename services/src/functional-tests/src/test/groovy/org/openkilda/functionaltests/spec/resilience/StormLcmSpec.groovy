package org.openkilda.functionaltests.spec.resilience

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.WfmManipulator
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowPayload

import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Narrative("""
Storm Lifecycle Management: verifies system behavior after restart of WFM. This is required to simulate prod 
deployments, which are done on the live environment. Before restart the system will have some data (created flows etc.),
verify their consistency after restart.
""")
/**
 * This test takes quite some time (~10+ minutes) since it redeploys all the storm topologies.
 * Aborting it in the middle of execution may lead to Kilda malfunction.
 */
@Tags(VIRTUAL)
class StormLcmSpec extends HealthCheckSpecification {
    @Shared
    WfmManipulator wfmManipulator

    def setupOnce() {
        //since we simulate storm restart by restarting the docker container, for now this is only possible on virtual
        //TODO(rtretiak): this can possibly be achieved for 'hardware' via lock-keeper instance
        requireProfiles("virtual")
        wfmManipulator = new WfmManipulator()
    }

    def "System survives Storm topologies restart"() {
        given: "Non-empty system with some flows created"
        List<FlowPayload> flows = []
        def flowsAmount = topology.activeSwitches.size() * 3
        flowsAmount.times {
            def flow = flowHelper.randomFlow(*topologyHelper.getRandomSwitchPair(false), false, flows)
            flow.maximumBandwidth = 500000
            flowHelper.addFlow(flow)
            flows << flow
        }

        and: "Database dump"
        def nodesDump = database.dumpAllNodes()
        def relationsDump = database.dumpAllRelations()

        when: "Storm topologies are restarted"
        wfmManipulator.restartWfm()

        then: "Database nodes and relations are unchanged"
        def newNodes = database.dumpAllNodes()
        def newRelation = database.dumpAllRelations()
        expect newNodes, sameBeanAs(nodesDump)
        expect newRelation, sameBeanAs(relationsDump).ignoring("time_modify").ignoring("latency")

        and: "Flows remain valid in terms of installed rules and meters"
        flows.each { flow ->
            northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }
        }

        and: "Flow can be updated"
        def flowToUpdate = flows[0]
        //expect enough free vlans here, ignore used switch-ports for simplicity of search
        def unusedVlan = (flowHelper.allowedVlans - flows.collectMany { [it.source.vlanId, it.destination.vlanId] })[0]
        flowHelper.updateFlow(flowToUpdate.id, flowToUpdate.tap { it.source.vlanId = unusedVlan })
        northbound.validateFlow(flowToUpdate.id).each { direction -> assert direction.asExpected }

        and: "Cleanup: remove flows"
        flows.each { flowHelper.deleteFlow(it.id) }
    }

    @Ignore("issue https://github.com/telstra/open-kilda/issues/2363")
    def "System's able to fail an ISL if switches on both ends go offline during restart of network topology"() {
        when: "Kill network topology"
        wfmManipulator.killTopology("network")

        and: "Disconnect switches on both ends of ISL"
        def islUnderTest = topology.islsForActiveSwitches.first()
        lockKeeper.knockoutSwitch(islUnderTest.srcSwitch.dpId)
        lockKeeper.knockoutSwitch(islUnderTest.dstSwitch.dpId)

        and: "Deploy network topology back"
        wfmManipulator.deployTopology("network")
        def networkDeployed = true
        TimeUnit.SECONDS.sleep(45) //after deploy topology needs more time to actually begin working

        then: "Switches are recognized as being deactivated"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(islUnderTest.srcSwitch.dpId).state == SwitchChangeType.DEACTIVATED
            assert northbound.getSwitch(islUnderTest.dstSwitch.dpId).state == SwitchChangeType.DEACTIVATED
        }

        and: "ISL between the switches gets failed after discovery timeout"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def allIsls = northbound.getAllLinks()
            assert islUtils.getIslInfo(allIsls, islUnderTest).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(allIsls, islUnderTest.reversed).get().state == IslChangeType.FAILED
        }

        and: "Cleanup: restore switch and failed ISLs"
        lockKeeper.reviveSwitch(islUnderTest.srcSwitch.dpId)
        lockKeeper.reviveSwitch(islUnderTest.dstSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            def allIsls = northbound.getAllLinks()
            assert islUtils.getIslInfo(allIsls, islUnderTest).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(allIsls, islUnderTest.reversed).get().state == IslChangeType.DISCOVERED
        }

        cleanup:
        !networkDeployed && wfmManipulator.deployTopology("network")
    }
}
