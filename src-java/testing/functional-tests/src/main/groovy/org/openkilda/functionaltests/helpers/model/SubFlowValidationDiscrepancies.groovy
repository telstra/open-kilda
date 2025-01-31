package org.openkilda.functionaltests.helpers.model

import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto

import groovy.transform.ToString
import groovy.transform.TupleConstructor

@TupleConstructor
@ToString
class SubFlowValidationDiscrepancies {
    String subFlowId
    Map<FlowDirection, List<PathDiscrepancyDto>> flowDiscrepancies
}
