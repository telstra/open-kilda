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

import org.apache.http.HttpResponse;
import org.openkilda.constants.OpenTsDB;
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
            final String switchId, final String port, final String flowId, final String srcSwitch, final String srcPort, final String dstSwitch, final String dstPort, final String statsType, final String metric)
            throws IntegrationException {

        LOGGER.info("Inside getStats: switchId: " + switchId);
        try {
            String payload = getOpenTsdbRequestBody(startDate, endDate, downsample, switchId, port,
                    flowId, srcSwitch, srcPort, dstSwitch, dstPort, statsType, metric);

            LOGGER.info("Inside getStats: CorrelationId: : startDate: " + startDate + ": endDate: "
                    + endDate + ": payload: " + payload);

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
            final Map<String, String[]> params) {
        String downsample = "";
        if (params != null) {
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                if (param.getKey().equalsIgnoreCase("averageOf")) {
                    downsample = param.getValue().toString();
                } else if (param.getValue() != null) {
                    Filter filter = new Filter();
                    filter.setGroupBy(Boolean.valueOf(OpenTsDB.GROUP_BY));
                    filter.setType(OpenTsDB.TYPE);
                    filter.setTagk(param.getKey());
                    filter.setFilter(param.getValue()[0]);
                    filters.add(filter);
                }
            }
        }
        return downsample;
    }

    private Query getQuery(final String downsample, final String metric,
            final Map<String, String[]> params) {
        List<Filter> filters = new ArrayList<Filter>();
        String paramDownSample = "";
        if (params != null) {
            paramDownSample = populateFiltersAndReturnDownsample(filters, params);
        }

        if (!StringUtil.isNullOrEmpty(downsample)) {
            paramDownSample = downsample + "-avg";
        } else if (!StringUtil.isNullOrEmpty(paramDownSample)) {
            paramDownSample = paramDownSample + "-avg";
        }

        Query query = new Query();
        query.setAggregator(OpenTsDB.AGGREGATOR);
        query.setRate(Boolean.valueOf(OpenTsDB.RATE));
        query.setDownsample(paramDownSample);
        query.setMetric(metric);
        query.setFilters(filters);
        return query;
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
            final String downsample, final String switchId, final String port, final String flowId,
            final String srcSwitch,final String srcPort, final String dstSwitch, final String dstPort, final String statsType, final String metric) throws JsonProcessingException {
        LOGGER.info("Inside getOpenTsdbRequestBody :");
        Map<String, String[]> params = getParam(statsType, switchId, port, flowId, srcSwitch, srcPort, dstSwitch, dstPort);
        List<Query> queries = new ArrayList<Query>();
        queries.add(getQuery(downsample, metric, params));
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

    private Map<String, String[]> getParam(final String statsType, final String switchId,
            final String port, final String flowId, final String srcSwitch, final String srcPort, final String dstSwitch, final String dstPort) {
        Map<String, String[]> params = new HashMap<String, String[]>();

        if (statsType.equalsIgnoreCase("switchStats")) {
            params.put("switchId", new String[] {switchId});
        } else if (statsType.equalsIgnoreCase("portStats")) {
            params.put("switchId", new String[] {switchId});
            params.put("port", new String[] {port});
        } else if (statsType.equalsIgnoreCase("flowStats")) {
            params.put("flowid", new String[] {flowId});
        } else if(statsType.equalsIgnoreCase("islStats")){
        	params.put("src_switch", new String[] { srcSwitch });
			params.put("src_port", new String[] { srcPort });
			params.put("dst_switch", new String[] { dstSwitch });
			params.put("dst_port", new String[] { dstPort });
        }
        return params;
    }
}
