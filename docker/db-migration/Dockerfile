# Copyright 2020 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

# Can't use official lisquibase image, because it's minimal version is 3.10.x, while
# liquebase-orientdb(https://github.com/unbroken-dome/liquibase-orientdb) plugin
# can work up to version 3.5.3

FROM openjdk:11-jre-slim-buster

# Install GPG for package vefification
RUN apt-get update \
    && apt-get -y install gnupg wget curl

# Add the liquibase user and step in the directory
RUN addgroup --gid 1001 liquibase
RUN adduser --disabled-password --uid 1001 --ingroup liquibase liquibase

# Make /liquibase directory and change owner to liquibase
RUN mkdir /liquibase && chown liquibase /liquibase
WORKDIR /liquibase

# Change to the liquibase user
USER liquibase

# Latest Liquibase Release Version
ARG LIQUIBASE_VERSION=3.5.5

# Download, verify, extract
ARG LB_SHA256=da0df7f6ea7694fb62b1448c3c734644d8d49795ff9bb7417db18bb1da5ff2c6
RUN set -x \
  && wget -O liquibase-${LIQUIBASE_VERSION}.tar.gz "https://github.com/liquibase/liquibase/releases/download/liquibase-parent-${LIQUIBASE_VERSION}/liquibase-${LIQUIBASE_VERSION}-bin.tar.gz" \
  && echo "$LB_SHA256  liquibase-${LIQUIBASE_VERSION}.tar.gz" | sha256sum -c - \
  && tar -xzf liquibase-${LIQUIBASE_VERSION}.tar.gz \
  && rm liquibase-${LIQUIBASE_VERSION}.tar.gz

# Setup GPG
RUN GNUPGHOME="$(mktemp -d)"

# Download JDBC libraries and plugins

RUN set -x \
  && mkdir -p /liquibase/lib/ \
  && wget -O /liquibase/lib/orientdb-jdbc-3.0.34-all.jar \
             "https://repo1.maven.org/maven2/com/orientechnologies/orientdb-jdbc/3.0.34/orientdb-jdbc-3.0.34-all.jar"  \
  # && wget -O /liquibase/lib/liquibase-orientdb-0.3.0.jar \
  #            "https://dl.bintray.com/till-krullmann/tools/org/unbroken-dome/liquibase-orientdb/liquibase-orientdb/0.3.0/liquibase-orientdb-0.3.0.jar"  \
  # black magic satisfying dependencies of `liquibase-orientdb-0.3.0.jar`
  && wget -O /liquibase/lib/commons-lang3-3.6.jar \
             "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.6/commons-lang3-3.6.jar" \
  && wget -O /liquibase/lib/commons-beanutils-1.9.2.jar \
             "https://repo1.maven.org/maven2/commons-beanutils/commons-beanutils/1.9.2/commons-beanutils-1.9.2.jar" \
  && wget -O /liquibase/lib/guava-23.3-jre.jar \
             "https://repo1.maven.org/maven2/com/google/guava/guava/23.3-jre/guava-23.3-jre.jar" \
  && wget -O /liquibase/lib/validation-api-1.1.0.Final.jar \
             "https://repo1.maven.org/maven2/javax/validation/validation-api/1.1.0.Final/validation-api-1.1.0.Final.jar" \
  && wget -O /liquibase/lib/hibernate-validator-5.2.4.Final.jar \
             "https://repo1.maven.org/maven2/org/hibernate/hibernate-validator/5.2.4.Final/hibernate-validator-5.2.4.Final.jar" \
  && wget -O /liquibase/lib/jackson-databind-2.6.7.jar \
             "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.6.7/jackson-databind-2.6.7.jar" \
  && wget -O /liquibase/lib/jboss-logging-3.2.1.Final.jar \
             "https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.2.1.Final/jboss-logging-3.2.1.Final.jar" \
  && wget -O /liquibase/lib/classmate-1.1.0.jar \
             "https://repo1.maven.org/maven2/com/fasterxml/classmate/1.1.0/classmate-1.1.0.jar" \
  && chown -R liquibase:liquibase /liquibase/lib/

COPY --chown=liquibase:liquibase lib/liquibase-orientdb-0.3.0.jar /liquibase/lib/liquibase-orientdb-0.3.0.jar

ENV KILDA_ORIENTDB_USER=kilda
ENV KILDA_ORIENTDB_PASSWORD=kilda
ENV KILDA_ORIENTDB_HOST=odb1.pendev
ENV KILDA_ORIENTDB_DB_NAME=kilda

USER root

COPY migrate.sh migrate-develop.sh apply-prehistory-migrations.sh /kilda/
RUN install install -g liquibase -o liquibase -d /kilda/flag
COPY migrations /liquibase/migrations

USER liquibase

CMD /kilda/migrate.sh
