LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/ACTIVITY_TYPE.csv' IGNORE INTO TABLE ACTIVITY_TYPE FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (activity_type_id,activity_name);

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/APPLICATION_SETTING.csv' IGNORE INTO TABLE APPLICATION_SETTING FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (id,description,setting_type,@vupdated_date,setting_value) SET updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_AUTH_TYPE.csv' IGNORE INTO TABLE KILDA_AUTH_TYPE FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (auth_type_id,auth_type_code,auth_type_name);

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_LINK_STORE_URLS.csv' IGNORE INTO TABLE KILDA_LINK_STORE_URLS FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (link_store_url_id,created_by,@vcreated_date,updated_by,@vupdated_date,link_url_id) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_OAUTH_CONFIG.csv' IGNORE INTO TABLE KILDA_OAUTH_CONFIG FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (oauth_config_id,created_by,@vcreated_date,updated_by,@vupdated_date,password,username,auth_type_id,generate_token_id,refresh_token_id) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_STATUS.csv' IGNORE INTO TABLE KILDA_STATUS FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (status_id,created_by,@vcreated_date,updated_by,@vupdated_date,status,status_code) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_PERMISSION.csv' IGNORE INTO TABLE KILDA_PERMISSION FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (permission_id,created_by,@vcreated_date,updated_by,@vupdated_date,description,@vis_admin_permission,@vis_editable,permission,status_id) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,''), is_admin_permission = (@vis_admin_permission = 'True'), is_editable = (@vis_editable = 'True');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_ROLE.csv' IGNORE INTO TABLE KILDA_ROLE FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (role_id,created_by,@created_date,updated_by,@vupdated_date,description,role,status_id) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_STORE_TYPE.csv' IGNORE INTO TABLE KILDA_STORE_TYPE FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (store_type_id,store_type_code,store_type_name,@oauth_config_id) SET oauth_config_id = NULLIF(@oauth_config_id,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_SWITCH_STORE_URLS.csv' IGNORE INTO TABLE KILDA_SWITCH_STORE_URLS FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (switch_store_url_id,created_by,@vcreated_date,updated_by,@vupdated_date,switch_url_id) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_URLS.csv' IGNORE INTO TABLE KILDA_URLS FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (url_id,created_by,@vcreated_date,updated_by,@vupdated_date,body,header,method_type,name,url) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_USER.csv' IGNORE INTO TABLE KILDA_USER FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (USER_ID,CREATED_BY,@vCREATED_DATE,UPDATED_BY,@vUPDATED_DATE,ACTIVE_FLAG,EMAIL,@vis_two_fa_configured,@vis_two_fa_enabled,IS_AUTHORIZED,@vLOGIN_TIME,@vLOGOUT_TIME,NAME,PASSWORD,two_fa_key,USERNAME,status_id) SET CREATED_DATE = NULLIF(@vCREATED_DATE,''),UPDATED_DATE = NULLIF(@vUPDATED_DATE,''),LOGIN_TIME = NULLIF(@vLOGIN_TIME,''),LOGOUT_TIME = NULLIF(@vLOGOUT_TIME,''), is_two_fa_enabled = (@vis_two_fa_enabled = 'True'), is_two_fa_configured = (@vis_two_fa_configured = 'True');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_PERMISSION.csv' IGNORE INTO TABLE KILDA_PERMISSION FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (permission_id,created_by,@vcreated_date,updated_by,@vupdated_date,description,@vis_admin_permission,@vis_editable,permission,status_id) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,''), is_admin_permission = (@vis_admin_permission = 'True'), is_editable = (@vis_editable = 'True');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/KILDA_USER_SETTING.csv' IGNORE INTO TABLE KILDA_USER_SETTING FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (user_setting_id,created_by,@vcreated_date,updated_by,@vupdated_date,data,settings,user_id) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/ROLE_PERMISSION.csv' IGNORE INTO TABLE ROLE_PERMISSION FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (permission_id,role_id);

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/SAML_CONFIGURATION.csv' IGNORE INTO TABLE SAML_CONFIGURATION FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (id,created_by,@vcreated_date,updated_by,@vupdated_date,attribute,entity_id,metadata,name,@vstatus,type,url,@vuser_creation,uuid) SET created_date = NULLIF(@vcreated_date,''), updated_date = NULLIF(@vupdated_date,''), status = (@vstatus = 'True'), user_creation = (@vuser_creation = 'True');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/SAML_USER_ROLES.csv' IGNORE INTO TABLE SAML_USER_ROLES FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (id,role_id);

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/SWITCH_NAME.csv' IGNORE INTO TABLE SWITCH_NAME FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (id,switch_dpid,switch_name,@vupdated_date) SET updated_date = NULLIF(@vupdated_date,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/USER_ACTIVITY.csv' IGNORE INTO TABLE USER_ACTIVITY FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (user_activity_id,@vactivity_time,client_ip,object_id,version_number,actvity_id) SET activity_time = NULLIF(@vactivity_time,'');

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/USER_ROLE.csv' IGNORE INTO TABLE USER_ROLE FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (user_id,role_id);

LOAD DATA LOCAL INFILE  '/opt/derby/derby-data/VERSION_ENTITY.csv' IGNORE INTO TABLE VERSION_ENTITY FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (version_id,@vversion_deployment_date,version_number) SET version_deployment_date = NULLIF(@vversion_deployment_date,'');
