package org.openkilda.northbound.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkPropsDto {

    private static final String DEFAULT = "";
    private String src_sw = DEFAULT;
    private String src_pt = DEFAULT;
    private String dst_sw = DEFAULT;
    private String dst_pt = DEFAULT;
    @JsonProperty("props")
    private Map<String,String> props = new HashMap<>();

    /**
     * Creates an empty link properties.
     */
    public LinkPropsDto(){
    }

    /**
     * Creates a copy of link properties
     */
    public LinkPropsDto(Map<String,String> props){
        this.props = new HashMap<>(props);
    }

    public String getProperty(String key) {
        return props.getOrDefault(key, DEFAULT);
    }

    public LinkPropsDto setProperty(String key, String value) {
        props.put(key, value);
        return this;
    }

    public String getSrc_sw() {
        return src_sw;
    }

    public void setSrc_sw(String src_sw) {
        this.src_sw = src_sw;
    }

    public String getSrc_pt() {
        return src_pt;
    }

    public void setSrc_pt(String src_pt) {
        this.src_pt = src_pt;
    }

    public String getDst_sw() {
        return dst_sw;
    }

    public void setDst_sw(String dst_sw) {
        this.dst_sw = dst_sw;
    }

    public String getDst_pt() {
        return dst_pt;
    }

    public void setDst_pt(String dst_pt) {
        this.dst_pt = dst_pt;
    }
}
