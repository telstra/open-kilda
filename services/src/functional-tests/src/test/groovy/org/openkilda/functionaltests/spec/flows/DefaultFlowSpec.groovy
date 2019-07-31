package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.messaging.error.MessageError
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

import javax.inject.Provider

@Narrative("""System allows to create default port(vlan=0) and simple flow(vlan=<any number>) on the same port.
Default flow has lower priority than simple flow.
Also system allows to pass tagged traffic via default flow.""")
class DefaultFlowSpec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    def "Systems allows to pass traffic via default and vlan flow when they are on the same port"() {
        given: "At least 3 traffGen switches"
        def allTraffGenSwitches = topology.activeTraffGens*.switchConnected
        assumeTrue("Unable to find required switches in topology", (allTraffGenSwitches.size() > 2) as boolean)

        when: "Create a vlan flow"
        def (Switch srcSwitch, Switch dstSwitch) = allTraffGenSwitches
        def bandwidth = 100
        def vlanFlow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        vlanFlow.maximumBandwidth = bandwidth
        vlanFlow.allocateProtectedPath = true
        flowHelper.addFlow(vlanFlow)

        and: "Create a default flow with the same srcSwitch and different dstSwitch"
        Switch newDstSwitch = allTraffGenSwitches.find { it != dstSwitch && it != srcSwitch }
        def defaultFlow = flowHelper.randomFlow(srcSwitch, newDstSwitch)
        defaultFlow.maximumBandwidth = bandwidth
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        defaultFlow.allocateProtectedPath = true
        flowHelper.addFlow(defaultFlow)

        then: "The default flow  has less priority than the vlan flow"
        def flowVlanPortInfo = database.getFlow(vlanFlow.id)
        def flowFullPortInfo = database.getFlow(defaultFlow.id)

        def rules = [srcSwitch.dpId, dstSwitch.dpId, newDstSwitch.dpId].collectEntries {
            [(it): northbound.getSwitchRules(it).flowEntries]
        }

        // can't be imported safely org.openkilda.floodlight.switchmanager.SwitchManager.DEFAULT_FLOW_PRIORITY
        def FLOW_PRIORITY = 24576
        def DEFAULT_FLOW_PRIORITY = FLOW_PRIORITY - 1

        [srcSwitch.dpId, dstSwitch.dpId].each { sw ->
            [flowVlanPortInfo.forwardPath.cookie.value, flowVlanPortInfo.reversePath.cookie.value].each { cookie ->
                assert rules[sw].find { it.cookie == cookie }.priority == FLOW_PRIORITY
            }
        }
        // DEFAULT_FLOW_PRIORITY sets on an ingress rule only
        rules[srcSwitch.dpId].find { it.cookie == flowFullPortInfo.reversePath.cookie.value }.priority == FLOW_PRIORITY
        rules[newDstSwitch.dpId].find {
            it.cookie == flowFullPortInfo.forwardPath.cookie.value
        }.priority == FLOW_PRIORITY

        rules[srcSwitch.dpId].find {
            it.cookie == flowFullPortInfo.forwardPath.cookie.value
        }.priority == DEFAULT_FLOW_PRIORITY
        rules[newDstSwitch.dpId].find {
            it.cookie == flowFullPortInfo.reversePath.cookie.value
        }.priority == DEFAULT_FLOW_PRIORITY

        and: "System allows traffic on the vlan flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(vlanFlow, bandwidth)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "System allows traffic on the default flow"
        def exam2 = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(defaultFlow, 1000)
        withPool {
            [exam2.forward, exam2.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Cleanup: Delete the flows"
        [vlanFlow, defaultFlow].each { flow -> flowHelper.deleteFlow(flow.id) }
    }

    def "System allows tagged traffic via default flow(0<->0)"() {
        // we can't test (0<->20, 20<->0) because iperf is not able to establish a connection
        when: "Create a default flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def defaultFlow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        flowHelper.addFlow(defaultFlow)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.source.vlanId = 10
        flow.destination.vlanId = 10

        then: "System allows tagged traffic on the default flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flow, 1000)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Cleanup: Delete the flows"
        flowHelper.deleteFlow(defaultFlow.id)
    }

    def "Unable to send traffic from simple flow into default flow and vice versa"() {
        given: "A default flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def defaultFlow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        flowHelper.addFlow(defaultFlow)

        and: "A simple flow"
        def simpleflow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        simpleflow.source.vlanId = 10
        simpleflow.destination.vlanId = 10
        flowHelper.addFlow(simpleflow)

        when: "Try to send traffic from simple flow into default flow and vice versa"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.source.vlanId = 0
        flow.destination.vlanId = 10

        then: "System doesn't allow to send traffic in these directions"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flow, 1000)
        [exam.forward, exam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert !traffExam.waitExam(direction).hasTraffic()
        }

        and: "Cleanup: Delete the flows"
        [defaultFlow, simpleflow].each { flowHelper.deleteFlow(it.id) }
    }

    def "Unable to create two default flow on the same port"() {
        when: "Create first default flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def defaultFlow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        defaultFlow1.source.vlanId = 0
        flowHelper.addFlow(defaultFlow1)

        and: "Try to create second default flow on the same port"
        def defaultFlow2 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        defaultFlow2.source.vlanId = 0
        flowHelper.addFlow(defaultFlow2)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 409
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Requested flow '$defaultFlow2.id' conflicts with existing flow \
'$defaultFlow1.id'. Details: requested flow '$defaultFlow2.id' source: switch=$defaultFlow2.source.switchDpId \
port=$defaultFlow2.source.portId vlan=0, existing flow '$defaultFlow1.id' \
source: switch=$defaultFlow1.source.switchDpId port=$defaultFlow1.source.portId vlan=0"

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(defaultFlow1.id)
    }
}
