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

public class RoleConversionUtil {
    public static RoleEntity toRoleEntity(final Role role, Set<PermissionEntity> permissionEntitySet) {


        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setName(role.getName());
        roleEntity.setPermissions(permissionEntitySet);
        roleEntity.setCreatedBy(1l);
        roleEntity.setCreatedDate(new Date());
        roleEntity.setUpdatedBy(1l);
        roleEntity.setUpdatedDate(new Date());
        roleEntity.setDescription(role.getDescription());

        StatusEntity statusEntity = Status.ACTIVE.getStatusEntity();
        roleEntity.setStatusEntity(statusEntity);
        return roleEntity;
    }

	public static Role toRole(final RoleEntity roleEntity) {
		Role role = new Role();
		role.setName(roleEntity.getName());
		role.setRoleId(roleEntity.getRoleId());
		role.setStatus(roleEntity.getStatusEntity().getStatus());
		role.setDescription(roleEntity.getDescription());

		List<Permission> permissionList = new ArrayList<Permission>();

		if (!ValidatorUtil.isNull(roleEntity.getPermissions())) {
			for (PermissionEntity permissionEntity : roleEntity.getPermissions()) {
				permissionList.add(PermissionConversionUtil.toPermission(permissionEntity, null));
			}
			role.setPermissions(permissionList);
		}
		return role;
	}

    public static List<Role> toAllRoleResponse(final List<RoleEntity> roleEntityList) {
        List<Role> roleList = new ArrayList<>();

        for (RoleEntity roleEntity : roleEntityList) {
            roleList.add(toRole(roleEntity));
        }
        return roleList;
    }

    public static Permission toPermissionByRole(final Set<RoleEntity> roleEntityList,
            final PermissionEntity permissionEntity) {
        Permission permission = new Permission();
        permission.setName(permissionEntity.getName());
        permission.setDescription(permissionEntity.getDescription());
        permission.setPermissionId(permissionEntity.getPermissionId());

        List<Role> role = new ArrayList<>();
        for (RoleEntity roleEntity : roleEntityList) {
            Role roles = new Role();
            roles.setRoleId(roleEntity.getRoleId());
            roles.setName(roleEntity.getName());
            role.add(roles);
        }

        permission.setRoles(role);
        return permission;
    }

    public static RoleEntity toUpateRoleEntity(final Role role, RoleEntity roleEntity) {
        if (!ValidatorUtil.isNull(role.getStatus())) {
            StatusEntity newStatusEntity = Status.getStatusByName(role.getStatus()).getStatusEntity();
            roleEntity.setStatusEntity(newStatusEntity);
        }

        if (!ValidatorUtil.isNull(role.getName())) {
            roleEntity.setName(role.getName());
        }

        if (!ValidatorUtil.isNull(role.getDescription())) {
            roleEntity.setDescription(role.getDescription());
        }

        roleEntity.setCreatedDate(new Date());
        roleEntity.setUpdatedDate(new Date());
        return roleEntity;
    }
}
