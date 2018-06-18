package org.usermanagement.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usermanagement.model.Permission;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.PermissionService;

@RestController
@RequestMapping(path = "/permission", produces = MediaType.APPLICATION_JSON_VALUE)
public class PermissionController {

	private static final Logger LOGGER = LoggerFactory.getLogger(PermissionController.class);
	
    @Autowired
    private PermissionService permissionService;
    
    @Autowired
    private ActivityLogger activityLogger;

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.POST)
    @Permissions(values = {IConstants.Permission.UM_PERMISSION_ADD})
	public Permission createPermission(@RequestBody final Permission permission) {
		activityLogger.log(ActivityType.CREATE_PERMISSION, permission.getName());
		LOGGER.info("[createPermission] (name: " + permission.getName() + ")");
		Permission permissionResponse = permissionService.createPermission(permission);
		return permissionResponse;
	}

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<Permission> getPermissionList(final HttpServletRequest request) {
        UserInfo userInfo = (UserInfo) request.getSession().getAttribute(IConstants.SESSION_OBJECT);
        LOGGER.info("[getPermissionList] (userId: " + userInfo.getUserId() +")");
        return permissionService.getAllPermission(userInfo.getUserId());
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{permission_id}", method = RequestMethod.GET)
    public Permission getPermissionById(@PathVariable("permission_id") final Long permissionId) {
    	LOGGER.info("[getPermissionById] (permissionId: " + permissionId +")");
        return permissionService.getPermissionById(permissionId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequestMapping(value = "/{permission_id}", method = RequestMethod.DELETE)
    @Permissions(values = {IConstants.Permission.UM_PERMISSION_DELETE})
	public void deletePermissionById(@PathVariable("permission_id") final Long permissionId) {
		activityLogger.log(ActivityType.DELETE_PERMISSION, permissionId + "");
		LOGGER.info("[deletePermissionById] (permissionId: " + permissionId + ")");
		permissionService.deletePermissionById(permissionId);
	}

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{permission_id}", method = RequestMethod.PUT)
    @Permissions(values = {IConstants.Permission.UM_PERMISSION_EDIT})
	public Permission updatePermission(@PathVariable("permission_id") final Long permissionId,
			@RequestBody final Permission permission) {
		activityLogger.log(ActivityType.UPDATE_PERMISSION, permissionId + "");
		LOGGER.info("[updatePermission] (permissionId: " + permissionId + ")");
		return permissionService.updatePermission(permissionId, permission);
	}
}
