package org.usermanagement.validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.repository.PermissionRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Permission;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

@Component
public class PermissionValidator {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private MessageUtils messageUtil;

    public void validatePermission(final Permission permission) {

        if (ValidatorUtil.isNull(permission.getName())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        }

        PermissionEntity permissionEntity = permissionRepository.findByName(permission.getName());
        if (permissionEntity != null) {
            throw new RequestValidationException(messageUtil.getAttributeUnique("name"));
        }
    }

    public void validateUpdatePermission(final Permission permission, final Long permissionId) {

        if (ValidatorUtil.isNull(permissionId)) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("permission_id"));
        } else if (ValidatorUtil.isNull(permission.getName()) && ValidatorUtil.isNull(permission.getStatus())
                && ValidatorUtil.isNull(permission.getDescription())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name,description and status"));
        }

        if(!ValidatorUtil.isNull(permission.getName())) {
            PermissionEntity permissionEntity = permissionRepository.findByName(permission.getName());
            if (permissionEntity != null && !permissionEntity.getPermissionId().equals(permissionId)) {
                throw new RequestValidationException(messageUtil.getAttributeUnique("name"));
            }
        }
    }
}
