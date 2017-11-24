
-- Dumping database structure for openkilda
CREATE DATABASE IF NOT EXISTS `openkilda` /*!40100 DEFAULT CHARACTER SET latin1 */;
USE `openkilda`;

-- Dumping structure for table openkilda.role
CREATE TABLE IF NOT EXISTS `role` (
  `user_role_id` bigint(11) NOT NULL,
  `user_role` varchar(100) NOT NULL,
  PRIMARY KEY (`user_role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Data exporting was unselected.
-- Dumping structure for table openkilda.user
CREATE TABLE IF NOT EXISTS `user` (
  `UserId` bigint(20) NOT NULL AUTO_INCREMENT,
  `Username` varchar(50) DEFAULT NULL,
  `Password` varchar(200) DEFAULT NULL,
  `Name` varchar(50) DEFAULT NULL,
  `Email` varchar(50) DEFAULT NULL,
  `LoginTime` timestamp NULL DEFAULT NULL,
  `LogoutTime` timestamp NULL DEFAULT NULL,
  `ActiveFlag` tinyint(4) DEFAULT NULL,
  `IsAuthorized` varchar(50) DEFAULT NULL,
  `CreatedBy` bigint(20) DEFAULT NULL,
  `CreatedDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `UpdatedDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `UpdatedBy` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`UserId`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=latin1;

-- Data exporting was unselected.
-- Dumping structure for table openkilda.user_role
CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FK_role` (`role_id`),
  KEY `FK_user` (`user_id`),
  CONSTRAINT `FK_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`user_role_id`),
  CONSTRAINT `FK_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`UserId`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;