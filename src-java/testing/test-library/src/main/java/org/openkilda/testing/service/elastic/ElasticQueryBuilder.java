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

package org.openkilda.testing.service.elastic;

import java.util.HashMap;
import java.util.Map;

/**
 * ElasticQuery builder class. Helps to build the ElasticSearch queries while specifying minimal number of parameters.
 * Be sure to specify at least application ID or tags field to get a viable query.
 */
public class ElasticQueryBuilder {
    private String appId;
    private String tags;
    private String level = "INFO OR WARN OR ERROR";
    private long timeRange = 60;
    private long resultCount = 100;
    private String defaultField = "_source";
    private String index = "_all";
    private Map<String, String> additionalFields;

    /**
     * Sets Application ID field the in ElasticSearch query. It is possible to match multiple application IDs by passing
     * IDs in following format:
     * "APP_ID1 OR APP_ID2 OR ... OR APP_IDN"
     *
     * @param appId Application ID, string.
     * @return this
     */
    public ElasticQueryBuilder setAppId(String appId) {
        this.appId = appId;
        return this;
    }

    /**
     * Sets Message Tags in the ElasticSearch query. In OpenKilda, this is similar with Application IDs, but allows to
     * match multiple applications working on similar topics with a single tag. It is also possible to match multiple
     * tags by passing them in following format:
     * "TAG1 OR TAG2 OR ... OR TAGN"
     *
     * @param tags tags to match against, string. Default is "INFO OR WARN OR ERROR".
     * @return this
     */
    public ElasticQueryBuilder setTags(String tags) {
        this.tags = tags;
        return this;
    }

    /**
     * Sets acceptable log levels in the ElasticSearch query. DEBUG, INFO, WARN and ERROR are accepted. Multiple levels
     * are supported the same way as multiple Application IDs or tags.
     *
     * @param level log levels, string. Default is 60.
     * @return this
     */
    public ElasticQueryBuilder setLevel(String level) {
        this.level = level;
        return this;
    }

    /**
     * Sets search depth of the ElasticSearch query, in seconds (from the current time).
     *
     * @param timeRange search depth, long. Default is 60.
     * @return this
     */
    public ElasticQueryBuilder setTimeRange(long timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    /**
     * Sets the maximum number of the entries returned by the ElasticSearch query.
     *
     * @param resultCount - maximum number of the result entries, long. Default is 100.
     * @return this
     */
    public ElasticQueryBuilder setResultCount(long resultCount) {
        this.resultCount = resultCount;
        return this;
    }

    /**
     * Selects the field in ElasticSearch database to search in.
     *
     * @param defaultField - field name, string. Default is "source".
     * @return this
     */
    public ElasticQueryBuilder setDefaultField(String defaultField) {
        this.defaultField = defaultField;
        return this;
    }

    /**
     * Selects the ElasticSearch index (aka database table) to search in.
     *
     * @param index - index name, string. Default is "_all", i.e. search is performed on all ElasticSearch indices.
     * @return this.
     */
    public ElasticQueryBuilder setIndex(String index) {
        this.index = index;
        return this;
    }

    /**
     * Inserts arbitrary filters into ElasticSearch query.
     *
     * @param name - field name
     * @param value - field value
     * @return this.
     */
    public ElasticQueryBuilder setField(String name, String value) {
        if (this.additionalFields == null) {
            this.additionalFields = new HashMap<>();
        }
        this.additionalFields.put(name, value);

        return this;
    }

    /**
     * Builder method. Make sure to call it in the end of the query building process.
     *
     * @return ElasticQuery
     */
    public ElasticQuery build() {
        return new ElasticQuery(appId, tags, level, timeRange, resultCount, defaultField, index, additionalFields);
    }
}
