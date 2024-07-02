package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedWithConflictExpectedError

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""
Covers PATCH /api/v2/flows/:flowId and PATCH /api/v1/flows/:flowId
This API allows to partially update a flow, i.e. update a flow without specifying a full flow payload. 
Depending on changed fields flow will be either updated+rerouted or just have its values changed in database.
""")

class PartialUpdateSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    def amountOfFlowRules = 2

    def "Able to partially update flow '#data.field' without reinstalling its rules"() {
        given: "A flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getBuilder(swPair)
                .withPathComputationStrategy(PathComputationStrategy.COST)
                .withMaxLatency(1000)
                .build().create()

        def originalCookies = switchRulesFactory.get(swPair.src.dpId).getRules().findAll {
            def cookie = new Cookie(it.cookie)
            !cookie.serviceFlag || cookie.type == CookieType.MULTI_TABLE_INGRESS_RULES
        }*.cookie

        when: "Request a flow partial update for a #data.field field"
        def updateRequest = new FlowPatchV2().tap { it."$data.field" = data.newValue }
        def response = flow.sendPartialUpdateRequest(updateRequest)

        then: "Update response reflects the changes"
        flow.waitForBeingInState(FlowState.UP)
        response."$data.field" == data.newValue

        and: "Changes actually took place"
        def flowInfo = flow.retrieveDetails()
        assert flowInfo.status == FlowState.UP
        assert flowInfo."$data.field" == data.newValue

        and: "Flow rules have not been reinstalled"
        assertThat(switchRulesFactory.get(swPair.src.dpId).getRules()*.cookie.toArray()).containsAll(originalCookies)

        where:
        data << [
                [
                        field   : "maxLatency",
                        newValue: 12345
                ],
                [
                        field   : "maxLatencyTier2",
                        newValue: 12345
                ],
                [
                        field   : "priority",
                        newValue: 654
                ],
                [
                        field   : "periodicPings",
                        newValue: true
                ],
                [
                        field   : "targetPathComputationStrategy",
                        newValue: PathComputationStrategy.LATENCY.toString().toLowerCase()
                ],
                [
                        field   : "pinned",
                        newValue: true
                ],
                [
                        field   : "description",
                        newValue: "updated"
                ],
                [
                        field   : "strictBandwidth",
                        newValue: true
                ],
        ]
    }

    @Tags([LOW_PRIORITY])
    def "Able to partially update flow #data.field without reinstalling its rules(v1)"() {
        given: "A flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def originalCookies = switchRulesFactory.get(swPair.src.dpId).getRules().findAll {
            def cookie = new Cookie(it.cookie)
            !cookie.serviceFlag || cookie.type == CookieType.MULTI_TABLE_INGRESS_RULES
        }*.cookie

        when: "Request a flow partial update for a #data.field field"
        def updateRequest = new FlowPatchDto().tap { it."$data.field" = data.newValue }
        def response = flow.sendPartialUpdateRequestV1(updateRequest)

        then: "Update response reflects the changes"
        flow.waitForBeingInState(FlowState.UP)
        response."$data.field" == data.newValue

        and: "Changes actually took place"
        flow.retrieveDetails()."$data.field" == data.newValue

        and: "Flow rules have not been reinstalled"
        switchRulesFactory.get(swPair.src.dpId).getRules()*.cookie.containsAll(originalCookies)

        where:
        data << [
                [
                        field   : "maxLatency",
                        newValue: 12345
                ],
                [
                        field   : "priority",
                        newValue: 654
                ],
                [
                        field   : "periodicPings",
                        newValue: true
                ],
                [
                        field   : "targetPathComputationStrategy",
                        newValue: PathComputationStrategy.LATENCY.toString().toLowerCase()
                ]
        ]
    }

    def "Able to partially update flow #data.field which causes a reroute"() {
        given: "A flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def originalCookies = switchRulesFactory.get(swPair.src.dpId).getRules().findAll { !new Cookie(it.cookie).serviceFlag }

        when: "Request a flow partial update for a #data.field field"
        def newValue = data.getNewValue(flow."$data.field")
        def updateRequest = new FlowPatchV2().tap { it."$data.field" = newValue }
        def response = flow.sendPartialUpdateRequest(updateRequest)

        then: "Update response reflects the changes"
        flow.waitForBeingInState(FlowState.UP)
        response."$data.field" == newValue

        and: "Changes actually took place"
        flow.retrieveDetails()."$data.field" == newValue

        and: "Flow rules have been reinstalled"
        //system doesn't reinstall shared rule on reroute action
        def newCookies = switchRulesFactory.get(swPair.src.dpId).getRules().findAll { !new Cookie(it.cookie).serviceFlag }
        newCookies.find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW } ==
                originalCookies.find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW }
        !newCookies.findAll { new Cookie(it.cookie).getType() != CookieType.SHARED_OF_FLOW }
                .any { it in originalCookies.findAll { new Cookie(it.cookie).getType() != CookieType.SHARED_OF_FLOW } }

        where:
        data << [
                [
                        field   : "maximumBandwidth",
                        getNewValue: { it + 100 }
                ],
                [
                        field   : "allocateProtectedPath",
                        getNewValue: { !it }
                ]
        ]
    }

    def "Able to turn on diversity feature using partial update"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create 2 not diverse flows going through these switches"
        def flow1 = flowFactory.getRandom(switchPair)
        def flow1Path = flow1.retrieveAllEntityPaths().getPathNodes()
        def flow2 = flowFactory.getRandom(switchPair, false, FlowState.UP, flow1.occupiedEndpoints())
        def flow2Path = flow2.retrieveAllEntityPaths().getPathNodes()

        then: "Both flows use the same path"
        flow1Path == flow2Path

        when: "Update second flow to become diverse with the first flow (partial update)"
        def updateRequest = new FlowPatchV2().tap { diverseFlowId = flow1.flowId }
        flow2.partialUpdate(updateRequest)

        then: "Flows use diverse paths"
        def flow1InvolvedIsls = flow1.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        def flow2InvolvedIsls = flow2.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        flow1InvolvedIsls.intersect(flow2InvolvedIsls).empty
    }

    def "Able to do partial update on a single-switch flow"() {
        given: "A single-switch flow"
        def swPair = switchPairs.singleSwitch().random()
        def flow = flowFactory.getRandom(swPair)
        def originalCookies = switchRulesFactory.get(swPair.src.dpId).getRules().findAll {
            def cookie = new Cookie(it.cookie)
            !cookie.serviceFlag || cookie.type == CookieType.MULTI_TABLE_INGRESS_RULES
        }*.cookie

        when: "Request a flow partial update for a 'priority' field"
        def newPriority = 777
        def updateRequest = new FlowPatchV2().tap { it.priority = newPriority }
        def response = flow.sendPartialUpdateRequest(updateRequest)

        then: "Update response reflects the changes"
        flow.waitForBeingInState(FlowState.UP)
        response.priority == newPriority

        and: "Changes actually took place"
        flow.retrieveDetails().priority == newPriority

        and: "Flow rules have not been reinstalled"
        switchRulesFactory.get(swPair.src.dpId).getRules()*.cookie.containsAll(originalCookies)
    }

    def "Able to update a flow port and vlan using partial update"() {
        given: "Three active switches"
        def allSwitches = topology.activeSwitches
        assumeTrue(allSwitches.size() >= 3, "Unable to find three active switches")
        def srcSwitch = allSwitches[0]
        def dstSwitch = allSwitches[1]

        and: "A vlan flow"
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch, false)

        when: "Update the flow: port number and vlan id on the src endpoint"
        def newPortNumber = topology.getAllowedPortsForSwitch(topology.activeSwitches.find {
            it.dpId == flow.source.switchId
        }).last()
        def newVlanId = flow.destination.vlanId + 1
        def updateRequest = new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {
                portNumber = newPortNumber
                vlanId = newVlanId
            }
        }
        flow.partialUpdate(updateRequest)

        then: "Flow is really updated"
        with(flow.retrieveDetails()) {
            it.source.portNumber == newPortNumber
            it.source.vlanId == newVlanId
        }

        and: "Flow is valid and pingable"
        flow.validateAndCollectDiscrepancies().isEmpty()
        with(flow.ping()) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        and: "The src switch passes switch validation"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(srcSwitch.dpId).isPresent()
    }

    def "Able to update a flow endpoint using partial update"() {
        given: "Three active switches"
        def allSwitches = topology.activeSwitches
        assumeTrue(allSwitches.size() >= 3, "Unable to find three active switches")
        def srcSwitch = allSwitches[0]
        def dstSwitch = allSwitches[1]
        def newDstSwitch = allSwitches[2]

        and: "A vlan flow"
        //pick a port that is free both on current dst switch and on future updated dst switch
        def port = topology.getAllowedPortsForSwitch(dstSwitch)
                .intersect(topology.getAllowedPortsForSwitch(newDstSwitch)).first()
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch, false)
                .withDestinationPort(port)
                .build().create()

        when: "Update the flow: switch id on the dst endpoint"
        def updateRequest = new FlowPatchV2().tap {
            destination = new FlowPatchEndpoint().tap {
                switchId = newDstSwitch.dpId
            }
        }
        flow.partialUpdate(updateRequest)

        then: "Flow is really updated"
        with(flow.retrieveDetails()) {
            it.destination.switchId == newDstSwitch.dpId
        }

        and: "Flow rules are installed on the new dst switch"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert switchRulesFactory.get(newDstSwitch.dpId).getRules().findAll { def cookie = new Cookie(it.cookie)
                !cookie.serviceFlag && cookie.type == SERVICE_OR_FLOW_SEGMENT
            }.size() == amountOfFlowRules
        }

        and: "Flow is valid and pingable"
        flow.validateAndCollectDiscrepancies().isEmpty()
        with(flow.ping()) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        and: "The new and old dst switches pass switch validation"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies([dstSwitch, newDstSwitch]*.dpId).isEmpty()
        }
    }

    def "Able to update flow encapsulationType using partial update"() {
        given: "A flow with a 'transit_vlan' encapsulation"
        def switchPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().random()
        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build().create()

        def originalCookies = switchRulesFactory.get(switchPair.src.dpId).getRules().findAll {
            def cookie = new Cookie(it.cookie)
            !cookie.serviceFlag && cookie.type == SERVICE_OR_FLOW_SEGMENT
        }

        when: "Request a flow partial update for an encapsulationType field(vxlan)"
        def newEncapsulationTypeValue = FlowEncapsulationType.VXLAN
        def updateRequest = new FlowPatchV2().tap { it.encapsulationType = newEncapsulationTypeValue }
        def response = flow.sendPartialUpdateRequest(updateRequest)

        then: "Update response reflects the changes"
        flow.waitForBeingInState(FlowState.UP)
        response.encapsulationType == newEncapsulationTypeValue

        and: "Changes actually took place"
        flow.retrieveDetails().encapsulationType == newEncapsulationTypeValue

        and: "Flow rules have been reinstalled"
        !switchRulesFactory.get(switchPair.src.dpId).getRules().findAll {
            def cookie = new Cookie(it.cookie)
            !cookie.serviceFlag && cookie.type == SERVICE_OR_FLOW_SEGMENT
        }.any { it in originalCookies }
    }

    @Tags(LOW_PRIORITY)
    def "Able to update a flow port and vlan for a single-switch flow using partial update"() {
        given: "An active single-switch flow (different ports)"
        def sw = topology.activeSwitches.first()
        def flow = flowFactory.getRandom(sw, sw)

        when: "Update the flow: port number and vlanId on the src endpoint"
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        def ingressCookie = flowInfoFromDb.forwardPath.cookie.value
        def egressCookie = flowInfoFromDb.reversePath.cookie.value
        def newPortNumber = (topology.getAllowedPortsForSwitch(topology.activeSwitches.find {
            it.dpId == flow.source.switchId
        }) - flow.source.portNumber - flow.destination.portNumber).last()
        def newVlanId = flow.source.vlanId - 1
        def updateRequest = new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {
                portNumber = newPortNumber
                vlanId = newVlanId
            }
        }
        flow.partialUpdate(updateRequest)

        then: "Flow is really updated"
        with(flow.retrieveDetails()) {
            it.source.portNumber == newPortNumber
            it.source.vlanId == newVlanId
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "The ingress/egress rules are really updated"
        Wrappers.wait(RULES_INSTALLATION_TIME + WAIT_OFFSET) {
            def swRules = switchRulesFactory.get(flow.source.switchId).getRules()
            with(swRules.find { it.cookie == ingressCookie }) {
                it.match.inPort == newPortNumber.toString()
                it.instructions.applyActions.flowOutput == flow.destination.portNumber.toString()
                it.instructions.applyActions.setFieldActions*.fieldValue.contains(flow.destination.vlanId.toString())
            }
            with(swRules.find { it.cookie == egressCookie }) {
                it.match.inPort == flow.destination.portNumber.toString()
                it.instructions.applyActions.flowOutput == newPortNumber.toString()
                it.instructions.applyActions.setFieldActions*.fieldValue.contains(newVlanId.toString())
            }
        }

        and: "The switch passes switch validation"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.source.switchId).isPresent()
    }

    @Tags(LOW_PRIORITY)
    def "Able to update a flow port and vlan for a single-switch single-port flow using partial update"() {
        given: "An active single-switch single-port flow"
        def sw = topology.activeSwitches.first()
        def flow = flowFactory.getRandom(sw, sw)

        when: "Update the flow: new port number on src+dst and new vlanId on the src endpoint"
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        def ingressCookie = flowInfoFromDb.forwardPath.cookie.value
        def egressCookie = flowInfoFromDb.reversePath.cookie.value
        def newPortNumber = (topology.getAllowedPortsForSwitch(topology.activeSwitches.find {
            it.dpId == flow.source.switchId
        }) - flow.source.portNumber).last()
        def newVlanId = flow.source.vlanId - 1
        def updateRequest = new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {
                portNumber = newPortNumber
                vlanId = newVlanId
            }
            destination = new FlowPatchEndpoint().tap {
                portNumber = newPortNumber
            }
        }
        flow.partialUpdate(updateRequest)

        then: "Flow is really updated"
        with(flow.retrieveDetails()) {
            it.source.portNumber == newPortNumber
            it.destination.portNumber == newPortNumber
            it.source.vlanId == newVlanId
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "The ingress/egress rules are really updated"
        Wrappers.wait(RULES_INSTALLATION_TIME + WAIT_OFFSET) {
            def swRules = switchRulesFactory.get(flow.source.switchId).getRules()
            with(swRules.find { it.cookie == ingressCookie }) {
                it.match.inPort == newPortNumber.toString()
                it.instructions.applyActions.flowOutput == "in_port"
                it.instructions.applyActions.setFieldActions*.fieldValue.contains(flow.destination.vlanId.toString())
            }
            with(swRules.find { it.cookie == egressCookie }) {
                it.match.inPort == newPortNumber.toString()
                it.instructions.applyActions.flowOutput == "in_port"
                it.instructions.applyActions.setFieldActions*.fieldValue.contains(newVlanId.toString())
            }
        }

        and: "The switch passes switch validation"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.source.switchId).isPresent()
    }

    @Tags([LOW_PRIORITY])
    def "Partial update with empty body does not actually update flow in any way(v1)"() {
        given: "A flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def originalCookies = switchRulesFactory.get(swPair.src.dpId).getRules().findAll {
            def cookie = new Cookie(it.cookie)
            !cookie.serviceFlag || cookie.type == CookieType.MULTI_TABLE_INGRESS_RULES
        }*.cookie

        when: "Request a flow partial update without specifying any fields"
        def flowBeforeUpdate = flow.retrieveDetails()
        flow.partialUpdateV1(new FlowPatchDto())

        then: "Flow is left intact"
        flow.retrieveDetails().hasTheSamePropertiesAs(flowBeforeUpdate)

        and: "Flow rules have not been reinstalled"
        switchRulesFactory.get(swPair.src.dpId).getRules()*.cookie.containsAll(originalCookies)
    }

    def "Partial update with empty body does not actually update flow in any way"() {
        given: "A flow"
        def swPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()
        def helperFlow = flowFactory.getRandom(swPair)
        def flow = flowFactory.getBuilder(swPair)
                .withPinned(true)
                .withPeriodicPing(true)
                .withDiverseFlow(helperFlow.flowId)
                .build().create()

        def originalCookies = switchRulesFactory.get(swPair.src.dpId).getRules().findAll {
            def cookie = new Cookie(it.cookie)
            !cookie.serviceFlag || cookie.type == CookieType.MULTI_TABLE_INGRESS_RULES
        }*.cookie

        when: "Request a flow partial update without specifying any fields"
        def flowBeforeUpdate = flow.retrieveDetails()
        flow.partialUpdate(new FlowPatchV2())

        then: "Flow is left intact"
        flow.retrieveDetails().hasTheSamePropertiesAs(flowBeforeUpdate)

        and: "Flow rules have not been reinstalled"
        switchRulesFactory.get(swPair.src.dpId).getRules()*.cookie.containsAll(originalCookies)
    }

    def "Unable to partial update a flow in case new port is an isl port on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")

        and: "A flow"
        def flow = flowFactory.getRandom(isl.srcSwitch, isl.dstSwitch)

        when: "Try to edit port to isl port"
        def updateRequest = new FlowPatchV2().tap {
            it."$data.switchType" = new FlowPatchEndpoint().tap { it.portNumber = isl."$data.port" }
        }
        flow.partialUpdate(updateRequest)

        then: "Error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(data.descriptionPattern(isl)).matches(exc)

        where:
        data << [
                [
                        switchType: "source",
                        port      : "srcPort",
                        descriptionPattern   : { Isl violatedIsl ->
                            getPortViolationError("source", violatedIsl.srcPort, violatedIsl.srcSwitch.dpId)
                        }
                ],
                [
                        switchType: "destination",
                        port      : "dstPort",
                        descriptionPattern   : { Isl violatedIsl ->
                            getPortViolationError("destination", violatedIsl.dstPort, violatedIsl.dstSwitch.dpId)
                        }
                ]
        ]
    }

    def "Unable to partial update flow when there are conflicting vlans (#data.conflict)"() {
        given: "Two potential flows"
        def swPair = switchPairs.all().random()
        def flow1 = flowFactory.getBuilder(swPair, false).build()
        def flow2 = flowFactory.getBuilder(swPair, false, flow1.occupiedEndpoints()).build()
        FlowPatchV2 patch = data.getPatch(flow1)

        when: "Create two flows"
        flow1.create()
        flow2.create()

        and: "Try updating the second flow which should conflict with the first one (partial update)"
        flow2.partialUpdate(patch)

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedWithConflictExpectedError(data.errorDescription(flow1, flow2, patch)).matches(error)

        where:
        data <<[
                [
                        conflict: "the same vlans on the same port on src switch",
                        getPatch: { FlowExtended dominantFlow ->
                            new FlowPatchV2().tap { source = new FlowPatchEndpoint().tap {
                                portNumber = dominantFlow.source.portNumber
                                vlanId = dominantFlow.source.vlanId
                            }
                            }
                        },
                        errorDescription: { FlowExtended dominantFlow, FlowExtended flowToConflict, FlowPatchV2 patchDto ->
                            errorDescription(dominantFlow, "source", flowToConflict, "source", patchDto)
                        }
                ],
                [
                        conflict: "the same vlans on the same port on dst switch",
                        getPatch: { FlowExtended dominantFlow ->
                            new FlowPatchV2().tap { destination = new FlowPatchEndpoint().tap {
                                portNumber = dominantFlow.destination.portNumber
                                vlanId = dominantFlow.destination.vlanId
                            }
                            }
                        },
                        errorDescription: { FlowExtended dominantFlow, FlowExtended flowToConflict, FlowPatchV2 patchDto ->
                            errorDescription(dominantFlow, "destination", flowToConflict, "destination", patchDto)
                        }
                ],
                [
                        conflict: "no vlan, both flows are on the same port on src switch",
                        getPatch: { FlowExtended dominantFlow ->
                            dominantFlow.source.vlanId = 0
                            new FlowPatchV2().tap { source = new FlowPatchEndpoint().tap {
                                portNumber = dominantFlow.source.portNumber
                                vlanId = dominantFlow.source.vlanId
                            }
                            }
                        },
                        errorDescription: { FlowExtended dominantFlow, FlowExtended flowToConflict, FlowPatchV2 patchDto ->
                            errorDescription(dominantFlow, "source", flowToConflict, "source", patchDto)
                        }
                ],
                [
                        conflict: "no vlan, both flows are on the same port on dst switch",
                        getPatch: { FlowExtended dominantFlow ->
                            dominantFlow.destination.vlanId = 0
                            new FlowPatchV2().tap { destination = new FlowPatchEndpoint().tap {
                                portNumber = dominantFlow.destination.portNumber
                                vlanId = dominantFlow.destination.vlanId
                            }
                            }
                        },
                        errorDescription: { FlowExtended dominantFlow, FlowExtended flowToConflict, FlowPatchV2 patchDto ->
                            errorDescription(dominantFlow, "destination", flowToConflict, "destination", patchDto)
                        }
                ]
        ]
    }

    def "Unable to update a flow to have both strict_bandwidth and ignore_bandwidth flags at the same time"() {
        given: "An existing flow without flag conflicts"
        def flow = flowFactory.getBuilder(switchPairs.all().random())
                .withIgnoreBandwidth(initialIgnore)
                .withStrictBandwidth(initialStrict)
                .build().create()

        when: "Partial update the flow to have strict_bw-ignore_bw conflict"
        def updateRequest = new FlowPatchV2().tap {
            ignoreBandwidth = updateIgnore
            strictBandwidth = updateStrict
        }
        flow.partialUpdate(updateRequest)

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(
                ~/Can not turn on ignore bandwidth flag and strict bandwidth flag at the same time/).matches(error)

        where:
        initialIgnore   | initialStrict | updateIgnore  | updateStrict
        false           | false         | true          | true
        true            | false         | null          | true
        false           | true          | true          | null
    }

    @Tags(LOW_PRIORITY)
    def "Able to update vlanId via partialUpdate in case vlanId==0 and innerVlanId!=0"() {
        given: "A default flow"
        def swPair = switchPairs.all().random()
        def defaultFlow = flowFactory.getBuilder(swPair)
                .withSourceVlan(0)
                .withSourceInnerVlan(0)
                .build().create()

        when: "Update innerVlanId only via partialUpdate"
        Integer newSrcInnerVlanId = 234
        // a flow will be updated as vlan!=0 and innerVlan==0
        def updateRequest = new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {
                innerVlanId = newSrcInnerVlanId
            }
        }
        def response = defaultFlow.sendPartialUpdateRequest(updateRequest)

        then: "Partial update response reflects the changes"
        response.source.vlanId == newSrcInnerVlanId
        response.source.innerVlanId == 0

        and: "Changes actually took place"
        with(defaultFlow.retrieveDetails()) {
            it.source.vlanId == newSrcInnerVlanId
            it.source.innerVlanId == defaultFlow.source.vlanId
        }
    }

    def "Unable to partial update flow with maxLatency incorrect value(#description)"() {
        given: "Two potential flows"
        def flow = flowFactory.getBuilder(switchPairs.all().random())
                .withMaxLatency(maxLatencyBefore)
                .withMaxLatencyTier2(maxLatencyT2Before)
                .build().create()

        when: "Partial update the flow "
        def updateRequest = new FlowPatchV2().tap {
            maxLatency = maxLatencyAfter
            maxLatencyTier2 = maxLatencyT2After
        }
        flow.partialUpdate(updateRequest)

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError("Invalid flow data", description).matches(error)

        where:
        maxLatencyBefore | maxLatencyT2Before | maxLatencyAfter | maxLatencyT2After | description
        null             | null               | null            | 1                 | ~/maxLatencyTier2 property cannot be used without maxLatency/
        2                | 3                  | null            | 1                 | ~/The maxLatency 2ms is higher than maxLatencyTier2 1ms/
    }

    @Shared
    def getPortViolationError = { String endpoint, int port, SwitchId swId ->
        ~/The port $port on the switch \'$swId\' is occupied by an ISL \($endpoint endpoint collision\)./
    }

    @Shared
    def errorDescription = { FlowExtended flow, String endpoint, FlowExtended conflictingFlow,
            String conflictingEndpoint, FlowPatchV2 patch ->
        def requestedFlow = jacksonMerge(conflictingFlow.convertToUpdate(), patch)
        ~/Requested flow \'$conflictingFlow.flowId\' conflicts with existing flow \'$flow.flowId\'. \
Details: requested flow \'$requestedFlow.flowId\' $conflictingEndpoint: switchId=\"\
${requestedFlow."$conflictingEndpoint".switchId}\" port=${requestedFlow."$conflictingEndpoint".portNumber}\
${requestedFlow."$conflictingEndpoint".vlanId ? " vlanId=" + requestedFlow."$conflictingEndpoint".vlanId : ""}, \
existing flow \'$flow.flowId\' $endpoint: switchId=\"${flow."$endpoint".switchId}\" port=${flow."$endpoint".portNumber}\
${flow."$endpoint".vlanId ? " vlanId=" + flow."$endpoint".vlanId : ""}/
    }

    /**
    * Merge obj2 into obj1 using Jackson serialisation.
    * @return new object as a result of merge
     */
    def jacksonMerge(Object obj1, Object obj2) {
        def mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        def map1 = mapper.convertValue(obj1, Map)
        def map2 = mapper.convertValue(obj2, Map)
        return mapper.convertValue(merge(map1, map2), obj1.class)
    }

    Map merge(Map... maps) {
        Map result
        if (maps.length == 0) {
            result = [:]
        } else if (maps.length == 1) {
            result = maps[0]
        } else {
            result = [:]
            maps.each { map ->
                map.each { k, v ->
                    result[k] = result[k] instanceof Map ? merge(result[k], v) : v
                }
            }
        }

        result
    }
}
