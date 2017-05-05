package org.bitbucket.openkilda.northbound.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * Flow status representation class.
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowStatus implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;
}
