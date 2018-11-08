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

package org.openkilda.floodlight.utils;

import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.UUID;

/**
 * An aspect for @NewCorrelationContextRequired which decorates each call with a new correlation context.
 */
@Aspect
public class CorrelationContextInitializer {

    /**
     * The around aspect.
     *
     * @param joinPoint the proceeding join point.
     * @return the result of join point proceeding.
     * @throws Throwable if something went wrong in join point proceeding.
     */
    @Around("execution(@org.openkilda.floodlight.utils.NewCorrelationContextRequired * *(..))")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
        try (CorrelationContextClosable closable = CorrelationContext.create(UUID.randomUUID().toString())) {
            return joinPoint.proceed();
        }
    }
}
