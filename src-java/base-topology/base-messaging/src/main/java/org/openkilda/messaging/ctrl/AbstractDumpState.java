package org.openkilda.messaging.ctrl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateVisitor;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractDumpState extends BaseMessage {
    private static final long serialVersionUID = 1L;

    public abstract void accept(DumpStateVisitor visitor);
}
