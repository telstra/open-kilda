package org.openkilda.functionaltests.helpers.model

import groovy.transform.ToString
import groovy.transform.TupleConstructor

@TupleConstructor
@ToString
class SubFlowPingDiscrepancies {
    String subFlowId
    Map<FlowDirection, String> flowDiscrepancies
}
