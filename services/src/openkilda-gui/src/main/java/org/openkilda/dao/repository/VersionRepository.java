package org.openkilda.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.openkilda.dao.entity.VersionEntity;

@Repository
public interface VersionRepository extends JpaRepository<VersionEntity, Long> {

}
