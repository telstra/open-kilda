INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (11, 11, CURRENT_TIMESTAMP);
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(32, 'CONFIG_SESSION_TIMEOUT');
	
INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(340, 'session_timeout_setting', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for session timeout configuration -> configure session timeout settings');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 340);
	
INSERT INTO "APPLICATION_SETTING" (id, setting_type, setting_value) VALUES (1, 'session_timeout', '45');