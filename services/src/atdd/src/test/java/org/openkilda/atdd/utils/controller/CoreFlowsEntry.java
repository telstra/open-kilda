package org.openkilda.atdd.utils.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/*
* The list of field is not complete. Only mandatory and used in our application fields are defined.
* */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({
        "version",
        "table_id",
        "packet_count",
        "byte_count",
        "priority",
        "idle_timeout_s",
        "hard_timeout_s",
        "flags",
        "instructions"
})
public class CoreFlowsEntry implements Serializable {
    @JsonProperty("cookie")
    public String cookie;

    @JsonProperty("duration_sec")
    public String durationSeconds;

    @JsonProperty("duration_nsec")
    public String durationNanoSeconds;

    @JsonProperty("match")
    public CoreFlowMatchField match;
}
