package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.rule.FlowApplyActions
import org.openkilda.messaging.info.rule.FlowCopyFieldAction
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.info.rule.FlowInstructions
import org.openkilda.messaging.info.rule.FlowMatchField
import org.openkilda.messaging.info.rule.FlowSetFieldAction
import org.openkilda.messaging.info.rule.FlowSwapFieldAction

import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true)
@Canonical
@EqualsAndHashCode(excludes = 'durationSeconds, durationNanoSeconds, packetCount, byteCount')
class FlowRuleEntity {
    long cookie
    long durationSeconds
    long durationNanoSeconds
    long tableId
    long packetCount
    String version
    int priority
    long idleTimeout
    long hardTimeout
    long byteCount
    MatchField match
    Instruction instructions
    String[] flags

    FlowRuleEntity(FlowEntry entry) {
        this.cookie = entry.cookie
        this.tableId = entry.tableId
        this.durationSeconds = entry.durationSeconds
        this.durationNanoSeconds = entry.durationNanoSeconds
        this.packetCount = entry.packetCount
        this.version = entry.version
        this.priority = entry.priority
        this.idleTimeout = entry.idleTimeout
        this.hardTimeout = entry.hardTimeout
        this.byteCount = entry.byteCount
        this.match = entry.match ? new MatchField(entry.match) : null
        this.instructions = entry.instructions ? new Instruction(entry.instructions) : null
        this.flags = entry.flags
    }

    @ToString(includeNames = true)
    @Canonical
    class MatchField {
        String ethSrc
        String ethDst
        String ethType
        String ipProto
        String udpSrc
        String udpDst
        String inPort
        String vlanVid
        String tunnelId
        String metadataValue
        String metadataMask

        MatchField(FlowMatchField matchField) {
            this.ethSrc = matchField.ethSrc
            this.ethDst = matchField.ethDst
            this.ethType = matchField.ethType
            this.ipProto = matchField.ipProto
            this.udpSrc = matchField.udpSrc
            this.udpDst = matchField.udpDst
            this.inPort = matchField.inPort
            this.vlanVid = matchField.vlanVid
            this.tunnelId = matchField.tunnelId
            this.metadataValue = matchField.metadataValue
            this.metadataMask = matchField.metadataMask
        }
    }

    @ToString(includeNames = true)
    @Canonical
    class Instruction {
        ApplyActions applyActions
        String none
        Long goToMeter
        Short goToTable

        Instruction(FlowInstructions instructions) {
            this.applyActions = instructions.applyActions ? new ApplyActions(instructions.applyActions) : null
            this.none = instructions.none
            this.goToMeter = instructions.goToMeter
            this.goToTable = instructions.goToTable
        }
    }

    @ToString(includeNames = true)
    @Canonical
    class ApplyActions {
        String flowOutput
        List<SetFieldAction> setFieldActions
        String pushVlan
        String popVlan
        String meter
        String pushVxlan
        String group
        FieldActionBase copyFieldAction
        FieldActionBase swapFieldAction

        ApplyActions(FlowApplyActions applyActions) {
            this.flowOutput = applyActions.flowOutput
            this.setFieldActions = applyActions.setFieldActions.collect { new SetFieldAction(it) }
            this.pushVlan = applyActions.pushVlan
            this.popVlan = applyActions.popVlan
            this.meter = applyActions.meter
            this.pushVxlan = applyActions.pushVxlan
            this.group = applyActions.group
            this.copyFieldAction = applyActions.copyFieldAction ? new FieldActionBase(applyActions.copyFieldAction) : null
            this.swapFieldAction = applyActions.swapFieldAction ? new FieldActionBase(applyActions.swapFieldAction) : null

        }

        @ToString(includeNames = true)
        @Canonical
        class SetFieldAction {
            String fieldName
            String fieldValue

            SetFieldAction(FlowSetFieldAction flowSetFieldAction) {
                this.fieldName = flowSetFieldAction.fieldName
                this.fieldValue = flowSetFieldAction.fieldValue
            }
        }

        @ToString(includeNames = true)
        @Canonical
        class FieldActionBase {
            String bits
            String srcOffset
            String dstOffset
            String srcOxm
            String dstOxm

            FieldActionBase(FlowCopyFieldAction copyFieldAction) {
                this.bits = copyFieldAction.bits
                this.srcOffset = copyFieldAction.srcOffset
                this.dstOffset = copyFieldAction.dstOffset
                this.srcOxm = copyFieldAction.srcOxm
                this.dstOxm = copyFieldAction.dstOxm
            }

            FieldActionBase(FlowSwapFieldAction swapFieldAction) {
                this.bits = swapFieldAction.bits
                this.srcOffset = swapFieldAction.srcOffset
                this.dstOffset = swapFieldAction.dstOffset
                this.srcOxm = swapFieldAction.srcOxm
                this.dstOxm = swapFieldAction.dstOxm
            }
        }
    }
}
