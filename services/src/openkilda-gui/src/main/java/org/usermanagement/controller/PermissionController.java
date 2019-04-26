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
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.PermissionService;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * The Class PermissionController.
 */

@RestController
@RequestMapping(path = "/api/permission", produces = MediaType.APPLICATION_JSON_VALUE)
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    /**
     * Creates the permission.
     *
     * @param permission the permission
     * @return the permission
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.POST)
    @Permissions(values = { IConstants.Permission.UM_PERMISSION_ADD })
    public Permission createPermission(@RequestBody final Permission permission) {
        Permission permissionResponse = permissionService.createPermission(permission);
        return permissionResponse;
    }

    /**
     * Gets the permission list.
     *
     * @param request the request
     * @return the permission list
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<Permission> getPermissionList(final HttpServletRequest request) {
        UserInfo userInfo = (UserInfo) request.getSession().getAttribute(IConstants.SESSION_OBJECT);
        return permissionService.getAllPermission(userInfo.getUserId());
    }

    /**
     * Gets the permission by id.
     *
     * @param permissionId the permission id
     * @return the permission by id
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{permission_id}", method = RequestMethod.GET)
    public Permission getPermissionById(@PathVariable("permission_id") final Long permissionId) {
        return permissionService.getPermissionById(permissionId);
    }

    /**
     * Delete permission by id.
     *
     * @param permissionId the permission id
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequestMapping(value = "/{permission_id}", method = RequestMethod.DELETE)
    @Permissions(values = { IConstants.Permission.UM_PERMISSION_DELETE })
    public void deletePermissionById(@PathVariable("permission_id") final Long permissionId) {
        permissionService.deletePermissionById(permissionId);
    }

    /**
     * Update permission.
     *
     * @param permissionId the permission id
     * @param permission the permission
     * @return the permission
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{permission_id}", method = RequestMethod.PUT)
    @Permissions(values = { IConstants.Permission.UM_PERMISSION_EDIT })
    public Permission updatePermission(@PathVariable("permission_id") final Long permissionId,
            @RequestBody final Permission permission) {
        return permissionService.updatePermission(permissionId, permission);
    }
}
