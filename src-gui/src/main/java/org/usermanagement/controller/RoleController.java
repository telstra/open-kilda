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

import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.service.RoleService;

import java.util.List;

/**
 * The Class RoleController.
 */

@RestController
@RequestMapping(path = "/api/role", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoleController {

    @Autowired
    private RoleService roleService;

    /**
     * Creates the role.
     *
     * @param role the role
     * @return the role
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.POST)
    @Permissions(values = { IConstants.Permission.UM_ROLE_ADD })
    public Role createRole(@RequestBody final Role role) {
        Role roleResponse = roleService.createRole(role);
        return roleResponse;
    }

    /**
     * Gets the roles.
     *
     * @return the roles
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<Role> getRoles() {
        List<Role> roleResponseList = roleService.getAllRole();
        return roleResponseList;
    }

    /**
     * Gets the role by id.
     *
     * @param roleId the role id
     * @return the role by id
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.GET)
    public Role getRoleById(@PathVariable("role_id") final Long roleId) {
        Role role = roleService.getRoleById(roleId);
        return role;
    }

    /**
     * Delete role.
     *
     * @param roleId the role id
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.DELETE)
    @Permissions(values = { IConstants.Permission.UM_ROLE_DELETE })
    public void deleteRole(@PathVariable("role_id") Long roleId) {
        roleService.deleteRoleById(roleId);

    }

    /**
     * Gets the roles by permission id.
     *
     * @param permissionId the permission id
     * @return the roles by permission id
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.GET)
    @Permissions(values = { IConstants.Permission.UM_PERMISSION_VIEW_ROLES })
    public Permission getRolesByPermissionId(@PathVariable("permission_id") final Long permissionId) {
        Permission permission = roleService.getRolesByPermissionId(permissionId);
        return permission;
    }

    /**
     * Update role.
     *
     * @param roleId the role id
     * @param role the role
     * @return the role
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{role_id}", method = RequestMethod.PUT)
    @Permissions(values = { IConstants.Permission.UM_ROLE_EDIT })
    public Role updateRole(@PathVariable("role_id") Long roleId, @RequestBody final Role role) {
        Role roleResponse = roleService.updateRole(roleId, role);
        return roleResponse;
    }

    /**
     * Assign roles to permission.
     *
     * @param permissionId the permission id
     * @param permission the permission
     * @return the permission
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/permission/{permission_id}", method = RequestMethod.PUT)
    @Permissions(values = { IConstants.Permission.UM_PERMISSION_ASSIGN_ROLES })
    public Permission assignRolesToPermission(@PathVariable("permission_id") final Long permissionId,
            @RequestBody Permission permission) {
        permission = roleService.assignRoleByPermissionId(permissionId, permission);
        return permission;
    }
}
