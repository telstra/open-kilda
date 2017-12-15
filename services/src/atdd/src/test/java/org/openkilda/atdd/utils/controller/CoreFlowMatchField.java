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
        "ip_proto",
        "udp_src",
        "udp_dst",
        "tcp_src",
        "tcp_dst"
})
public class CoreFlowMatchField implements Serializable {
    @JsonProperty("eth_src")
    public String ethSource;

    @JsonProperty("eth_dst")
    public String ethDest;

    @JsonProperty("eth_type")
    public String ethType;
}
