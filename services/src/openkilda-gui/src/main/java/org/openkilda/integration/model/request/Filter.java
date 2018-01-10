package org.openkilda.integration.model.request;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Filter.
 * 
 * @author sumitpal.singh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"tagk", "group_by", "type", "filter"})
public class Filter implements Serializable {

    /** The tagk. */
    @JsonProperty("tagk")
    private String tagk;

    /** The group by. */
    @JsonProperty("group_by")
    private boolean groupBy;

    /** The type. */
    @JsonProperty("type")
    private String type;

    /** The filter. */
    @JsonProperty("filter")
    private String filter;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -300223703794502712L;

    /**
     * Gets the tagk.
     *
     * @return the tagk
     */
    @JsonProperty("tagk")
    public String getTagk() {
        return tagk;
    }

    /**
     * Sets the tagk.
     *
     * @param tagk the new tagk
     */
    @JsonProperty("tagk")
    public void setTagk(String tagk) {
        this.tagk = tagk;
    }

    /**
     * Checks if is group by.
     *
     * @return true, if is group by
     */
    @JsonProperty("group_by")
    public boolean isGroupBy() {
        return groupBy;
    }

    /**
     * Sets the group by.
     *
     * @param groupBy the new group by
     */
    @JsonProperty("group_by")
    public void setGroupBy(boolean groupBy) {
        this.groupBy = groupBy;
    }

    /**
     * Gets the type.
     *
     * @return the type
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the new type
     */
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the filter.
     *
     * @return the filter
     */
    @JsonProperty("filter")
    public String getFilter() {
        return filter;
    }

    /**
     * Sets the filter.
     *
     * @param filter the new filter
     */
    @JsonProperty("filter")
    public void setFilter(String filter) {
        this.filter = filter;
    }

    @Override
    public String toString() {
        return "Filter [tagk=" + tagk + ", groupBy=" + groupBy + ", type=" + type + ", filter="
                + filter + "]";
    }

}
