package org.bitbucket.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * Class represents high level view of data for every message used by any service.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class MessageData implements Serializable {
    /**
     * Serialization version number constant.
     */
    static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public MessageData() {
    }
}
