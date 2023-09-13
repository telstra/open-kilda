package org.openkilda.functionaltests.helpers.model


import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import groovy.transform.ToString
import groovy.transform.TupleConstructor

@TupleConstructor
@ToString(includeNames = true, includePackage = false)
class FlowPathModel {
    String flowId
    PathModel path
    PathModel protectedPath

    List<Isl> getCommonIslsWithProtected(boolean isForward = true) {
        isForward ? path.forward.getInvolvedIsls().intersect(protectedPath.forward.getInvolvedIsls()) :
                path.reverse.getInvolvedIsls().intersect(protectedPath.reverse.getInvolvedIsls())
    }
}