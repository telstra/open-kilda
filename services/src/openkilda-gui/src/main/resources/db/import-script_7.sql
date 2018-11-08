INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (7, 7, CURRENT_TIMESTAMP);
	
INSERT  INTO "KILDA_STORE_TYPE" (store_type_id, store_type_name, store_type_code) VALUES 
	(1, 'Link Store', 'LINK_STORE');
	
INSERT  INTO "KILDA_AUTH_TYPE" (auth_type_id, auth_type_name, auth_type_code) VALUES 
	(1, 'Oauth 2.0', 'OAUTH_TWO');
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(26, 'UPDATE_LINK_STORE_CONFIG'),
	(27, 'UPDATE_OAUTH_CONFIG');
	
INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(239, 'store_setting', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for store configuration -> configure store settings');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 239);