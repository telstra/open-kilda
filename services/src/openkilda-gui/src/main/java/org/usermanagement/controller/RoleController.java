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
    public void deleteRoleById(@PathVariable("role_id") Long roleId) {
        roleService.deleteRoleById(roleId);

    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.GET)
    public Permission getRolesByPermissionId(@PathVariable("permission_id") final Long permissionId) {
        Permission permission = roleService.getRolesByPermissionId(permissionId);

        return permission;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.PUT)
    public Role updateRole(@PathVariable("role_id") Long roleId, @RequestBody final Role request) {
        Role roleResponse = roleService.updateRole(roleId, request);
        return roleResponse;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.PUT)
    public Permission assignRoleToPermission(@PathVariable("permission_id") final Long permissionId,
            @RequestBody Permission request) {
        Permission permission = roleService.assignRoleByPermissionId(permissionId, request);

        return permission;
    }
}
