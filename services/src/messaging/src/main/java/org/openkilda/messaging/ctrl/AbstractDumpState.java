package org.openkilda.messaging.ctrl;

import org.openkilda.messaging.BaseMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractDumpState extends BaseMessage {
    private static final long serialVersionUID = 1L;
}
