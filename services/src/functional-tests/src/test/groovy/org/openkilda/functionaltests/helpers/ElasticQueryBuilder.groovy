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
     * Elastic Search Query Builer - set _app_id field
     * @param appId Application ID in following format: APP1 OR APP2 OR ... OR APP_N
     * @return this
     */
    def addAppId(String appId) {
        this.appId = appId
        return this
    }

    /**
     * Elastic Search Query Builder - set tags field
     * @param tags record tags in following format: TAG1 OR TAG2 OR ... OR TAG_N
     * @return this
     */
    def addTags(String tags) {
        this.tags = tags
        return this
    }

    /**
     * Elastic Search Query Builder - set log level filter
     * @param levels log levels in following format: DEBUG OR INFO OR WARN OR ERROR
     * @return this
     */
    def addLevel(String levels="INFO OR WARN OR ERROR") {
        this.level = levels
        return this
    }

    /**
     * Elastic Search Query Builder - set keywords filter (WIP)
     * @param keywords - WIP, do not use
     * @return this
     */
    def addKeywords(Map<String, List<String>> keywords) {
        this.keywords = keywords
        return this
    }

    /**
     *
     * @param timeRange
     * @return
     */
    def addTimeRange(long timeRange) {
        this.timeRange = 60
        return this
    }

    def addResultCount(long resultCount) {
        this.resultCount = resultCount
        return this
    }

    def addDefaultField(String defaultField) {
        this.defaultField = defaultField
        return this
    }

    def addIndex(String index) {
        this.index = index
        return this
    }

}
