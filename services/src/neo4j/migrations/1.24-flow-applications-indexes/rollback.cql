DROP INDEX ON :`mirror_group`(`path_id`);
DROP INDEX ON :`mirror_group` (`switch_id`);
DROP INDEX ON :`mirror_group` (`flow_id`);
DROP CONSTRAINT ON (`mirror_group`:`mirror_group`) ASSERT `mirror_group`.`unique_index` IS UNIQUE;
DROP CONSTRAINT ON (`mirror_group`:`mirror_group`) ASSERT `mirror_group`.`entityId` IS UNIQUE;
DROP CONSTRAINT ON (`application_rule`:`application_rule`) ASSERT `application_rule`.`entityId` IS UNIQUE;
DROP CONSTRAINT ON (`exclusion_id`:`exclusion_id`) ASSERT `exclusion_id`.`entityId` IS UNIQUE;
DROP CONSTRAINT ON (`exclusion_id`:`exclusion_id`) ASSERT `exclusion_id`.`unique_index` IS UNIQUE;