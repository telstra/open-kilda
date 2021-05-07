/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.context;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceManagerSupplier;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * An aspect for @PersistenceContextRequired which decorates each call with a new persistence context.
 */
@Aspect
@Slf4j
public class PersistenceContextInitializer {
    /**
     * Wraps annotated method with init/close operations for the persistence context.
     */
    @Around("@annotation(persistenceContextRequired)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint,
                               PersistenceContextRequired persistenceContextRequired) throws Throwable {
        PersistenceManager persistenceManager = PersistenceManagerSupplier.DEFAULT.get();
        PersistenceContextManager persistenceContextManager = persistenceManager.getPersistenceContextManager();
        boolean isNewContext = !persistenceContextManager.isContextInitialized()
                || persistenceContextRequired.requiresNew();

        if (isNewContext) {
            persistenceContextManager.initContext();
        }

        boolean handlingException = false;
        try {
            return joinPoint.proceed(joinPoint.getArgs());
        } catch (Throwable e) {
            handlingException = true;
            throw e;
        } finally {
            if (isNewContext) {
                close(persistenceContextManager, handlingException);
            }
        }
    }

    private void close(PersistenceContextManager manager, boolean handlingException) throws Throwable {
        try {
            manager.closeContext();
        } catch (Throwable e) {
            if (!handlingException) {
                throw e;
            }
            log.error("Absorbing exception emitted by persistence context close call, to not hide exception emitted by "
                    + "context body", e);
        }
    }
}
