package org.openkilda.utility;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openkilda.ws.request.Filter;
import org.openkilda.ws.request.ISLStatsRequestBody;
import org.openkilda.ws.request.Query;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class StatsUtil.
 */
@Component
public class StatsUtil {

	/** The Constant log. */
	private static final Logger log = Logger.getLogger(StatsUtil.class);

	/**
	 * Removes the json element.
	 *
	 * @param tsdbOutput
	 *            the tsdb output
	 * @return the string
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static String removeJSONElement(String tsdbOutput) {
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
					for (Iterator<?> iterator = dpsJSONObj.keySet().iterator(); iterator
							.hasNext();) {
						String key = (String) iterator.next();
						Double val = Double.valueOf(String.valueOf(dpsJSONObj
								.get(key)));
						if (val < 0) {
							dpsJSONObj.put(key, 0);
						}
					}
				}
			}
		} catch (ParseException e) {
			log.error("Inside StatsClient  while removeJSONElement is: "
					+ e.getMessage());
		}

		return tsdbArray.toString();
	}

	/**
	 * Gets the request body.
	 *
	 * @param startDate
	 *            the start date
	 * @param endDate
	 *            the end date
	 * @param metric
	 *            the metric
	 * @param query_pairs
	 *            the query_pairs
	 * @return the request body
	 */
	public String getRequestBody(String startDate, String endDate,
			String metric, Map<String, String> query_pairs) {

		log.info("Inside getRequestBody :");
		String downsample = "";
		String requestBody = "";
		ISLStatsRequestBody islStatsRequestBody = new ISLStatsRequestBody();
		Query query = new Query();
		List<Query> queryList = new ArrayList<Query>();
		List<Filter> filterList = new ArrayList<Filter>();
		query.setAggregator(IConstants.OPEN_TSDB_AGGREGATOR);
		query.setRate(Boolean.valueOf(IConstants.OPEN_TSDB_RATE));
		String downsampling = "";

		if (query_pairs != null) {
			for (Map.Entry<String, String> entry : query_pairs.entrySet()) {
				if (entry.getKey().equalsIgnoreCase("averageOf"))
					downsample = entry.getValue();

				if (entry.getValue() != null
						&& !entry.getValue().trim().isEmpty()
						&& !entry.getKey().equalsIgnoreCase("averageOf")) {
					Filter filter = new Filter();
					filter.setGroupBy(Boolean
							.valueOf(IConstants.OPEN_TSDB_GROUP_BY));
					filter.setType(IConstants.OPEN_TSDB_TYPE);
					filter.setTagk(entry.getKey());
					filter.setFilter(entry.getValue());
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
		log.info("exit getRequestBody :");

		ObjectMapper mapper = new ObjectMapper();
		try {
			requestBody = mapper.writeValueAsString(islStatsRequestBody);
		} catch (Exception e) {
			log.error("Exception  : " + e.getMessage());
		}

		return requestBody;
	}

	/**
	 * Sets the date format.
	 *
	 * @param date
	 *            the date
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
	 * @param request
	 *            the request
	 * @return the query_pairs
	 */
	public Map<String, String> getQuery_pairs(HttpServletRequest request) {

		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		try {
			String query = request.getQueryString();
			String[] pairs = query.split("&");
			for (String pair : pairs) {
				int idx = pair.indexOf("=");
				query_pairs.put(
						URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
						URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
			}
		} catch (Exception e) {
			log.error("Exception :" + e.getMessage());
		}
		return query_pairs;
	}

}
