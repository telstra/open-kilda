
package org.bitbucket.openkilda.floodlight.pathverification.type;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "switch",
    "port"
})
public class Node implements Serializable
{

    @JsonProperty("switch")
    private String _switch;
    @JsonProperty("port")
    private long port;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();
    private final static long serialVersionUID = 4943197009773591222L;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Node() {
    }

    /**
     * 
     * @param port
     * @param _switch
     */
    public Node(String _switch, long port) {
        super();
        this._switch = _switch;
        this.port = port;
    }

    @JsonProperty("switch")
    public String getSwitch() {
        return _switch;
    }

    @JsonProperty("switch")
    public void setSwitch(String _switch) {
        this._switch = _switch;
    }

    public Node withSwitch(String _switch) {
        this._switch = _switch;
        return this;
    }

    @JsonProperty("port")
    public long getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(long port) {
        this.port = port;
    }

    public Node withPort(long port) {
        this.port = port;
        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public Node withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(_switch).append(port).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Node) == false) {
            return false;
        }
        Node rhs = ((Node) other);
        return new EqualsBuilder().append(_switch, rhs._switch).append(port, rhs.port).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
