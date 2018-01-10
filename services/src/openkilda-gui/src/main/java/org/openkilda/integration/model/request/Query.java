package org.openkilda.integration.model.request;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Query.
 * 
 * @author sumitpal.singh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"rate", "aggregator", "downsample", "metric", "filters"})
public class Query implements Serializable {

    /** The rate. */
    @JsonProperty("rate")
    private boolean rate;

    /** The aggregator. */
    @JsonProperty("aggregator")
    private String aggregator;

    /** The downsample. */
    @JsonProperty("downsample")
    private String downsample;

    /** The metric. */
    @JsonProperty("metric")
    private String metric;

    /** The filters. */
    @JsonProperty("filters")
    private List<Filter> filters = null;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 5051126705056803873L;

    /**
     * Checks if is rate.
     *
     * @return true, if is rate
     */
    @JsonProperty("rate")
    public boolean isRate() {
        return rate;
    }

    /**
     * Sets the rate.
     *
     * @param rate the new rate
     */
    @JsonProperty("rate")
    public void setRate(boolean rate) {
        this.rate = rate;
    }

    /**
     * Gets the aggregator.
     *
     * @return the aggregator
     */
    @JsonProperty("aggregator")
    public String getAggregator() {
        return aggregator;
    }

    /**
     * Sets the aggregator.
     *
     * @param aggregator the new aggregator
     */
    @JsonProperty("aggregator")
    public void setAggregator(String aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * Gets the downsample.
     *
     * @return the downsample
     */
    @JsonProperty("downsample")
    public String getDownsample() {
        return downsample;
    }

    /**
     * Sets the downsample.
     *
     * @param downsample the new downsample
     */
    @JsonProperty("downsample")
    public void setDownsample(String downsample) {
        this.downsample = downsample;
    }

    /**
     * Gets the metric.
     *
     * @return the metric
     */
    @JsonProperty("metric")
    public String getMetric() {
        return metric;
    }

    /**
     * Sets the metric.
     *
     * @param metric the new metric
     */
    @JsonProperty("metric")
    public void setMetric(String metric) {
        this.metric = metric;
    }

    /**
     * Gets the filters.
     *
     * @return the filters
     */
    @JsonProperty("filters")
    public List<Filter> getFilters() {
        return filters;
    }

    /**
     * Sets the filters.
     *
     * @param filters the new filters
     */
    @JsonProperty("filters")
    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    @Override
    public String toString() {
        return "Query [rate=" + rate + ", aggregator=" + aggregator + ", downsample=" + downsample
                + ", metric=" + metric + ", filters=" + filters + "]";
    }

}
