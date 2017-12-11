package org.openkilda.messaging.ctrl;

import org.openkilda.messaging.ctrl.state.CacheBoltState;
import org.openkilda.messaging.ctrl.state.CrudBoltState;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.ctrl.state.OFEPortBoltState;
import org.openkilda.messaging.ctrl.state.OFESwitchBoltState;
import org.openkilda.messaging.ctrl.state.TransactionBoltState;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CacheBoltState.class, name = "cache-bolt"),
        @JsonSubTypes.Type(value = CrudBoltState.class, name = "crud-bolt"),
        @JsonSubTypes.Type(value = OFEPortBoltState.class, name = "ofeport-bolt"),
        @JsonSubTypes.Type(value = OFESwitchBoltState.class, name = "ofeswitch-bolt"),
        @JsonSubTypes.Type(value = OFELinkBoltState.class, name = "ofelink-bolt"),
        @JsonSubTypes.Type(value = TransactionBoltState.class, name = "transaction-bolt")})
public abstract class AbstractDumpState implements Serializable {
    private static final long serialVersionUID = 1L;
}
