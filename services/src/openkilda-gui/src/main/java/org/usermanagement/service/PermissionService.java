package org.usermanagement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.usermanagement.conversion.PermissionConversionUtil;
import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.repository.PermissionRepository;
import org.usermanagement.dao.repository.RoleRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Permission;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;
import org.usermanagement.validator.PermissionValidator;

@Service
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class PermissionService {

	@Autowired
	private PermissionRepository permissionRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PermissionValidator permissionValidator;

	@Autowired
	private MessageUtils messageUtil;

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public Permission createPermission(final Permission permission) {
		permissionValidator.validatePermission(permission);

		PermissionEntity permissionEntity = PermissionConversionUtil.toPermissionEntity(permission);
		permissionRepository.save(permissionEntity);

		return PermissionConversionUtil.toPermission(permissionEntity, null);
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public List<Permission> getAllPermission(final long loggedInUserId) {
		List<PermissionEntity> permissionEntities = permissionRepository.findAll();

        permissionEntities =
                permissionEntities.stream().filter(permission ->
                    loggedInUserId == 1 || !permission.getIsAdminPermission()).collect(Collectors.toList());
		List<Permission> permissions = PermissionConversionUtil.toAllPermissionResponse(permissionEntities);
		return permissions;
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public Permission getPermissionById(final Long permissionId) {
		PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
		Set<RoleEntity> roleEntityList = roleRepository.findByPermissions_permissionId(permissionId);

		if (ValidatorUtil.isNull(permissionEntity)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
		}

		return PermissionConversionUtil.toPermission(permissionEntity, roleEntityList);
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public void deletePermissionById(final Long permissionId) {

		PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
		if (ValidatorUtil.isNull(permissionEntity)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
		}
		Set<RoleEntity> roleEntityList = roleRepository.findByPermissions_permissionId(permissionId);
		if (roleEntityList.size() > 0) {
			String roles = "";
			for (RoleEntity roleEntity : roleEntityList) {
				roles += !"".equals(roles) ? "," + roleEntity.getName() : roleEntity.getName();
			}
			throw new RequestValidationException(
					messageUtil.getAttributeDeletionNotAllowed(permissionEntity.getName(), roles));
		}
		permissionRepository.delete(permissionEntity);

	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public Permission updatePermission(final Long permissionId, final Permission permission) {

		permissionValidator.validateUpdatePermission(permission, permissionId);

		PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
		if (ValidatorUtil.isNull(permissionEntity)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
		}

		permissionEntity = PermissionConversionUtil.toUpatePermissionEntity(permission, permissionEntity);
		permissionRepository.save(permissionEntity);

		return PermissionConversionUtil.toPermission(permissionEntity, null);

	}
}
