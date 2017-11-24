package org.openkilda.dao;

import java.io.Serializable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * 
 * @author Gaurav Chugh
 *
 * @param <T>
 * @param <ID>
 */
@NoRepositoryBean
public interface GenericRepository<T, ID extends Serializable> extends JpaRepository<T, ID> {

}
