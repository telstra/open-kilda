package org.openkilda.functionaltests.spec.xresilience

import static org.assertj.core.api.Assertions.assertThat
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.SWITCHES_ACTIVATION_TIME
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.WfmManipulator
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.IslStatus
import org.openkilda.testing.Constants

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Isolated
import spock.lang.Issue
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
@Isolated
class StormLcmSpec extends HealthCheckSpecification {

    static final IntRange KILDA_ALLOWED_VLANS = 1..4095

    @Shared
    WfmManipulator wfmManipulator
    @Value('${docker.host}')
    @Shared
    String dockerHost
    @Autowired
    @Shared
    FlowFactory flowFactory

    def setupSpec() {
        //since we simulate storm restart by restarting the docker container, for now this is only possible on virtual
        //TODO(rtretiak): this can possibly be achieved for 'hardware' via lock-keeper instance
        requireProfiles("virtual")
        wfmManipulator = new WfmManipulator(dockerHost)
    }

    def cleanupSpec() {
        Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
            assert northbound.getAllSwitches().findAll {
                it.switchId in topology.switches.dpId && it.state == SwitchChangeType.ACTIVATED
            }.size() == topology.activeSwitches.size()
        }

        Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
            assert northbound.getAllLinks().findAll {
                it.source.switchId in topology.switches.dpId && it.state == IslChangeType.DISCOVERED
            }.size() == topology.islsForActiveSwitches.size() * 2
        }
    }

    @Tags(LOW_PRIORITY)
    // note: it takes ~15 minutes to run this test
    def "System survives Storm topologies restart"() {
        given: "Non-empty system with some flows created"
        List<FlowExtended> flows = []
        def flowsAmount = topology.activeSwitches.size() * 3
        List<SwitchPortVlan> busyEndpoints = []
        flowsAmount.times {
            def flow = flowFactory.getBuilder(switchPairs.all().random(), false, busyEndpoints)
                    .withBandwidth(500000).build()
                    .create()

            busyEndpoints.addAll(flow.occupiedEndpoints())
            flows << flow
        }

        and: "All created flows are valid"
        flows.each { flow -> flow.validateAndCollectDiscrepancies().isEmpty() }

        and: "Database dump"
        def initialSwitchesDump = switchHelper.dumpAllSwitches()
        def initialGraphRelations
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            initialGraphRelations = database.dumpAllRelations()
            // waiting for both switch_properties and flow_stats "has" relations
            assert database.dumpAllRelations().findAll { it.label == "has" }.size() == flowsAmount + topology.activeSwitches.size()
        }
        def initialRelationsDump = collectRelationsDetails(initialGraphRelations)

        when: "Storm topologies are restarted"
        wfmManipulator.restartWfm()

        then: "Database nodes and relations are unchanged"
        def newSwitchesDump = switchHelper.dumpAllSwitches()
        assertThat(newSwitchesDump).containsExactlyInAnyOrder(*initialSwitchesDump)

        def graphRelationsAfterWfmRestarting = database.dumpAllRelations()
        def newRelationsDump = collectRelationsDetails(graphRelationsAfterWfmRestarting)
        assertThat(newRelationsDump).containsExactlyInAnyOrder(*initialRelationsDump)

        and: "Topology is recovered after storm topology restarting"
        Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
            assert northbound.getAllLinks().findAll {
                it.source.switchId in topology.switches.dpId && it.state == IslChangeType.DISCOVERED
            }.size() == topology.islsForActiveSwitches.size() * 2
        }
        //wait until switches are activated
        Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
            assert northbound.getAllSwitches().findAll {
                it.switchId in topology.switches.dpId && it.state == SwitchChangeType.ACTIVATED
            }.size() == topology.activeSwitches.size()
        }

        and: "Flows remain valid in terms of installed rules and meters"
        flows.each { flow -> flow.validateAndCollectDiscrepancies().isEmpty() }

        and: "Flow can be updated"
        FlowExtended flowToUpdate = flows[0]
        //expect enough free vlans here, ignore used switch-ports for simplicity of search
        def unusedVlan = (KILDA_ALLOWED_VLANS - flows
                .collectMany { [it.source.vlanId, it.destination.vlanId] })[0]

        flowToUpdate.update(flowToUpdate.tap { it.source.vlanId = unusedVlan })
        flowToUpdate.validateAndCollectDiscrepancies().isEmpty()
    }

    @Ignore
    @Issue("https://github.com/telstra/open-kilda/issues/5506 (ISL between deactivated switches is in a DISCOVERED state)")
    @Tags(LOW_PRIORITY)
    def "System's able to fail an ISL if switches on both ends go offline during restart of network topology"() {
        given: "Actual network topology"
        String networkTopologyName = wfmManipulator.getStormActualNetworkTopology()

        when: "Kill network topology"
        wfmManipulator.killTopology(networkTopologyName)

        and: "Disconnect switches on both ends of ISL"
        def islUnderTest = topology.islsForActiveSwitches.first()
        def srcBlockData = lockKeeper.knockoutSwitch(islUnderTest.srcSwitch, RW)
        def dstBlockData = lockKeeper.knockoutSwitch(islUnderTest.dstSwitch, RW)

        and: "Deploy network topology back"
        wfmManipulator.deployTopology(networkTopologyName)
        def networkDeployed = true
        TimeUnit.SECONDS.sleep(45) //after deploy topology needs more time to actually begin working

        then: "Switches are recognized as being deactivated"
        Wrappers.wait(Constants.FL_DUMP_INTERVAL * 3) { //can take up to 3 network dumps
            assert northbound.getSwitch(islUnderTest.srcSwitch.dpId).state == SwitchChangeType.DEACTIVATED
            assert northbound.getSwitch(islUnderTest.dstSwitch.dpId).state == SwitchChangeType.DEACTIVATED
        }

        and: "ISL between the switches gets failed after discovery timeout"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def allIsls = northbound.getAllLinks()
            assert islUtils.getIslInfo(allIsls, islUnderTest).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(allIsls, islUnderTest.reversed).get().state == IslChangeType.FAILED
        }

        cleanup:
        networkTopologyName && !networkDeployed && wfmManipulator.deployTopology(networkTopologyName)
        srcBlockData && lockKeeper.reviveSwitch(islUnderTest.srcSwitch, srcBlockData)
        dstBlockData && lockKeeper.reviveSwitch(islUnderTest.dstSwitch, dstBlockData)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET * 3) {
            assert database.getIsls(topology.getIsls()).every {it.status == IslStatus.ACTIVE}
            assert northbound.getAllLinks().every {it.state == IslChangeType.DISCOVERED}
        }
    }

    private def collectRelationsDetails(List relationsDump) {
        List<String> propertiesFieldsToIgnore = ["time_modify", "latency", "time_create",
                                                 "switch_address_port", "connected_at", "master"]
        relationsDump.collect {
            //there is no need to ignore inVertex, outVertex, id as ONLY label and properties fields are used
            [(it.label + "_data"): [it.properties().collectEntries {
                if (it?.key in propertiesFieldsToIgnore) {
                    [:]
                } else {
                    [(it?.key): it?.value]
                }
            }.findAll()]]
        }
    }
}
