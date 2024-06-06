#!/bin/bash

LIBS=lib

[ ! -d $LIBS ] && mkdir $LIBS

LIQUIBASE_VERSION=3.5.5
LIQUIBASE_SHA256=da0df7f6ea7694fb62b1448c3c734644d8d49795ff9bb7417db18bb1da5ff2c6
LIQUIBASE_URL="https://github.com/liquibase/liquibase/releases/download/liquibase-parent-${LIQUIBASE_VERSION}/liquibase-${LIQUIBASE_VERSION}-bin.tar.gz"
[ ! -f $LIBS/liquibase-${LIQUIBASE_VERSION}.tar.gz ] \
  && wget -nc -nv -O $LIBS/liquibase-${LIQUIBASE_VERSION}.tar.gz $LIQUIBASE_URL \
  && echo "$LIQUIBASE_SHA256 $LIBS/liquibase-${LIQUIBASE_VERSION}.tar.gz" | sha256sum -c

[ ! -f $LIBS/orientdb-jdbc-3.0.34-all.jar ] \
  && wget -nc -nv -O $LIBS/orientdb-jdbc-3.0.34-all.jar \
             "https://repo1.maven.org/maven2/com/orientechnologies/orientdb-jdbc/3.0.34/orientdb-jdbc-3.0.34-all.jar"  \
  && wget -nc -nv -O $LIBS/commons-lang3-3.6.jar \
             "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.6/commons-lang3-3.6.jar" \
  && wget -nc -nv -O $LIBS/commons-beanutils-1.9.2.jar \
             "https://repo1.maven.org/maven2/commons-beanutils/commons-beanutils/1.9.2/commons-beanutils-1.9.2.jar" \
  && wget -nc -nv -O $LIBS/guava-23.3-jre.jar \
             "https://repo1.maven.org/maven2/com/google/guava/guava/23.3-jre/guava-23.3-jre.jar" \
  && wget -nc -nv -O $LIBS/validation-api-1.1.0.Final.jar \
             "https://repo1.maven.org/maven2/javax/validation/validation-api/1.1.0.Final/validation-api-1.1.0.Final.jar" \
  && wget -nc -nv -O $LIBS/hibernate-validator-5.2.4.Final.jar \
             "https://repo1.maven.org/maven2/org/hibernate/hibernate-validator/5.2.4.Final/hibernate-validator-5.2.4.Final.jar" \
  && wget -nc -nv -O $LIBS/jackson-databind-2.6.7.jar \
             "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.6.7/jackson-databind-2.6.7.jar" \
  && wget -nc -nv -O $LIBS/jboss-logging-3.2.1.Final.jar \
             "https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.2.1.Final/jboss-logging-3.2.1.Final.jar" \
  && wget -nc -nv -O $LIBS/classmate-1.1.0.jar \
             "https://repo1.maven.org/maven2/com/fasterxml/classmate/1.1.0/classmate-1.1.0.jar" \
