package org.usermanagement.dao.entity;

import java.io.Serializable;
import java.sql.Clob;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.openkilda.entity.BaseEntity;

/**
 * The Class UserSettingEntity.
 */
@Entity
@Table(name = "KILDA_USER_SETTING")
public class UserSettingEntity extends BaseEntity implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/** The user id. */
	@Id
	@Column(name = "USER_SETTING_ID", nullable = false)
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long userSettingId;

	/** The userId. */
	@Column(name = "USER_ID", nullable = false)
	private Long userId;

	/** The settings. */
	@Column(name = "SETTINGS", nullable = false)
	@Lob
	private String settings;
	
	/** The data. */
	@Column(name = "DATA", nullable = true, columnDefinition="clob")
	@Lob
	private String data;

	public Long getUserSettingId() {
		return userSettingId;
	}

	public void setUserSettingId(Long userSettingId) {
		this.userSettingId = userSettingId;
	}

	public String getSettings() {
		return settings;
	}

	public void setSettings(String settings) {
		this.settings = settings;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	} 
	
	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "UserEntity [userSettingId=" + userSettingId + ", settings=" + settings + ", userId" + userId + "]";
	}
}
