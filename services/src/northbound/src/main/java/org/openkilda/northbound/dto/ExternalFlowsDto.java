package org.openkilda.northbound.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

/**
 * ExternalFlowsDto contains sufficient flow information to populate kilda. This is used to
 * communicate existing flows, as in flows detected in the network but haven't been created
 * through kilda properly.
 *
 * Here's an example of a json fragment that would feed into this:
 *
 *
    {
         "flow_id": "f48fe1a28f2c849b",
         "src_node": "SW0000B0D2F5B008B0-p.3-v.45",
         "dst_node": "SW0000B0D2F5B008B0-p.1-v.23",
         "max_bandwidth": 1000,
         "forward_path": [{
             "switch_name": "ofsw3.nrt4",
             "switch_id": "SW0000B0D2F5B008B0",
             "cookie": "0x10400000005dc8f",
             "cookie_int": 73183493945154703
         }],
         "reverse_path": [{
             "switch_name": "ofsw3.nrt4",
             "switch_id": "SW0000B0D2F5B008B0",
             "cookie": "0x18400000005dc8f",
             "cookie_int": 109212290964118671
         }]
    }
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalFlowsDto {

    public static final class PathNode {
        private String switch_name = DEFAULT;
        private String switch_id = DEFAULT;
        private String cookie = DEFAULT;
        private Long cookie_int = 0L;

        public String getSwitch_name() {
            return switch_name;
        }

        public void setSwitch_name(String switch_name) {
            this.switch_name = switch_name;
        }

        public String getSwitch_id() {
            return switch_id;
        }

        public void setSwitch_id(String switch_id) {
            this.switch_id = switch_id;
        }

        public String getCookie() {
            return cookie;
        }

        public void setCookie(String cookie) {
            this.cookie = cookie;
        }

        public Long getCookie_int() {
            return cookie_int;
        }

        public void setCookie_int(Long cookie_int) {
            this.cookie_int = cookie_int;
        }
    }

    private static final String DEFAULT = "";
    private String flow_id = DEFAULT;
    private String src_node = DEFAULT;
    private String dst_node = DEFAULT;
    private Long max_bandwidth = 0L;

    private List<PathNode> forward_path = null;
    private List<PathNode> reverse_path = null;

    public ExternalFlowsDto(){
    }

    public String getFlow_id() {
        return flow_id;
    }

    public void setFlow_id(String flow_id) {
        this.flow_id = flow_id;
    }

    public String getSrc_node() {
        return src_node;
    }

    public void setSrc_node(String src_node) {
        this.src_node = src_node;
    }

    public String getDst_node() {
        return dst_node;
    }

    public void setDst_node(String dst_node) {
        this.dst_node = dst_node;
    }

    public Long getMax_bandwidth() {
        return max_bandwidth;
    }

    public void setMax_bandwidth(Long max_bandwidth) {
        this.max_bandwidth = max_bandwidth;
    }

    public List<PathNode> getForward_path() {
        return forward_path;
    }

    public void setForward_path(List<PathNode> forward_path) {
        this.forward_path = forward_path;
    }

    public List<PathNode> getReverse_path() {
        return reverse_path;
    }

    public void setReverse_path(List<PathNode> reverse_path) {
        this.reverse_path = reverse_path;
    }
}
