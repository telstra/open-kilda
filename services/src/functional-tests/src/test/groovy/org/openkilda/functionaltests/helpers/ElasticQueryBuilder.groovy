package org.openkilda.functionaltests.helpers

import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

@Builder(builderStrategy = SimpleStrategy, prefix = "set")
class ElasticQueryBuilder {
    String appId
    String tags
    String level
    long timeRange
    long resultCount
    String defaultField
    String index

    ElasticQueryBuilder() {
        this.appId = ""
        this.tags = ""
        this.level = "INFO OR WARN OR ERROR"
        this.timeRange = 60
        this.resultCount = 100
        this.defaultField = "source"
        this.index = "_all"
    }
}
