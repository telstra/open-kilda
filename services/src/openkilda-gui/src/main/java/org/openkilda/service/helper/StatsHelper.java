package org.openkilda.service.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openkilda.constants.OpenTsDB;
import org.openkilda.integration.model.request.Filter;
import org.openkilda.integration.model.request.ISLStatsRequestBody;
import org.openkilda.integration.model.request.Query;

@Component
public class StatsHelper {

    /** The Constant log. */
    private static final Logger LOGGER = Logger.getLogger(StatsHelper.class);

    /**
     * Removes the json element.
     *
     * @param tsdbOutput the tsdb output
     * @return the string
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String removeJSONElement(final String tsdbOutput) {
        JSONParser jsonParser = new JSONParser();
        JSONArray tsdbArray = new JSONArray();

        try {
            tsdbArray = (JSONArray) jsonParser.parse(tsdbOutput);
            for (Object obj : tsdbArray) {
                JSONObject rootJSONObj = (JSONObject) obj;
                JSONObject tagsJSONObj = (JSONObject) rootJSONObj.get("tags");
                tagsJSONObj.remove("host");

                if (rootJSONObj.containsKey("dps")) {
                    Map dpsJSONObj = (Map) rootJSONObj.get("dps");
                    for (Iterator<?> iterator = dpsJSONObj.keySet().iterator(); iterator.hasNext();) {
                        String key = (String) iterator.next();
                        Double val = Double.valueOf(String.valueOf(dpsJSONObj.get(key)));
                        if (val < 0) {
                            dpsJSONObj.put(key, 0);
                        }
                    }
                }
            }
        } catch (ParseException e) {
            LOGGER.error("Inside StatsClient  while removeJSONElement is: " + e.getMessage());
        }

        return tsdbArray.toString();
    }

    /**
     * Gets the request body.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param metric the metric
     * @param queryPairs the query_pairs
     * @return the request body
     */
    public String getRequestBody(String startDate, String endDate, final String metric,
            final Map<String, String[]> queryPairs) {

        LOGGER.info("Inside getRequestBody :");
        List<Query> queryList = new ArrayList<Query>();
        List<Filter> filterList = new ArrayList<Filter>();
        ISLStatsRequestBody islStatsRequestBody = new ISLStatsRequestBody();

        String downsample = "";
        String requestBody = "";
        String downsampling = "";

        Query query = new Query();
        query.setAggregator(OpenTsDB.AGGREGATOR);
        query.setRate(Boolean.valueOf(OpenTsDB.RATE));

        if (queryPairs != null) {
            for (Map.Entry<String, String[]> entry : queryPairs.entrySet()) {
                if (entry.getKey().equalsIgnoreCase("averageOf")) {
                    downsample = entry.getValue().toString();
                }

                if (entry.getValue() != null
                        && !entry.getKey().equalsIgnoreCase("averageOf")) {
                    Filter filter = new Filter();
                    filter.setGroupBy(Boolean.valueOf(OpenTsDB.GROUP_BY));
                    filter.setType(OpenTsDB.TYPE);
                    filter.setTagk(entry.getKey());
                    filter.setFilter(entry.getValue()[0]);
                    filterList.add(filter);
                }
            }
        }
        if (downsample != null && downsample.trim().length() != 0) {
            downsampling = downsample + "-avg";
            query.setDownsample(downsampling);
        }
        query.setMetric(metric);
        query.setFilters(filterList);
        startDate = setDateFormat(startDate);
        endDate = setDateFormat(endDate);
        islStatsRequestBody.setStart(startDate);
        islStatsRequestBody.setEnd(endDate);
        queryList.add(query);
        islStatsRequestBody.setQueries(queryList);

        ObjectMapper mapper = new ObjectMapper();
        try {
            requestBody = mapper.writeValueAsString(islStatsRequestBody);
        } catch (Exception e) {
            LOGGER.error("Exception  : " + e.getMessage());
        }

        LOGGER.info("exit getRequestBody :");
        return requestBody;
    }

    /**
     * Sets the date format.
     *
     * @param date the date
     * @return the string
     */
    public String setDateFormat(String date) {
        date = date.replaceFirst("-", "/");
        date = date.replaceFirst("-", "/");
        return date;
    }

    /**
     * Gets the query_pairs.
     *
     * @param request the request
     * @return the query_pairs
     */
    public Map<String, String> getQueryPairs(final String query) {

        Map<String, String> queryPairs = new LinkedHashMap<String, String>();
        try {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        } catch (Exception e) {
            LOGGER.error("Exception :" + e.getMessage());
        }
        return queryPairs;
    }

}
