package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.info.rule.SwitchFlowEntries
import org.openkilda.messaging.model.Flow
import org.openkilda.messaging.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.springframework.beans.factory.annotation.Autowired

import spock.lang.Unroll


class FlowValidationNegativeSpec extends BaseSpecification {

    @Autowired
    PathHelper pathHelper

    @Autowired
    Database database

    @Autowired
    TopologyDefinition topology

    Map<SwitchId, SwitchFlowEntries> captureSwitchRules(List<SwitchId> switchIds) {
        def rules = [:]
        switchIds.each { switchId -> rules[switchId] = northbound.getSwitchRules(switchId) }
        return rules
    }

    Map<SwitchId, List<FlowEntry>> findFlowRules(long cookie, List<SwitchId> switches) {
        def newRules = captureSwitchRules(switches)
        def result = [:]
        newRules.each {k, v ->
            List<FlowEntry> rules = v.flowEntries.findAll() {it.cookie == cookie}
            if (rules) {
                result[k] = rules
            }
        }
        return result
    }

    def noDirectLinks(Switch src, Switch dst, List<IslInfoData> links) {
        def connectingLinks = links.findAll {link ->
            (link.path[0].switchId == src.dpId) && (link.path[-1].switchId == dst.dpId)
        }
        return connectingLinks == []
    }

    def getNonNeighbouringSwitches() {
        def islInfoData = northbound.getAllLinks()
        def switches = topology.getActiveSwitches()
        def differentSwitches = [switches, switches].combinations().unique().findAll {src, dst -> src != dst}

        def nonNeighbouringSwitches = differentSwitches.findAll {src, dst -> noDirectLinks(src, dst, islInfoData)}
        return nonNeighbouringSwitches[0]
    }

    def getNeighbouringSwitches() {
        def switches = topology.getActiveSwitches()
        return [switches, switches].combinations().findAll { src, dst -> src != dst }.unique().first()
    }

    def getSingleSwitch() {
        def switches = topology.getActiveSwitches()
        return [switches, switches].combinations().findAll() { src, dst -> src == dst }.unique().first()
    }


    @Unroll
    def "Flow validation should fail if #rule switch rule is deleted"() {
        given: "Flow with a transit switch exists"

        def (Switch srcSwitch, Switch dstSwitch) = getNonNeighbouringSwitches()
        assert srcSwitch && dstSwitch
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow = northbound.addFlow(flow)
        assert flow?.id
        assert Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow.id).status) }
        def forward = database.getFlow(flow.id)?.left
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id).collect {it -> it.dpId}

        def newRules = findFlowRules(forward.cookie, involvedSwitches)
        and: "Rules are installed on all switches"
        assert newRules.size() > 2

        when: "${rule} switch rule is deleted"
        def cookie = newRules[newRules.keySet()[item]][0].cookie.toString()
        northbound.deleteSwitchRules(newRules.keySet()[item], cookie)
        then: "Flow validation should fail"

        !northbound.validateFlow(flow.id).every {it -> it.asExpected}
        cleanup: "Delete the flow"
        if (flow){
            northbound.deleteFlow(flow.id)
        }
        where:
        rule << ["ingress", "transit", "egress"]
        item << [0, 1, -1]
    }

    @Unroll
    def "Both flow and switch validation should fail if flow rule is missing with #flowconfig flow configuration"() {
        given: "A ${flowconfig} flow exists"
        def (src, dest) = method()

        def flow = flowHelper.randomFlow(src, dest)
        assert flow?.id
        flow = northbound.addFlow(flow)
        assert Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow.id).status) }

        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id).collect {it -> it.dpId}
        Flow forward = database.getFlow(flow.id).left
        def flowRules = findFlowRules(forward.cookie, involvedSwitches)

        when: "A flow rule gets deleted"
        def cookie = flowRules[flowRules.keySet()[0]][0].cookie.toString()
        northbound.deleteSwitchRules(flowRules.keySet()[0], cookie)

        then: "Flow and switch validation should fail"
        !northbound.validateFlow(flow.id).every {it -> it.asExpected}
        northbound.validateSwitchRules(flowRules.keySet()[0]).missingRules.size() > 0
        cleanup: "Delete the flow"
        if (flow) {
            northbound.deleteFlow(flow.id)
        }
        where:
        flowconfig << ["single switch", "neighbouring", "transit"]
        method << [{ getSingleSwitch() }, { getNeighbouringSwitches() }, { getNonNeighbouringSwitches() }]
    }

    def "Missing rules on a switch should not prevent intact flows from successful validation"() {
        given: "Two flows with same switches in path exist"
        def src = topology.getActiveSwitches()[0]
        def flow1 = flowHelper.singleSwitchFlow(src)
        assert flow1?.id
        flow1 = northbound.addFlow(flow1)
        Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow1.id).status) }
        Flow forward = database.getFlow(flow1.id).left
        def flow2 = flowHelper.singleSwitchFlow(src)
        flow2 = northbound.addFlow(flow2)
        assert flow2?.id
        Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow2.id).status) }

        def flowRules = findFlowRules(forward.cookie, [src.dpId])


        when: "Rule from flow #1 gets deleted"
        northbound.deleteSwitchRules(src.dpId, flowRules[src.dpId].first().cookie.toString())
        then: "Flow #2 should be validated successfully"
        northbound.validateFlow(flow2.id).every {it -> it.asExpected}
        and: "Flow #1 validation should fail"
        !northbound.validateFlow(flow1.id).every {it -> it.asExpected}
        and: "Switch validation should fail"
        northbound.validateSwitchRules(flowRules.keySet()[0]).missingRules.size() > 0
        cleanup: "Delete the flows"
        if (flow1) {
            northbound.deleteFlow(flow1.id)
        }
        if (flow2) {
            northbound.deleteFlow(flow2.id)
        }
    }
}