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

package org.openkilda.dao.interceptor;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.entity.BaseEntity;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Collection;

/**
 * The Class DaoInterceptor.
 */
@Component
@Aspect
public class DaoInterceptor {

    /** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(DaoInterceptor.class);

    /** The server context. */
    @Autowired
    private ServerContext serverContext;


    /**
     * Update user information.
     *
     * @param joinPoint the join point.
     */
    @Before("execution(* org.springframework.data.jpa.repository.JpaRepository.save(..))")
    public void updateUserInformation(final JoinPoint joinPoint) {
        Object objEntity = joinPoint.getArgs()[0];

        if (objEntity instanceof Collection) {

            @SuppressWarnings("unchecked")
            Collection<Object> objects = (Collection<Object>) objEntity;
            for (Object object : objects) {
                updateUserInformation(object);
            }
        } else {
            updateUserInformation(objEntity);
        }
    }


    /**
     * Update user information.
     *
     * @param objEntity the obj entity
     */
    public void updateUserInformation(final Object objEntity) {
        RequestContext requestContext = serverContext.getRequestContext();
        if (objEntity instanceof BaseEntity) {
            BaseEntity entity = (BaseEntity) objEntity;
            Long userId = requestContext == null || requestContext.getUserId() == null ? null
                    : requestContext.getUserId();
            if (userId != null) {
                if (entity.id() != null && entity.id() != 0) {
                    entity.setUpdatedBy(userId);
                    entity.setUpdatedDate(Calendar.getInstance().getTime());
                } else {
                    entity.setCreatedBy(userId);
                    entity.setCreatedDate(Calendar.getInstance().getTime());
                }
                _log.debug("[updateUserInformation] Update created and updated by in entity.");
            }
        }
    }
}
