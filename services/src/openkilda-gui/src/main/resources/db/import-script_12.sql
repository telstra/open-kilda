INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (12, 12, CURRENT_TIMESTAMP);
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(33, 'CONFIG_SWITCH_NAME_STORAGE_TYPE'),
	(34, 'UPDATE_SWITCH_NAME');
	
INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(341, 'sw_switch_update_name', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission to switch name update -> to switch name update');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 341);
	
INSERT INTO "APPLICATION_SETTING" (id, setting_type, setting_value) VALUES (2, 'switch_name_storage_type', 'FILE_STORAGE');

UPDATE "KILDA_PERMISSION" SET PERMISSION = 'application_setting', DESCRIPTION = 'Permission to update application Settings-> to update application settings' WHERE PERMISSION_ID = 340; 