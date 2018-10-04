/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.functionaltests.helpers

import groovy.util.logging.Slf4j
import org.openkilda.testing.service.elastic.ElasticService

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
class ElasticHelper {

    @Autowired
    private ElasticService elasticService

    /**
     * Builder interface for getLogs.
     * @param queryBuilder - Closure
     * @return Empty map in case of error
     * Deserialized JSON in case of success
     */
    Map getLogs(ElasticQueryBuilder q) {
        this.appId = queryBuilder.appId
        this.tags = queryBuilder.tags
        this.level = ""
        this.timeRange = 60
        this.resultCount = 100
        this.defaultField = "_source"
        this.index = "_all"
        queryBuilder(this)

        return getLogs(q.appId, q.tags, q.level, q.keywords, q.timeRange, q.resultCount, q.defaultField, q.index)
    }


    /**
     * Searches Elastic Search database for a specific log entries
     * @param app_id - application ID to lookup (either app_id or tags should be speficied)
     * @param tags - one or more tag, delimited by OR operator, to lookup (either app_id or tags should be specified)
     * @param level - log level (can be null)
     * @param keywods - keywords to look at (WIP, not handled at the moment)
     * @param timeRange - search depth (in seconds, set to 0 to search all).
     * @param resultCount - max number of returned documents (100 default)
     * @param defaultField - field in log entry to search at (_source by default)
     * @param index - Elastic Search index to lookup (_all by default)
     *
     * In case you need to lookup multiple application IDs, tags or log levels, pass parameters as:
     * "VALUE1 OR VALUE2 OR ... OR VALUE_N"
     *
     * Returns:
     *  Empty Map in case of Elastic Service error
     *  Deserialized JSON in case of success (even if no log entries fetched)
     */
    Map getLogs(String appId, String tags, String level, Map<String, List<String>> keywords=[:], long timeRange=60,
                long resultCount=100, String defaultField = "_source", String index = "_all") {
        if ((!appId && !tags)) {
            throw new IllegalArgumentException("Either app_id or tags should be specified")
        }
        def time = System.currentTimeMillis()
        def queryString = ""

        if (appId) {
            queryString = "app_id: (${appId})"
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
        log.debug("Issuing elastic query: ${jsonBuilder.toString()}")
        HttpHeaders headers = new HttpHeaders()
        headers.add("content-type", "application/json")
        def rawQuery = new HttpEntity(jsonBuilder.toString(), headers)
        try {
            /**
             * Elasticsearch is not guaranteed to be reachable at all times. Therefore, results from it should not be
             * relied upon.
             */
            def queryResult = elasticService.getLogs(uri, rawQuery)
            return queryResult
        } catch (Exception e) {
            log.warn("An error occured during communication with Elastic Search: ${e.toString()}. " +
                     "Empty result set will be returned.")
            return [:]
        }
    }
}
