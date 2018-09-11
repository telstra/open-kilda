package org.openkilda.functionaltests.helpers

import groovy.util.logging.Slf4j
import org.openkilda.testing.service.elastic.ElasticHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import groovy.json.JsonBuilder
import org.springframework.stereotype.Component

/**
 * A set of helper methods to access, read and filter log entries from the log management system
 */

@Component
@Slf4j
class KibanaHelper {

    @Autowired
    private ElasticHelper elasticHelper

    /**
     * Searches Elastic Search database for a specific log entries
     * @param app_id - application ID to lookup (either app_id or tags should be speficied)
     * @param tags - one or more tag, delimited by OR operator, to lookup (either app_id or tags should be specified)
     * @param level - log level (can be null)
     * @param timeRange - search depth (in seconds, set to 0 to search all).
     * @param resultCount - max number of returned documents (100 default)
     * @param defaultField - field in log entry to search at (_source by default)
     * @param index - Elastic Search index to lookup (_all by default)
     *
     * In case you need to lookup multiple application IDs, tags or log levels, pass parameters as:
     * "VALUE1 OR VALUE2 OR ... OR VALUE_N"
     */
    def getLogs(String app_id=null, String tags=null, String level="INFO OR WARN OR ERROR", Map<String,
            List<String>> keywords=[:], long timeRange=60, int resultCount=100, String defaultField = "_source",
                String index = "_all") {
        if ((!app_id && !tags)) {
            throw new IllegalArgumentException("Either app_id or tags should be specified")
        }
        def time = System.currentTimeMillis()
        def queryString = ""

        if (app_id) {
            queryString = "app_id: (${app_id})"
        }

        if (tags) {
            if (queryString) {
                queryString += " AND "
            }
            queryString += "tags: (${tags})"
        }

        if (level) {
            queryString += " AND level: (${level})"
        }

        if (timeRange) {
            queryString += " AND timeMillis: [${time - timeRange * 1000} TO ${time}]"
        }

        if (keywords) {
            //TODO: Investigate how to efficiently query _source.message field for multiple keywords
        }

        def query = ["query": [
                "query_string": [
                        "default_field": defaultField,
                        "query": queryString
                ]
        ]]

        def jsonBuilder = new JsonBuilder(query)


        String uri = "/${index}/_search/?size=${resultCount}"
        log.info("Issuing elastic query: ${jsonBuilder.toString()}")
        HttpHeaders headers = new HttpHeaders()
        headers.add("content-type", "application/json")
        def rawQuery = new HttpEntity(jsonBuilder.toString(), headers)
        try {
            /**
             * Elasticsearch is not guaranteed to be reachable at all times. Therefore, results from it should not be
             * relied upon.
             */
            def queryResult = elasticHelper.getLogs(uri, rawQuery)
            return queryResult
        } catch (Exception e) {
            log.warn("An error occured during communication with Elastic Search. Empty result set will be returned.")
            return []
        }
    }
}
