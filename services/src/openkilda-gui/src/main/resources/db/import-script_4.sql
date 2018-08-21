INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (4, 4, CURRENT_TIMESTAMP);

INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(136, 'sw_port_config', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for switches -> configure port');
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(21, 'CONFIGURE_SWITCH_PORT');	
