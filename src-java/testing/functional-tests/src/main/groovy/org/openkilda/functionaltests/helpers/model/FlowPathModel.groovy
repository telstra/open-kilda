package org.openkilda.functionaltests.helpers.model


import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.transform.TupleConstructor

@Canonical
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

    List<Isl> getInvolvedIsls(boolean isForward = true) {
        isForward ? (path.forward.getInvolvedIsls() + protectedPath?.forward?.getInvolvedIsls()).findAll() :
                (path.reverse.getInvolvedIsls() + protectedPath?.reverse?.getInvolvedIsls()).findAll()
    }


}