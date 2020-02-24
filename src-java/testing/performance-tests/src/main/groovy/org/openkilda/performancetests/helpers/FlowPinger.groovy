package org.openkilda.performancetests.helpers

import static groovyx.gpars.GParsPool.withPool
import static groovyx.gpars.dataflow.Dataflow.task

import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v1.flows.PingOutput
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import groovyx.gpars.dataflow.Promise

import java.util.concurrent.TimeUnit

/**
 * Start an async background task of continuously 'pinging' given list of flows until 'stop' method is called.
 * If pings result in exceptions, it will only throw when 'stop' method is called.
 * Ping is considered failed only if it fails twice in a row. Singular fail is only reported as WARN to logs
 */
@Slf4j
class FlowPinger {
    NorthboundService northbound
    List<String> flowIds
    List<PingOutput> errors
    int rerouteDelay
    Promise pingTask
    volatile boolean stop = false

    FlowPinger(NorthboundService nb, List<String> flowIds, int rerouteDelay) {
        this.northbound = nb
        this.flowIds = flowIds
        this.rerouteDelay = rerouteDelay
        errors = []
    }

    void start() {
        pingTask = task {
            withPool {
                while (!stop) {
                    log.debug("pinging ${flowIds.size()} flows")
                    flowIds.eachParallel { String flowId ->
                        def firstPing = northbound.pingFlow(flowId, new PingInput())
                        if (firstPing.forward?.error || firstPing.reverse?.error) {
                            log.warn("ping fail once for $firstPing.flowId")
                            //ok, we may have failed due to reroute delay (throttling reroute)
                            //ping one more time after reroute delay
                            TimeUnit.SECONDS.sleep(rerouteDelay)
                            def secondPing = northbound.pingFlow(flowId, new PingInput())
                            if (secondPing.forward?.error || secondPing.reverse?.error) {
                                log.error("ping fail twice for $secondPing.flowId")
                                errors << secondPing
                            }
                        }
                    }
                    sleep(500)
                }
            }
        }
    }

    List<PingOutput> stop() {
        stop = true
        pingTask.join()
        if (pingTask.isError()) throw pingTask.error //if any thread has thrown during pings
        return errors //actual failed ping reports
    }
    
    boolean isStopped() {
        return stop
    }
}
