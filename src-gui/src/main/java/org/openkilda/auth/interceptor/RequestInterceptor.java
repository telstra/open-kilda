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

package org.openkilda.auth.interceptor;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.Permissions;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.constants.IConstants;
import org.openkilda.constants.IConstants.ApplicationSetting;
import org.openkilda.constants.Status;
import org.openkilda.service.ApplicationSettingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.PermissionService;
import org.usermanagement.service.RoleService;
import org.usermanagement.util.MessageUtils;

import java.nio.file.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class RequestInterceptor extends HandlerInterceptorAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestInterceptor.class);
    private static final String CORRELATION_ID = "correlation_id";

    @Autowired
    private ServerContext serverContext;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private MessageUtils messageUtils;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ApplicationSettingService applicationSettingService;

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
            throws AccessDeniedException {
        String correlationId = request.getParameter(CORRELATION_ID);
        correlationId = correlationId == null ? UUID.randomUUID().toString() : correlationId;

        HttpSession session = request.getSession();
        UserInfo userInfo = null;
        if (IConstants.SessionTimeout.TIME_IN_MINUTE == null) {
            IConstants.SessionTimeout.TIME_IN_MINUTE = Integer.valueOf(applicationSettingService
                    .getApplicationSettings().get(ApplicationSetting.SESSION_TIMEOUT.name()));
        }
        session.setMaxInactiveInterval(IConstants.SessionTimeout.TIME_IN_MINUTE * 60);
        userInfo = (UserInfo) session.getAttribute(IConstants.SESSION_OBJECT);
        if (userInfo != null) {
            validateUser(userInfo);
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                Permissions permissions = handlerMethod.getMethod().getAnnotation(Permissions.class);
                if (permissions != null) {
                    validateAndPopulatePermisssion(userInfo, permissions);
                }
            }
            updateRequestContext(correlationId, request, userInfo);
        }
        return true;
    }

    @Override
    public void postHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler,
            final ModelAndView modelAndView) throws Exception {
        super.postHandle(request, response, handler, modelAndView);
        MDC.remove(CORRELATION_ID);
    }
    
    private void validateUser(final UserInfo userInfo) throws AccessDeniedException {
        UserEntity userEntity = userRepository.findByUserId(userInfo.getUserId());
        if (userEntity == null || !userEntity.getActiveFlag()) {
            throw new AccessDeniedException(messageUtils.getUnauthorizedMessage());
        }
    }

    private void updateRequestContext(final String correlationId, final HttpServletRequest request,
            final UserInfo userInfo) {
        RequestContext requestContext = serverContext.getRequestContext();
        requestContext.setCorrelationId(userInfo.getUsername() + "_" + correlationId);
        requestContext.setUserId(userInfo.getUserId());
        requestContext.setUserName(userInfo.getUsername());
        requestContext.setFullName(userInfo.getName());
        requestContext.setPermissions(userInfo.getPermissions());
        requestContext.setIs2FaEnabled(userInfo.getIs2FaEnabled());
        requestContext.setStatus(userInfo.getStatus());
        requestContext.setClientIpAddress(getClientIp(request));
        
        MDC.put(CORRELATION_ID, requestContext.getCorrelationId());
    }

    private void validateAndPopulatePermisssion(final UserInfo userInfo, final Permissions permissions)
            throws AccessDeniedException {
        if (!permissions.checkObjectAccessPermissions()) {
            if (!hasPermissions(userInfo, permissions.values())) {
                LOGGER.warn("Access Denied. User(id: " + userInfo.getUserId()
                        + ") not have the permission to perform this operation. Permissions required "
                        + permissions.values());
                throw new AccessDeniedException(messageUtils.getUnauthorizedMessage());
            }
        }
    }

    private boolean hasPermissions(final UserInfo userInfo, final String... permissions) {
        boolean hasPermission = true;
        Set<String> availablePermissions = availablePermissions(userInfo);
        if (!availablePermissions.isEmpty()) {
            for (String permission : permissions) {
                if (!availablePermissions.contains(permission)) {
                    hasPermission = false;
                    break;
                }
            }
        } else {
            hasPermission = false;
        }
        return hasPermission;
    }

    private Set<String> availablePermissions(final UserInfo userInfo) {
        Set<String> availablePermissions = new HashSet<>();
        UserEntity userEntity = userRepository.findByUserId(userInfo.getUserId());
        if (userInfo.getUserId() != 1 && userEntity != null
                && Status.ACTIVE.getStatusEntity().equals(userEntity.getStatusEntity())) {
            Set<String> roles = userInfo.getRoles();
            if (roles != null && roles.size() > 0) {
                List<Role> roleList = roleService.getRoleByName(roles);
                for (Role role : roleList) {
                    if (Status.ACTIVE.getStatusEntity().getStatus().equalsIgnoreCase(role.getStatus())
                            && role.getPermissions() != null) {
                        for (Permission permission : role.getPermissions()) {
                            if (Status.ACTIVE.getStatusEntity().getStatus().equalsIgnoreCase(permission.getStatus())) {
                                availablePermissions.add(permission.getName());
                            }
                        }
                    }
                }
            }
        } else {
            List<Permission> permissions = permissionService.getAllPermission(userInfo.getUserId());
            for (Permission permission : permissions) {
                availablePermissions.add(permission.getName());
            }
        }
        userInfo.setPermissions(availablePermissions);
        return availablePermissions;
    }

    private static String getClientIp(final HttpServletRequest request) {
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }

        return remoteAddr;
    }

}
