INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (3, 3, CURRENT_TIMESTAMP);

INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(33, 'fw_flow_create', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for flow management -> create flow'),
	(34, 'fw_flow_update', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for flow management -> update flow'),
	(35, 'fw_flow_delete', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for flow management -> delete flow');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 33),
	(2, 34),
	(2, 35);