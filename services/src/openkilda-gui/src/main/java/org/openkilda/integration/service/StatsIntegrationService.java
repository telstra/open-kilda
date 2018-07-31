package org.openkilda.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.openkilda.constants.IConstants.Metrics;
import org.openkilda.constants.OpenTsDB;
import org.openkilda.constants.OpenTsDB.StatsType;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.Filter;
import org.openkilda.integration.model.IslStats;
import org.openkilda.integration.model.Query;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.JsonUtil;
import org.openkilda.utility.StringUtil;

/**
 * The Class StatsIntegrationService.
 *
 * @author Gaurav Chugh
 */
@Service
public class StatsIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsIntegrationService.class);


    @Autowired
    private RestClientManager restClientManager;

    @Autowired
    private ApplicationProperties applicationProperties;

    public String getStats(final String startDate, final String endDate, final String downsample,
            final List<String> switchId, final String port, final String flowId,
            final String srcSwitch, final String srcPort, final String dstSwitch,
            final String dstPort, final StatsType statsType, final String metric,
            final String direction) throws IntegrationException {

        LOGGER.info("Inside getStats: switchId: " + switchId);
        try {
            String payload = getOpenTsdbRequestBody(startDate, endDate, downsample, switchId, port,
                    flowId, srcSwitch, srcPort, dstSwitch, dstPort, statsType, metric, direction);

            LOGGER.info("Inside getStats: startDate: " + startDate + ": endDate: " + endDate
                    + ": payload: " + payload);

            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getOpenTsdbQuery(),
                            HttpMethod.POST, payload, "application/json", "");
            if (RestClientManager.isValidResponse(response)) {
                return IoUtil.toString(response.getEntity().getContent());
            }
        } catch (IOException ex) {
            LOGGER.error("Inside getStats Exception is: " + ex.getMessage());
            throw new IntegrationException(ex);
        }
        return null;
    }

    private String populateFiltersAndReturnDownsample(final List<Filter> filters,
            final Map<String, String[]> params,final Integer index, final StatsType statsType) {
        String downsample = "";
        if (params != null) {
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                if (param.getKey().equalsIgnoreCase("averageOf")) {
                    downsample = param.getValue().toString();
                } else if (param.getValue() != null) {
                    Filter filter = new Filter();
                    filter.setGroupBy(Boolean.valueOf(OpenTsDB.GROUP_BY));
                    if (statsType.equals(StatsType.SWITCH_PORT) && param.getKey().equals("port")) {
                        filter.setType(OpenTsDB.TYPE_WILDCARD);
                    } else {
                        filter.setType(OpenTsDB.TYPE);
                    }
                    filter.setTagk(param.getKey());
                    if(index == 0 && param.getKey().equals("direction")) {
                        filter.setFilter("forward");
                    } else if(index == 1 && param.getKey().equals("direction")) {
                        filter.setFilter("reverse");
                    } else {
                        filter.setFilter(param.getValue()[0]);
                    }
                    filters.add(filter);
                }
            }
        }
        return downsample;
    }

    private Query getQuery(final String downsample, final String metric,
            final Map<String, String[]> params, final Integer index,final StatsType statsType) {
        List<Filter> filters = new ArrayList<Filter>();
        String paramDownSample = "";
        if (params != null) {
        		paramDownSample = populateFiltersAndReturnDownsample(filters, params, index, statsType);
        }

        if (!StringUtil.isNullOrEmpty(downsample)) {
            paramDownSample = downsample + "-avg";
        } else if (!StringUtil.isNullOrEmpty(paramDownSample)) {
            paramDownSample = paramDownSample + "-avg";
        }
        Query query = new Query();
        query.setAggregator(OpenTsDB.AGGREGATOR);
        if (!statsType.equals(StatsType.ISL)) {
            query.setRate(Boolean.valueOf(OpenTsDB.RATE));
        }
        if (statsType.equals(StatsType.SWITCH_PORT)
                && Metrics.PEN_SWITCH_STATE.getDisplayTag().equals(metric)) {
            query.setRate(false);
        }
        if(validateDownSample(paramDownSample, query)) {
            query.setDownsample(paramDownSample);
        }
        query.setMetric(metric);
        query.setFilters(filters);
        return query;
    }

	private boolean validateDownSample(String paramDownSample, final Query query) {
		boolean isValidDownsample = false;
		paramDownSample = paramDownSample.replaceFirst("^0+(?!$)", "");
        if(Character.isDigit(paramDownSample.charAt(0))) {
        	String[] downSampleArr = paramDownSample.split("-");
        	if(downSampleArr != null && downSampleArr.length > 0){
        		String dwnSample = downSampleArr[0];
        		 Pattern pattern = Pattern.compile("[msh]");
        	     Matcher matcher = pattern.matcher(dwnSample);
        	     if (matcher.find()) {
                    isValidDownsample = true;
                }
        	}
        }
        return isValidDownsample;
	}

    /**
     * Sets the date format.
     *
     * @param date the date
     * @return the string
     */
    private String formatDate(final String date) {
        return date.replaceFirst("-", "/").replaceFirst("-", "/");
    }

    private String getOpenTsdbRequestBody(final String startDate, final String endDate,
            final String downsample, final List<String> switchIds, final String port,
            final String flowId, final String srcSwitch, final String srcPort,
            final String dstSwitch, final String dstPort, final StatsType statsType,
            final String metric, final String direction) throws JsonProcessingException {
        LOGGER.info("Inside getOpenTsdbRequestBody :");
        
        List<Query> queries = getQueries(startDate, endDate, downsample, switchIds, port, flowId,
                srcSwitch, srcPort, dstSwitch, dstPort, statsType, metric, direction);
        return getRequest(startDate, endDate, queries);
    }

    private String getRequest(final String startDate, final String endDate,
            final List<Query> queryList) throws JsonProcessingException {
        IslStats islStatsRequest = new IslStats();
        islStatsRequest.setStart(formatDate(startDate));
        islStatsRequest.setEnd(formatDate(endDate));
        islStatsRequest.setQueries(queryList);
        return JsonUtil.toString(islStatsRequest);
    }
    
    /**
     * Gets the metircs.
     *
     * @param statsType the stats type
     * @param metric the metric
     * @return the metircs
     */
    private List<String> getMetircs(StatsType statsType, String metric){
        List<String> metricList = new ArrayList<String>();
        if (statsType.equals(StatsType.PORT)) {
            metricList = Metrics.switchValue(metric);
        } else if (statsType.equals(StatsType.FLOW)) {
            metricList = Metrics.flowValue(metric, true);
        } else if (statsType.equals(StatsType.ISL)) {
            metricList = Metrics.switchValue(metric);
        } else if (statsType.equals(StatsType.ISL_LOSS_PACKET)) {
            metricList = Metrics.switchValue(metric);
        } else if (statsType.equals(StatsType.FLOW_LOSS_PACKET)) {
            metricList = Metrics.flowValue("packets", false);
            metricList.addAll(Metrics.flowValue(metric, false));
        } else if (statsType.equals(StatsType.FLOW_RAW_PACKET)) {
            metricList = Metrics.flowValue("packets", false);
            metricList.addAll(Metrics.flowValue(metric, false));
        } else if (statsType.equals(StatsType.SWITCH_PORT)) {
            metricList = Metrics.getStartsWith("Switch_");
        }
        return metricList;
    }
    
    /**
     * Gets the queries.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @param switchIds the switch ids
     * @param port the port
     * @param flowId the flow id
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @param statsType the stats type
     * @param metric the metric
     * @param direction the direction
     * @return the queries
     */
    private List<Query> getQueries(final String startDate, final String endDate,
            final String downsample, final List<String> switchIds, final String port,
            final String flowId, final String srcSwitch, final String srcPort,
            final String dstSwitch, final String dstPort, final StatsType statsType,
            final String metric, final String direction){
        
        List<String> metricList = getMetircs(statsType, metric);
        
        List<Query> queries = new ArrayList<Query>();
        if (statsType.equals(StatsType.FLOW_LOSS_PACKET)) {
            queries = getFlowLossPacketsQueries(queries, downsample, flowId, srcSwitch, srcPort,
                    statsType, metricList, direction);
        } else if (statsType.equals(StatsType.ISL_LOSS_PACKET)) {
            queries = getIslLossPacketsQueries(queries, downsample, flowId, srcSwitch, srcPort,
                    dstSwitch, dstPort, statsType, metricList);
        } else if (statsType.equals(StatsType.FLOW_RAW_PACKET)) {
            queries = getFlowRawPacketsQueries(queries, downsample, switchIds, flowId, statsType,
                    metricList, direction);
        } else if (statsType.equals(StatsType.SWITCH_PORT)) {
            queries = getSwitchPortQueries(queries, switchIds, metricList, statsType, downsample);
        } else {
            String switchId = (switchIds == null || switchIds.isEmpty()) ? null : switchIds.get(0);
            Map<String, String[]> params = getParam(statsType, switchId, port, flowId, srcSwitch,
                    srcPort, dstSwitch, dstPort);
            if (metricList != null && !metricList.isEmpty()) {
                for (int index = 0; index < metricList.size(); index++) {
                    String metricName = metricList.get(index);
                    queries.add(getQuery(downsample, metricName, params, index, statsType));
                }
            }
        }
        return queries;
    }
    
    /**
     * Gets the flow loss packets queries.
     *
     * @param queries the queries
     * @param downsample the downsample
     * @param flowId the flow id
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param statsType the stats type
     * @param metricList the metric list
     * @param direction the direction
     * @return the flow loss packets queries
     */
    private List<Query> getFlowLossPacketsQueries(List<Query> queries,final String downsample, final String flowId,
            final String srcSwitch, final String srcPort, final StatsType statsType,
            final List<String> metricList, final String direction) {
        Map<String, String[]> params =
                getParam(statsType, null, null, flowId, srcSwitch, srcPort, null, null);
        int index = (direction.isEmpty() || "forward".equalsIgnoreCase(direction)) ? 0 : 1;
        if (metricList != null && !metricList.isEmpty()) {
            queries.add(getQuery(downsample, metricList.get(0), params, index, statsType));
            queries.add(getQuery(downsample, metricList.get(1), params, index, statsType));
        }
        return queries;
    }
    
    /**
     * Gets the isl loss packets queries.
     *
     * @param queries the queries
     * @param downsample the downsample
     * @param flowId the flow id
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @param statsType the stats type
     * @param metricList the metric list
     * @return the isl loss packets queries
     */
    private List<Query> getIslLossPacketsQueries(List<Query> queries, final String downsample, final String flowId,
            final String srcSwitch, final String srcPort, final String dstSwitch,
            final String dstPort, final StatsType statsType, final List<String> metricList) {
        Map<String, String[]> rx_params =
                getParam(statsType, null, null, flowId, srcSwitch, srcPort, null, null);
        Map<String, String[]> tx_params =
                getParam(statsType, null, null, flowId, null, null, dstSwitch, dstPort);
        if (metricList != null && !metricList.isEmpty()) {
            queries.add(getQuery(downsample, metricList.get(0), rx_params, 0, statsType));
            queries.add(getQuery(downsample, metricList.get(1), tx_params, 0, statsType));
        }
        return queries;
    }
    
    /**
     * Gets the flow raw packets queries.
     *
     * @param queries the queries
     * @param downsample the downsample
     * @param switchIds the switch ids
     * @param flowId the flow id
     * @param statsType the stats type
     * @param metricList the metric list
     * @return the flow raw packets queries
     */
    private List<Query> getFlowRawPacketsQueries(List<Query> queries, final String downsample,
            final List<String> switchIds, final String flowId, final StatsType statsType,
            final List<String> metricList, final String direction) {
        Map<String, String[]> flow_params =
                getParam(StatsType.FLOW, null, null, flowId, null, null, null, null);
        Map<String, String[]> params =
                getParam(StatsType.FLOW_RAW_PACKET, null, null, flowId, null, null, null, null);
        if (metricList != null && !metricList.isEmpty()) {
            int index = (direction.isEmpty() || "forward".equalsIgnoreCase(direction)) ? 0 : 1;
            Query query = getQuery(downsample, metricList.get(1), params, index, statsType);
            for (String switchId : switchIds) {
                params = getParam(StatsType.SWITCH, switchId, null, null, null, null, null, null);
                populateFiltersAndReturnDownsample(query.getFilters(), params, 0, statsType);
            }
            queries.add(query);
            Query flow_query =
                    getQuery(downsample, metricList.get(0), flow_params, index, StatsType.FLOW);
            queries.add(flow_query);
        }
        return queries;
    }
    
    /**
     * Gets the switch port queries.
     *
     * @param queries the queries
     * @param switchIds the switch ids
     * @param metricList the metric list
     * @param statsType the stats type
     * @param downsample the downsample
     * @return the switch port queries
     */
    private List<Query> getSwitchPortQueries(List<Query> queries, final List<String> switchIds,
            final List<String> metricList, final StatsType statsType, final String downsample) {
        String switchId = switchIds.isEmpty() ? null : switchIds.get(0);
        Map<String, String[]> params =
                getParam(StatsType.PORT, switchId, "*", null, null, null, null, null);
        if (metricList != null && !metricList.isEmpty()) {
            for (int index = 0; index < metricList.size(); index++) {
                String metricName = metricList.get(index);
                queries.add(getQuery(downsample, metricName, params, 0, statsType));
            }
        }
        return queries;
    }
    

    private Map<String, String[]> getParam(final StatsType statsType, final String switchId,
            final String port, final String flowId, final String srcSwitch, final String srcPort,
            final String dstSwitch, final String dstPort) {
        Map<String, String[]> params = new HashMap<String, String[]>();

        if (statsType.equals(StatsType.SWITCH)) {
            params.put("switchid", new String[] {switchId});
        } else if (statsType.equals(StatsType.PORT)) {
            params.put("switchid", new String[] {switchId});
            params.put("port", new String[] {port});
        } else if (statsType.equals(StatsType.FLOW)
                || statsType.equals(StatsType.FLOW_LOSS_PACKET)) {
            params.put("flowid", new String[] {flowId});
            params.put("direction", new String[] {});
        } else if (statsType.equals(StatsType.ISL)) {
            params.put("src_switch", new String[] {srcSwitch});
            params.put("src_port", new String[] {srcPort});
            params.put("dst_switch", new String[] {dstSwitch});
            params.put("dst_port", new String[] {dstPort});
        } else if (statsType.equals(StatsType.ISL_LOSS_PACKET)) {
            if (srcSwitch == null && srcPort == null) {
                params.put("switchid", new String[] {dstSwitch});
                params.put("port", new String[] {dstPort});
            } else {
                params.put("switchid", new String[] {srcSwitch});
                params.put("port", new String[] {srcPort});
            }
        } else if (statsType.equals(StatsType.FLOW_RAW_PACKET)) {
            params.put("flowid", new String[] {flowId});
        }
        return params;
    }

}
