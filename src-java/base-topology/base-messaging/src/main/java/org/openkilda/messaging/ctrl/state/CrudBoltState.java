package org.openkilda.messaging.ctrl.state;

import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateVisitor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrudBoltState extends AbstractDumpState {
    public void accept(DumpStateVisitor visitor) {
        visitor.visit(this);
    }
}
