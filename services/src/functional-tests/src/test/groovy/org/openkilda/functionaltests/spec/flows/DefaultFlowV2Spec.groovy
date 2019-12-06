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
import spock.lang.Ignore
import spock.lang.Narrative

import javax.inject.Provider

@Narrative("""System allows to create default port(vlan=0) and simple flow(vlan=<any number>) on the same port.
Default flow has lower priority than simple flow.
Also system allows to pass tagged traffic via default flow.""")
class DefaultFlowV2Spec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    def "System allows tagged traffic via default flow(0<->0)"() {
        // we can't test (0<->20, 20<->0) because iperf is not able to establish a connection
        when: "Create a default flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def defaultFlow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        flowHelperV2.addFlow(defaultFlow)

        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.source.vlanId = 10
        flow.destination.vlanId = 10

        then: "System allows tagged traffic on the default flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flowHelperV2.toV1(flow), 1000, 3)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Cleanup: Delete the flows"
        flowHelperV2.deleteFlow(defaultFlow.flowId)
    }

    def "Unable to send traffic from simple flow into default flow and vice versa"() {
        given: "A default flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def defaultFlow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        flowHelperV2.addFlow(defaultFlow)

        and: "A simple flow"
        def simpleflow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        simpleflow.source.vlanId = 10
        simpleflow.destination.vlanId = 10
        flowHelperV2.addFlow(simpleflow)

        when: "Try to send traffic from simple flow into default flow and vice versa"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.source.vlanId = 0
        flow.destination.vlanId = 10

        then: "System doesn't allow to send traffic in these directions"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flowHelperV2.toV1(flow), 1000, 3)
        [exam.forward, exam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert !traffExam.waitExam(direction).hasTraffic()
        }

        and: "Cleanup: Delete the flows"
        [defaultFlow, simpleflow].each { flowHelperV2.deleteFlow(it.flowId) }
    }

    @Ignore("wait for a fix: toString")
    def "Unable to create two default flow on the same port"() {
        when: "Create first default flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def defaultFlow1 = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        defaultFlow1.source.vlanId = 0
        flowHelperV2.addFlow(defaultFlow1)

        and: "Try to create second default flow on the same port"
        def defaultFlow2 = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        defaultFlow2.source.vlanId = 0
        flowHelperV2.addFlow(defaultFlow2)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 409
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == "Requested flow '$defaultFlow2.flowId' source endpoint switchId=\
\"$defaultFlow2.source.switchId\" port=$defaultFlow2.source.portNumber conflicts with existing flow \
'$defaultFlow1.flowId' endpoint switchId=\"$defaultFlow1.source.switchId\" port=$defaultFlow1.source.portNumber"

        and: "Cleanup: Delete the flow"
        flowHelperV2.deleteFlow(defaultFlow1.flowId)
    }
}
