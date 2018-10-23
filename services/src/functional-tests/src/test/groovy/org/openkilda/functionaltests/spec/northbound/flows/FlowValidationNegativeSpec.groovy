package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.info.rule.SwitchFlowEntries
import org.openkilda.messaging.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.springframework.beans.factory.annotation.Autowired

import spock.lang.Unroll


class FlowValidationNegativeSpec extends BaseSpecification {

    @Autowired
    PathHelper pathHelper
    List<Switch> switches
    List<SwitchId> switchIds

    Map<SwitchId, SwitchFlowEntries> captureSwitchRules(List<SwitchId> switchIds) {
        def rules = [:]
        switchIds.each { switchId -> rules[switchId] = northbound.getSwitchRules(switchId) }
        return rules
    }

    Map<SwitchId, List<FlowEntry>> findFlowRules(Map<SwitchId, SwitchFlowEntries> oldRules, List<SwitchId> switches) {
        def newRules = captureSwitchRules(switches)
        def result = [:]
        newRules.each {k, v ->
            def difference = v.flowEntries - (v.flowEntries.intersect((Iterable<FlowEntry>)oldRules[k].flowEntries))
            if (difference) {
                result[k] = difference
            }
        }
        return result
    }


    def setupOnce() {
        switches = topologyDefinition.getActiveSwitches()
        switchIds = switches.collect {it -> it.dpId}
    }

    def getNonNeighbouringSwitches() {
        def islInfoData = northbound.getAllLinks()
        return [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst -> islInfoData.every { link ->
                def switchId = link.path*.switchId
                !(switchId.contains(src.dpId) && switchId.contains(dst.dpId))
            }
        }
    }

    def getNeighbouringSwitches() {
        return [switches, switches].combinations().findAll { src, dst -> src != dst }.unique().first()
    }

    def getSingleSwitch() {
        return [switches, switches].combinations().findAll() { src, dst -> src == dst } .unique().first()
    }


    @Unroll
    def "Flow validation should fail if #rule switch rule is deleted"() {
        given: "Flow with a transit switch exists"

        def (Switch srcSwitch, Switch dstSwitch) = getNonNeighbouringSwitches()
        assert srcSwitch && dstSwitch
        def oldRules = captureSwitchRules(switchIds)
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow.id).status) }

        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id).collect {it -> it.dpId}

        def newRules = findFlowRules(oldRules, involvedSwitches)
        and: "Rules are installed on all switches"
        assert newRules.size() > 2

        when: "${rule} switch rule is deleted"
        def cookie = newRules[newRules.keySet()[item]][0].cookie.toString()
        northbound.deleteSwitchRules(newRules.keySet()[item], cookie)
        then: "Flow validation should fail"

        !northbound.validateFlow(flow.id).first().asExpected
        cleanup: "Delete the flow"
        northbound.deleteFlow(flow.id)
        where:
        rule << ["ingress", "egress", "transit"]
        item << [0, 1, -1]
    }

    @Unroll
    def "Both flow and switch validation should fail if flow rule is missing with #flowconfig flow configuration"() {
        given: "A ${flowconfig} flow exists"
        def (src, dest) = method()

        def oldRules = captureSwitchRules(switchIds)
        def flow = flowHelper.randomFlow(src, dest)
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id).collect {it -> it.dpId}
        def flowRules = findFlowRules(oldRules, involvedSwitches)

        when: "A flow rule gets deleted"
        def cookie = flowRules[flowRules.keySet()[0]][0].cookie.toString()
        northbound.deleteSwitchRules(flowRules.keySet()[0], cookie)

        then: "Flow and switch validation should fail"
        !northbound.validateFlow(flow.id).every {it -> it.asExpected}
        northbound.validateSwitchRules(flowRules.keySet()[0]).missingRules.size() > 0
        cleanup: "Delete the flow"
        northbound.deleteFlow(flow.id)
        where:
        flowconfig << ["singleswitch", "neighbouring", "transit"]
        method << [{ getSingleSwitch() }, { getNeighbouringSwitches() }, { getNonNeighbouringSwitches() }]
    }

    @Unroll
    def "Missing rules on a switch should not prevent intact flows from successful validation"() {
        given: "Two flows with same switches in path exist"
        def src = switches[0]
        def oldRules = captureSwitchRules([src.dpId])
        def flow1 = flowHelper.singleSwitchFlow(src)
        Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow1.id).status) }

        def flowRules = findFlowRules(oldRules, [src.dpId])

        def flow2 = flowHelper.singleSwitchFlow(src)
        Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow2.id).status) }

        when: "Rule from flow #1 gets deleted"
        northbound.deleteSwitchRules(src.dpId, flowRules[src.dpId].first().cookie.toString())
        then: "Flow #2 should be validated successfully"
        northbound.validateFlow(flow2.id).every {it -> it.asExpected}
        and: "Switch validation should fail"

        cleanup: "Delete the flows"
    }
}