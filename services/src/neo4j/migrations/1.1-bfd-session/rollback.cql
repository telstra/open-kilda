DROP CONSTRAINT ON ( bfd_session:bfd_session ) ASSERT bfd_session.entityId IS UNIQUE;
CREATE CONSTRAINT ON ( port_bfd:port_bfd ) ASSERT port_bfd.entityId IS UNIQUE;

DROP CONSTRAINT ON ( bfd_session:bfd_session ) ASSERT bfd_session.discriminator IS UNIQUE;
CREATE CONSTRAINT ON ( port_bfd:port_bfd ) ASSERT port_bfd.discriminator IS UNIQUE;
