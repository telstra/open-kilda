package org.usermanagement.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.usermanagement.dao.entity.PermissionEntity;

@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    PermissionEntity findByPermissionId(Long permissionId);

    PermissionEntity findByName(String permissionName);
}
