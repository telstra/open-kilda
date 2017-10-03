package org.bitbucket.openkilda.messaging.model.rule;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * Represents rule entity for FLOW_MOD/UPDATE OpenFlow command.
 */
@JsonSerialize
public class FlowUpdate extends FlowDelete implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;
}
