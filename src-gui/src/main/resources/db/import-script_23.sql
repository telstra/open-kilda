INSERT INTO VERSION_ENTITY (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (23, 23, CURRENT_TIMESTAMP);

INSERT INTO KILDA_PERMISSION (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(352, 'menu_available_path', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for available path menu');
	
INSERT INTO ROLE_PERMISSION (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 352);