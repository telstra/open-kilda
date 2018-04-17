package org.openkilda.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkProps {

    private static final String DEFAULT = "";
    private String src_switch = DEFAULT;
    private String src_port = DEFAULT;
    private String dst_switch = DEFAULT;
    private String dst_port = DEFAULT;
    @JsonProperty("props")
    private Map<String, String> props = new HashMap<>();

    /**
     * Creates an empty link properties.
     */
    public LinkProps() {}

    /**
     * Creates a copy of link properties
     */
    public LinkProps(Map<String, String> props) {
        this.props = new HashMap<>(props);
    }

    public String getProperty(String key) {
        return props.getOrDefault(key, DEFAULT);
    }

    public LinkProps setProperty(String key, String value) {
        props.put(key, value);
        return this;
    }

    public String getSrc_switch() {
        return src_switch;
    }

    public void setSrc_switch(String src_switch) {
        this.src_switch = src_switch;
    }

    public String getSrc_port() {
        return src_port;
    }

    public void setSrc_port(String src_port) {
        this.src_port = src_port;
    }

    public String getDst_switch() {
        return dst_switch;
    }

    public void setDst_switch(String dst_switch) {
        this.dst_switch = dst_switch;
    }

    public String getDst_port() {
        return dst_port;
    }

    public void setDst_port(String dst_port) {
        this.dst_port = dst_port;
    }
}
