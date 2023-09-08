package org.openkilda.functionaltests.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.IterationInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate

import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

import static java.time.LocalDateTime.now
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME
import static org.openkilda.functionaltests.listeners.LogParallelSpecsListener.getIterationPath

class CollectFailedTestLogsListener extends AbstractSpringListener{
    private ConcurrentHashMap<String, String> startTime = new ConcurrentHashMap<>()
    @Value('${elasticsearch.endpoint}')
    private String elasticSearchEndpoint
    @Value('${elasticsearch.index}')
    private String elasticSearchIndex


    @Override
    void beforeIteration(IterationInfo iterationInfo) {
        startTime.put(iterationInfo.getDisplayName(), utcTimeNow())
    }

    @Override
    void error(ErrorInfo error) {
        def objectMapper = new ObjectMapper()
        def startTime = startTime.get(error.getMethod().getIteration().getDisplayName())
        def endTime = utcTimeNow()
        def logs = new RestTemplate().getForEntity(
                    "${elasticSearchEndpoint}${elasticSearchIndex}/_search?q=" +
                            "@timestamp:[${startTime} TO ${endTime}]&size=10000", String.class).getBody()
        def beautifiedString = objectMapper.readValue(logs, Object.class)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(getTargetLogFile(error), beautifiedString)
    }

    private static String utcTimeNow() {
        return now(ZoneOffset.UTC).format(ISO_DATE_TIME)
    }

    private static File getTargetLogFile(ErrorInfo error) {
        return new File("build/logs/${getIterationPath(error.getMethod().getIteration())}.server.log.json")
    }
}
