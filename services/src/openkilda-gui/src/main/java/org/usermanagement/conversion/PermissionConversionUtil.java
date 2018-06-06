package org.usermanagement.conversion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.openkilda.constants.Status;
import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.StatusEntity;
import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.util.ValidatorUtil;

public class PermissionConversionUtil {

    public static List<Permission> toAllPermissionResponse(final List<PermissionEntity> permissionList) {
        List<Permission> permissions = new ArrayList<>();

        for (PermissionEntity permissionEntity : permissionList) {
            permissions.add(toPermission(permissionEntity,null));
        }
        return permissions;
    }

    public static PermissionEntity toPermissionEntity(final Permission permission) {
        PermissionEntity permissionEntity = new PermissionEntity();
        permissionEntity.setName(permission.getName());
        permissionEntity.setCreatedBy(1l);
        permissionEntity.setUpdatedBy(1l);
        permissionEntity.setCreatedDate(new Date());
        permissionEntity.setUpdatedDate(new Date());
        permissionEntity.setDescription(permission.getDescription());
        permissionEntity.setIsEditable(permission.getIsEditable());
        permissionEntity.setIsAdminPermission(permission.getIsAdminPermission());
        StatusEntity statusEntity = Status.ACTIVE.getStatusEntity();
        permissionEntity.setStatusEntity(statusEntity);
        return permissionEntity;
    }

    public static Permission toPermission(final PermissionEntity permissionEntity, final Set<RoleEntity> roleEntities) {
        Permission permission = new Permission();
        permission.setName(permissionEntity.getName());
        permission.setPermissionId(permissionEntity.getPermissionId());
        permission.setIsEditable(permissionEntity.getIsEditable());
        permission.setIsAdminPermission(permissionEntity.getIsAdminPermission());
        permission.setStatus(permissionEntity.getStatusEntity().getStatus());
        permission.setDescription(permissionEntity.getDescription());

        if (!ValidatorUtil.isNull(roleEntities)) {
            List<Role> roles = new ArrayList<>();
            for (RoleEntity entity : roleEntities) {
                Role role = new Role();
                role.setRoleId(entity.getRoleId());
                role.setName(entity.getName());
                roles.add(role);
            }
            permission.setRoles(roles);
        }
        return permission;
    }

    public static PermissionEntity toUpatePermissionEntity(final Permission permission,
            final PermissionEntity permissionEntity) {
        if (!ValidatorUtil.isNull(permission.getStatus())) {
            StatusEntity newStatusEntity = Status.getStatusByName(permission.getStatus()).getStatusEntity();
            permissionEntity.setStatusEntity(newStatusEntity);
        }

        if (!ValidatorUtil.isNull(permission.getName())) {
            permissionEntity.setName(permission.getName());
        }

        if (!ValidatorUtil.isNull(permission.getDescription())) {
            permissionEntity.setDescription(permission.getDescription());
        }

        permissionEntity.setCreatedDate(new Date());
        permissionEntity.setUpdatedDate(new Date());
        return permissionEntity;
    }
}
