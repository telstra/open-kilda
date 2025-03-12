package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.model.SwitchFeature
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Narrative("""System allows to create default port(vlan=0) and simple flow(vlan=<any number>) on the same port.
Default flow has lower priority than simple flow.
Also system allows to pass tagged traffic via default flow.""")

class DefaultFlowSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Tags([SMOKE_SWITCHES])
   def "Systems allows to pass traffic via default/vlan and qinq flow when they are on the same port"() {
        given: "At least 3 traffGen switches"
        def allTraffGenSwitches = switches.all().withTraffGens().getListOfSwitches()
        assumeTrue(allTraffGenSwitches.size() > 2, "Unable to find required switches in topology")

        when: "Create a vlan flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = allTraffGenSwitches
        SwitchExtended newDstSwitch = allTraffGenSwitches.find { it != dstSwitch && it != srcSwitch }
        assert [srcSwitch, dstSwitch, newDstSwitch].every { it.getDbFeatures().contains(SwitchFeature.MULTI_TABLE) }

        def bandwidth = 1000
        def vlanFlow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(bandwidth).build()
                .create()

        and: "Create a default flow with the same srcSwitch and different dstSwitch"
        def defaultFlow = flowFactory.getBuilder(srcSwitch, newDstSwitch)
                .withBandwidth(bandwidth)
                .withSourceVlan(0)
                .withDestinationVlan(0).build()
                .create()

        and: "Create a QinQ flow with the same src and dst switch"
        def qinqFlow = flowFactory.getBuilder(srcSwitch, newDstSwitch)
                .withBandwidth(bandwidth)
                .withSourceVlan(vlanFlow.source.vlanId)
                .withDestinationVlan(vlanFlow.destination.vlanId)
                .withSourceInnerVlan(vlanFlow.destination.vlanId)
                .withDestinationInnerVlan(vlanFlow.source.vlanId).build()
                .create()

        then: "System allows traffic on the vlan flow"
        def traffExam = traffExamProvider.get()
        def examVlanFlow = vlanFlow.traffExam(traffExam, bandwidth, 5)
        withPool {
            [examVlanFlow.forward, examVlanFlow.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "System allows traffic on the default flow"
        def examDefaultFlow = defaultFlow.traffExam(traffExam, bandwidth, 5)
        withPool {
            [examDefaultFlow.forward, examDefaultFlow.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "System allows traffic on the QinQ flow"
        def examQinqFlow = qinqFlow.traffExam(traffExam, bandwidth, 5)
        withPool {
            [examQinqFlow.forward, examQinqFlow.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }
    }

    @Tags([SMOKE_SWITCHES])
    def "System allows tagged traffic via default flow(0<->0)"() {
        // we can't test (0<->20, 20<->0) because iperf is not able to establish a connection
        given: "At least 2 traffGen switches"
        def allTraffGenSwitches = switches.all().withTraffGens().getListOfSwitches()
        assumeTrue(allTraffGenSwitches.size() > 1, "Unable to find required switches in topology")

        when: "Create a default flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = allTraffGenSwitches
        def defaultFlow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withSourceVlan(0)
                .withDestinationVlan(0).build()
                .create()

        /** Issue #3472 - the defect is still reproduced
         * and: "Create a qinq flow"
         * def qinqFlow = flowFactory.getBuilder(srcSwitch, dstSwitch)
         * .withSourceVlan(10)
         * .withSourceInnerVlan(200)
         * .withDestinationVlan(10)
         * .withDestinationInnerVlan(300).build()
         * .create()
         */

        def flow = defaultFlow.deepCopy().tap {
            it.source.vlanId = 10
            it.destination.vlanId = 10
        }

        then: "System allows tagged traffic on the default flow"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, 1000, 3)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }
    }

    @Tags([SMOKE_SWITCHES])
    def "Unable to send traffic from simple flow into default flow and vice versa"() {
        given: "At least 2 traffGen switches"
        def allTraffGenSwitches = switches.all().withTraffGens().getListOfSwitches()
        assumeTrue(allTraffGenSwitches.size() > 1, "Unable to find required switches in topology")

        and: "A default flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = allTraffGenSwitches
        def defaultFlow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withSourceVlan(0)
                .withDestinationVlan(0).build()
                .create()

        and: "A simple flow"
        flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withSourceVlan(10)
                .withDestinationVlan(10).build()
                .create()

        when: "Try to send traffic from simple flow into default flow and vice versa"
        def flow = defaultFlow.deepCopy().tap {
            it.source.vlanId = 0
            it.destination.vlanId = 10
        }

        then: "System doesn't allow to send traffic in these directions"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, 1000, 3)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert !traffExam.waitExam(direction).hasTraffic()
            }
        }
    }

    def "Unable to create two default flow on the same port"() {
        when: "Create first default flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = switches.all().withTraffGens().getListOfSwitches()
        def defaultFlow1 = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withSourceVlan(0).build()
                .create()

        and: "Try to create second default flow on the same port"
        def defaultFlow2 = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withSourcePort(defaultFlow1.source.portNumber)
                .withSourceVlan(0).build()
        defaultFlow2.create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithConflictExpectedError(~/Requested flow '${defaultFlow2.flowId}' conflicts with existing flow \
'${defaultFlow1.flowId}'. Details: requested flow '${defaultFlow2.flowId}' source: switchId="${defaultFlow2.source.switchId}" \
port=${defaultFlow2.source.portNumber}, existing flow '${defaultFlow1.flowId}' \
source: switchId="${defaultFlow1.source.switchId}" port=${defaultFlow1.source.portNumber}/).matches(exc)
    }
}
