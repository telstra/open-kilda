package org.openkilda.auth.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import java.nio.file.AccessDeniedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.Permissions;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.constants.IConstants;
import org.openkilda.constants.Status;
import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.RoleService;
import org.usermanagement.util.MessageUtils;


@Component
public class RequestInterceptor extends HandlerInterceptorAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestInterceptor.class);
    private static final String CORRELATION_ID = "correlationid";

    @Autowired
    private ServerContext serverContext;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MessageUtils messageUtils;

	@Override
	public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
			throws Exception {
	    String correlationId = request.getParameter(CORRELATION_ID);
	    correlationId = correlationId == null ? UUID.randomUUID().toString() : correlationId;

		try {
	        MDC.put(CORRELATION_ID, correlationId);
	        HttpSession session = request.getSession();
	        UserInfo userInfo = null;


			userInfo = (UserInfo) session.getAttribute(IConstants.SESSION_OBJECT);
			if(userInfo != null) {
			    if (handler instanceof HandlerMethod) {
	                HandlerMethod handlerMethod = (HandlerMethod) handler;
	                Permissions permissions = handlerMethod.getMethod().getAnnotation(Permissions.class);
	                LOGGER.info("[preHandle] Checking permissions");
	                if (permissions != null) {
	                    LOGGER.info("[preHandle] Permission values: " + permissions.values());
	                    validateAndPopulatePermisssion(userInfo, permissions);
	                }
	            }

                updateRequestContext(correlationId, request, userInfo);
			}
		} catch (IllegalStateException ex) {
			LOGGER.info("[getLoggedInUser] Exception while retrieving user information from session. Exception: "
					+ ex.getLocalizedMessage(), ex);
		}
		return true;
	}

	@Override
	public void postHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler,
	        final ModelAndView modelAndView) throws Exception {
	    super.postHandle(request, response, handler, modelAndView);
	    MDC.remove(CORRELATION_ID);
	}

	private void updateRequestContext(final String correlationId, final HttpServletRequest request, final UserInfo userInfo) {
	    RequestContext requestContext = serverContext.getRequestContext();
	    requestContext.setCorrelationId(correlationId);
	    requestContext.setUserId(userInfo.getUserId());
	    requestContext.setUserName(userInfo.getUsername());
	    requestContext.setPermissions(userInfo.getPermissions());

	    requestContext.setClientIpAddress(getClientIp(request));
    }

    private void validateAndPopulatePermisssion(final UserInfo userInfo, final Permissions permissions)
			throws Exception {
		if (!permissions.checkObjectAccessPermissions()) {
			if (!hasPermissions(userInfo, permissions.values())) {
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
		Set<String> roles = userInfo.getRoles();
		if (roles != null && roles.size() > 0) {
			List<Role> roleList = roleService.getRoleByName(roles);
			for (Role role : roleList) {
				if (role.getPermissions() != null) {
					for (Permission permission : role.getPermissions()) {
						if (Status.ACTIVE.getStatusEntity().getStatus().equalsIgnoreCase(permission.getStatus())) {
							availablePermissions.add(permission.getName());
						}
					}
				}
			}
		}
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
