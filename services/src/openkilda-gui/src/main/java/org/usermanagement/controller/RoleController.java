package org.usermanagement.controller;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
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

import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.service.RoleService;

@RestController
@RequestMapping(path = "/role", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoleController {

    @Autowired
    private RoleService roleService;

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.POST)
    @Permissions(values = { IConstants.Permission.UM_ROLE_ADD})
    public Role create(@RequestBody final Role request) {
        Role roleResponse = roleService.createRole(request);

        return roleResponse;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<Role> getRoleList() {
        List<Role> roleResponseList = roleService.getAllRole();

        return roleResponseList;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.GET)
    public Role getRoleById(@PathVariable("role_id") final Long roleId) {
        Role role = roleService.getRoleById(roleId);

        return role;
    }


    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.DELETE)
    @Permissions(values = { IConstants.Permission.UM_ROLE_DELETE})
    public void deleteRoleById(@PathVariable("role_id") Long roleId) {
        roleService.deleteRoleById(roleId);

    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.GET)
    @Permissions(values = { IConstants.Permission.UM_PERMISSION_VIEW_ROLES})
    public Permission getRolesByPermissionId(@PathVariable("permission_id") final Long permissionId) {
        Permission permission = roleService.getRolesByPermissionId(permissionId);

        return permission;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.PUT)
    @Permissions(values = { IConstants.Permission.UM_ROLE_EDIT})
    public Role updateRole(@PathVariable("role_id") Long roleId, @RequestBody final Role request) {
        Role roleResponse = roleService.updateRole(roleId, request);
        return roleResponse;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.PUT)
    @Permissions(values = {IConstants.Permission.UM_PERMISSION_ASSIGN_ROLES})
    public Permission assignRoleToPermission(@PathVariable("permission_id") final Long permissionId,
            @RequestBody Permission request) {
        Permission permission = roleService.assignRoleByPermissionId(permissionId, request);

        return permission;
    }
}
