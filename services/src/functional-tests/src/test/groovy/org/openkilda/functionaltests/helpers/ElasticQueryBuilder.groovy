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

class ElasticQueryBuilder {
    public String appId
    public String tags
    public String level = "INFO OR WARN OR ERROR"
    public long timeRange = 60
    public long resultCount = 100
    public Map<String, List<String>> keywords = [:]
    public String defaultField = "source"
    public String index = "_all"

    /**
     * Returns an instance of ElasticQueryBuilder. Use add* methods chaining to set query parameters.
     * At least one of (app_id, tags) fields should be specified in the query.
     * @return
     */
    static ElasticQueryBuilder buildQuery() {
        return new ElasticQueryBuilder()
    }

    /**
     * Sets app_id field value (to search by app_id)
     * @param appId Application ID in following format: APP1 OR APP2 OR ... OR APP_N
     * @return this
     */
    def addAppId(String appId) {
        this.appId = appId
        return this
    }

    /**
     * Set tags field (to search by tags)
     * @param tags record tags in following format: TAG1 OR TAG2 OR ... OR TAG_N
     * @return this
     */
    def addTags(String tags) {
        this.tags = tags
        return this
    }

    /**
     * Sets log level filter (INFO OR WARN OR ERROR by default)
     * @param levels log levels in following format: DEBUG OR INFO OR WARN OR ERROR
     * @return this
     */
    def addLevel(String levels="INFO OR WARN OR ERROR") {
        this.level = levels
        return this
    }

    /**
     * Sets query keywords filter (WIP)
     * @param keywords - WIP, do not use
     * @return this
     */
    def addKeywords(Map<String, List<String>> keywords) {
        this.keywords = keywords
        return this
    }

    /**
     * Sets search depth from a current time (in seconds, 60 by default)
     * @param timeRange
     * @return
     */
    def addTimeRange(long timeRange) {
        this.timeRange = timeRange
        return this
    }

    /**
     * Sets desired maximum number of documents returned by ElasticSearch
     * @param resultCount - number of documents
     * @return this
     */
    def addResultCount(long resultCount) {
        this.resultCount = resultCount
        return this
    }

    /**
     * Sets default lookup field for ElasticSearch (_source by default)
     * @param defaultField - field name
     * @return this
     */
    def addDefaultField(String defaultField) {
        this.defaultField = defaultField
        return this
    }

    /**
     * Sets lookup index for ElasticSearch (may be useful in narrowing down search scope, _all by default)
     * @param index - index name
     * @return this
     */
    def addIndex(String index) {
        this.index = index
        return this
    }

}
