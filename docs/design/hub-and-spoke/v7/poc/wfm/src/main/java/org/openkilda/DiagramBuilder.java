package org.openkilda;

import org.openkilda.FlowCrudFsm.FlowCrudEvent;
import org.openkilda.FlowCrudFsm.FlowCrudState;
import org.openkilda.model.FlCommand;

import org.squirrelframework.foundation.component.SquirrelProvider;
import org.squirrelframework.foundation.fsm.DotVisitor;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;



public class DiagramBuilder {

    public static class FlowCrudCarrier implements IFlowCrudCarrier {
        @Override
        public void registerCallback(String key) {

        }

        @Override
        public void cancelCallback(String key) {

        }

        @Override
        public void installRule(String key, FlCommand command) {

        }

        @Override
        public void checkRule(String key, FlCommand command) {

        }

        @Override
        public void response(String key, String response) {

        }
    }

    public static void main(String[] args) {
        StateMachineBuilder<FlowCrudFsm, FlowCrudState, FlowCrudEvent, Object> builder = FlowCrudFsm.builder();
        FlowCrudFsm fsm = builder.newStateMachine(FlowCrudState.Finished, new FlowCrudCarrier(), "", null);

        DotVisitor visitor = SquirrelProvider.getInstance().newInstance(DotVisitor.class);
        fsm.accept(visitor);
        visitor.convertDotFile("FlowCrud");

    }
}
