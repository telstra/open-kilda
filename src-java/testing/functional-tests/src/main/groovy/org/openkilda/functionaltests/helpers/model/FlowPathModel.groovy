package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.model.SwitchId
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

    List<Isl> getCommonIslsWithProtected(Direction direction = Direction.FORWARD) {
        direction == Direction.FORWARD ? path.forward.getInvolvedIsls().intersect(protectedPath.forward.getInvolvedIsls()) :
                path.reverse.getInvolvedIsls().intersect(protectedPath.reverse.getInvolvedIsls())
    }

    List<Isl> getInvolvedIsls(Direction direction = Direction.FORWARD) {
        direction == Direction.FORWARD ? (path.forward.getInvolvedIsls() + protectedPath?.forward?.getInvolvedIsls()).findAll() :
                (path.reverse.getInvolvedIsls() + protectedPath?.reverse?.getInvolvedIsls()).findAll()
    }

    List<SwitchId> getTransitSwitches(Direction direction = Direction.FORWARD) {
        direction == Direction.FORWARD ? (path.forward.getTransitInvolvedSwitches() + protectedPath?.forward?.getTransitInvolvedSwitches()).findAll() :
                (path.reverse.getTransitInvolvedSwitches() + protectedPath?.reverse?.getTransitInvolvedSwitches()).findAll()
    }
}