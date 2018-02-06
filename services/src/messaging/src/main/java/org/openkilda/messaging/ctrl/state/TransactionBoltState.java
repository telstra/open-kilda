package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateVisitor;

import java.util.Map;
import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionBoltState extends AbstractDumpState {
    @JsonProperty("transaction")
    Map<String, Map<String, Set<Long>>> transaction;

    @JsonCreator
    public TransactionBoltState(
            @JsonProperty("transaction") Map<String, Map<String, Set<Long>>> transaction) {
        this.transaction = transaction;
    }

    public void accept(DumpStateVisitor visitor) {
        visitor.visit(this);
    }
}
