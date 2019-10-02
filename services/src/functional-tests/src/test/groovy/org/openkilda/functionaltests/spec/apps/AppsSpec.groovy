package org.openkilda.functionaltests.spec.apps

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.applications.command.CommandAppMessage
import org.openkilda.applications.command.apps.CreateExclusion
import org.openkilda.applications.command.apps.RemoveExclusion
import org.openkilda.applications.model.Endpoint
import org.openkilda.applications.model.Exclusion
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.model.Cookie
import org.openkilda.model.Flow
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.UdpData
import org.openkilda.testing.tools.UdpPacketSender

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value

import javax.inject.Provider

class AppsSpec extends HealthCheckSpecification {

    @Value("#{kafkaTopicsConfig.getAppsTopic()}")
    String appsTopic

    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    @Autowired
    Provider<TraffExamService> traffExamProvider

    String app = "telescope"
    String proto = "UDP"
    String ethType = "IPv4"

    def "Able to update a flow with adding application for this flow"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)

        when: "Create the flow"
        flowHelper.addFlow(flow)

        then: "The flow and src/dst switches are valid"
        def createdFlow = database.getFlow(flow.id)
        validateFlowAndSwitches(createdFlow)

        def switchId = flow.getSource().getDatapath()
        def port = flow.getSource().getPortNumber()
        def vlan = flow.getSource().getVlanId()

        when: "Add an application to the flow and create an exclude"
        northbound.addFlowApp(flow.getId(), app, switchId, port, vlan)
        def udpData = UdpData.buildRandom()
        addExclude(flow.getId(), flow.getSource(), udpData)

        then: "A rule masked as exclusion is returned"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitchRules(flow.source.datapath).getFlowEntries().any {
                Cookie.isMaskedAsExclusion(it.cookie) }
        }

        when: "Send a UDP packet with the same parameters as used for the exclude "
        def tgService = traffExamProvider.get()
        def device = new UdpPacketSender(tgService, topology.getTraffGen(switchId), vlan)
        device.sendUdp(udpData)

        then: "The rule packet counter is increased"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitchRules(flow.source.datapath).getFlowEntries().any {
                Cookie.isMaskedAsExclusion(it.cookie) && it.packetCount == 1
            }
        }

        when: "Remove the exclude"
        removeExclude(flow.getId(), flow.getSource(), udpData)

        then: "The rule masked as exclusion is not returned"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitchRules(flow.source.datapath).getFlowEntries().every {
                !Cookie.isMaskedAsExclusion(it.cookie) }
        }

        when: "Remove the flow application"
        northbound.removeFlowApp(flow.getId(), app, switchId, port, vlan)

        then: "The flow and src/dst switches are valid"
        validateFlowAndSwitches(createdFlow)

        cleanup: "Disconnect the device and remove the flow"
        flow && flowHelper.deleteFlow(flow.id)
        device && device.close()
    }

    private void addExclude(String flowId, FlowEndpointPayload endpoint, UdpData udpData) {
        def producer = new KafkaProducer(producerProps)
        def createExclusion = CommandAppMessage.builder()
                .payload(CreateExclusion.builder()
                        .flowId(flowId)
                        .endpoint(Endpoint.builder()
                                .switchId(endpoint.getDatapath().toString())
                                .portNumber(endpoint.getPortNumber())
                                .vlanId(endpoint.getVlanId())
                                .build())
                        .application(app)
                        .exclusion(Exclusion.builder()
                                .srcIp(udpData.getSrcIp())
                                .srcPort(udpData.getSrcPort())
                                .dstIp(udpData.getDstIp())
                                .dstPort(udpData.getDstPort())
                                .proto(proto)
                                .ethType(ethType)
                                .build())
                        .build())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build()
        producer.send(new ProducerRecord(appsTopic, UUID.randomUUID().toString(), createExclusion.toJson()))
        producer.flush()
    }

    private void removeExclude(String flowId, FlowEndpointPayload endpoint, UdpData udpData) {
        def producer = new KafkaProducer(producerProps)
        def createExclusion = CommandAppMessage.builder()
                .payload(RemoveExclusion.builder()
                        .flowId(flowId)
                        .endpoint(Endpoint.builder()
                                .switchId(endpoint.getDatapath().toString())
                                .portNumber(endpoint.getPortNumber())
                                .vlanId(endpoint.getVlanId())
                                .build())
                        .application(app)
                        .exclusion(Exclusion.builder()
                                .srcIp(udpData.getSrcIp())
                                .srcPort(udpData.getSrcPort())
                                .dstIp(udpData.getDstIp())
                                .dstPort(udpData.getDstPort())
                                .proto(proto)
                                .ethType(ethType)
                                .build())
                        .build())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build()
        producer.send(new ProducerRecord(appsTopic, UUID.randomUUID().toString(), createExclusion.toJson()))
        producer.flush()
    }

    private void validateFlowAndSwitches(Flow flow) {
        northbound.validateFlow(flow.flowId).each { assert it.asExpected }
        [flow.srcSwitch, flow.destSwitch].each {
            def validation = northbound.validateSwitch(it.switchId)
            switchHelper.verifyRuleSectionsAreEmpty(validation, ["missing", "excess"])
            if (it.ofVersion != "OF_12") {
                switchHelper.verifyMeterSectionsAreEmpty(validation, ["missing", "misconfigured", "excess"])
            }
        }
    }
}
