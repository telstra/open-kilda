DROP CONSTRAINT ON (`port_properties`:`port_properties`) ASSERT `port_properties`.`entityId` IS UNIQUE;
DROP CONSTRAINT ON (`port_properties`:`port_properties`) ASSERT `port_properties`.`discriminator` IS UNIQUE;
