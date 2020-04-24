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

package org.openkilda.persistence.tx;

import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.spi.PersistenceProvider;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * An aspect for @TransactionRequired which ensures a call to be within a transaction.
 */
@Aspect
@Slf4j
public class TransactionInspector {
    private final PersistenceProvider persistenceProvider = PersistenceProvider.getInstance();

    /**
     * Wraps annotated method with checks for a transaction.
     */
    @Around("@annotation(transactionRequired)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint,
                               TransactionRequired transactionRequired) throws Throwable {
        PersistenceContextManager persistenceContextManager = persistenceProvider.getPersistenceContextManager();
        if (!persistenceContextManager.isContextInitialized() || !persistenceContextManager.isTxOpen()) {
            throw new PersistenceException("A transactional method was invoked outside a transaction.");
        }

        return joinPoint.proceed(joinPoint.getArgs());
    }
}
