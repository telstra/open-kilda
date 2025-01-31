package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.model.FlowDirection.FORWARD
import static org.openkilda.functionaltests.helpers.model.FlowDirection.REVERSE

import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult
import org.openkilda.northbound.dto.v2.yflows.SubFlowPingPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult

import groovy.transform.ToString

@ToString
class ComplexFlowPingResponse {
    boolean pingSuccess
    String error
    List<SubFlowPingDiscrepancies> subFlowsDiscrepancies

    ComplexFlowPingResponse(YFlowPingResult pingResponse) {
        this.pingSuccess = pingResponse.pingSuccess
        this.error = pingResponse.error
        this.subFlowsDiscrepancies = collectDiscrepanciesPerSubFlow(pingResponse.subFlows)
    }

    ComplexFlowPingResponse(HaFlowPingResult pingResponse) {
        this.pingSuccess = pingResponse.pingSuccess
        this.error = pingResponse.error
        this.subFlowsDiscrepancies = collectDiscrepanciesPerSubFlow(pingResponse.subFlows)
    }

    static private def collectDiscrepanciesPerSubFlow(List<SubFlowPingPayload> subFlowsPingDetails) {
        List<SubFlowPingDiscrepancies> discrepanciesPerSubFlow = subFlowsPingDetails.collect { subFlowPingDetails ->
            verifyPingLogic(subFlowPingDetails, FORWARD)
            verifyPingLogic(subFlowPingDetails, REVERSE)

            HashMap<FlowDirection, String> discrepancies = [:]
            subFlowPingDetails.forward.pingSuccess ?: discrepancies.put(FORWARD, subFlowPingDetails.forward.error)
            subFlowPingDetails.reverse.pingSuccess ?: discrepancies.put(REVERSE, subFlowPingDetails.reverse.error)

            return discrepancies.isEmpty() ? null : new SubFlowPingDiscrepancies(subFlowPingDetails.flowId, discrepancies)
        }.findAll()
        return discrepanciesPerSubFlow
    }

    static private void verifyPingLogic(SubFlowPingPayload pingPayload, FlowDirection direction) {
        def pingResult = direction == FORWARD ? pingPayload.forward : pingPayload.reverse
        assert (pingResult.pingSuccess && !pingResult.error) || (!pingResult.pingSuccess && pingResult.error),
                "There is an error in the ping logic for sub-flow ${pingPayload.flowId} $direction: $pingResult"
    }
}
