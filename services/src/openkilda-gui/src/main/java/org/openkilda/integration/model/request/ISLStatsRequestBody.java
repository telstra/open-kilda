package org.openkilda.integration.model.request;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class ISLStatsRequestBody.
 * 
 * @author sumitpal.singh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"start", "queries", "end"})
public class ISLStatsRequestBody implements Serializable {

    /** The start. */
    @JsonProperty("start")
    private String start;

    /** The queries. */
    @JsonProperty("queries")
    private List<Query> queries = null;

    /** The end. */
    @JsonProperty("end")
    private String end;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -5664522661231030709L;

    /**
     * Gets the start.
     *
     * @return the start
     */
    @JsonProperty("start")
    public String getStart() {
        return start;
    }

    /**
     * Sets the start.
     *
     * @param start the new start
     */
    @JsonProperty("start")
    public void setStart(String start) {
        this.start = start;
    }

    /**
     * Gets the queries.
     *
     * @return the queries
     */
    @JsonProperty("queries")
    public List<Query> getQueries() {
        return queries;
    }

    /**
     * Sets the queries.
     *
     * @param queries the new queries
     */
    @JsonProperty("queries")
    public void setQueries(List<Query> queries) {
        this.queries = queries;
    }

    /**
     * Gets the end.
     *
     * @return the end
     */
    @JsonProperty("end")
    public String getEnd() {
        return end;
    }

    /**
     * Sets the end.
     *
     * @param end the new end
     */
    @JsonProperty("end")
    public void setEnd(String end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "ISLStatsRequestBody [start=" + start + ", queries=" + queries + ", end=" + end
                + "]";
    }

}
