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

package org.openkilda.wfm.topology.utils;

import org.apache.storm.tuple.Tuple;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

/**
 * An aspect for IRichBolt / IStatefulBolt which decorates processing of a tuple with the logger context.
 */
@Aspect
public class LoggerContextInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerContextInitializer.class);

    @Around("execution(* org.apache.storm.topology.IRichBolt+.execute(org.apache.storm.tuple.Tuple)) && args(input)"
            + "|| execution(* org.apache.storm.topology.IStatefulBolt+.execute(org.apache.storm.tuple.Tuple)) && args(input)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint, Tuple input) throws Throwable {
        String correlationId = CorrelationContext.extractFrom(input)
                .map(CorrelationContext::getId)
                .orElseGet(() -> {
                    LOGGER.warn("CorrelationId was not sent or can't be extracted for tuple {}", input);
                    return DEFAULT_CORRELATION_ID;
                });

        // putClosable is not available in SLF4J 1.6, so let's save and restore the previous state.
        String previousCorrelationId = MDC.get(CORRELATION_ID);
        MDC.put(CORRELATION_ID, correlationId);

        try {
            return joinPoint.proceed(joinPoint.getArgs());
        } finally {
            if (previousCorrelationId != null) {
                MDC.put(CORRELATION_ID, previousCorrelationId);
            } else {
                MDC.remove(CORRELATION_ID);
            }
        }
    }
}
