package org.usermanagement.controller;

import java.util.List;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.service.RoleService;

@RestController
@RequestMapping(path = "/role", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoleController {

	private static final Logger LOGGER = LoggerFactory.getLogger(RoleController.class);
	
    @Autowired
    private RoleService roleService;
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.POST)
    @Permissions(values = { IConstants.Permission.UM_ROLE_ADD})
	public Role createRole(@RequestBody final Role role) {
		LOGGER.info("[createRole] (name: " + role.getName() + ")");
		Role roleResponse = roleService.createRole(role);
		return roleResponse;
	}

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<Role> getRoles() {
    	LOGGER.info("[getRoles]");
        List<Role> roleResponseList = roleService.getAllRole();
        return roleResponseList;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.GET)
    public Role getRoleById(@PathVariable("role_id") final Long roleId) {
    	LOGGER.info("[getRoleById] (id: " + roleId +")");
        Role role = roleService.getRoleById(roleId);
        return role;
    }


    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.DELETE)
    @Permissions(values = { IConstants.Permission.UM_ROLE_DELETE})
    public void deleteRole(@PathVariable("role_id") Long roleId) {
    	LOGGER.info("[deleteRoleById] (id: " + roleId +")");
        roleService.deleteRoleById(roleId);

    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.GET)
    @Permissions(values = { IConstants.Permission.UM_PERMISSION_VIEW_ROLES})
    public Permission getRolesByPermissionId(@PathVariable("permission_id") final Long permissionId) {
    	LOGGER.info("[getRolesByPermissionId] (permissionId: " + permissionId +")");
        Permission permission = roleService.getRolesByPermissionId(permissionId);
        return permission;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.PUT)
    @Permissions(values = { IConstants.Permission.UM_ROLE_EDIT})
	public Role updateRole(@PathVariable("role_id") Long roleId, @RequestBody final Role role) {
		LOGGER.info("[updateRole] (id: " + roleId + ", name: " + role.getName() + ")");
		Role roleResponse = roleService.updateRole(roleId, role);
		return roleResponse;
	}

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.PUT)
    @Permissions(values = {IConstants.Permission.UM_PERMISSION_ASSIGN_ROLES})
	public Permission assignRolesToPermission(@PathVariable("permission_id") final Long permissionId,
			@RequestBody Permission permission) {
		LOGGER.info("[assignRoleToPermission] (permissionId: " + permissionId + ")");
		permission = roleService.assignRoleByPermissionId(permissionId, permission);
		return permission;
	}
}
