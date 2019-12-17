#
# Build stage
#
FROM maven:3.6.3-jdk-8-slim AS build

RUN curl -sL https://github.com/apache/flink/archive/release-1.8.3.tar.gz | tar zx
#RUN cd flink-release-1.8.3 && mvn clean install -B -DskipTests -Dfast -Pinclude-kinesis -pl flink-connectors/flink-connector-kinesis
RUN cd flink-release-1.8.3 \
    && mvn -B -s /usr/share/maven/ref/settings-docker.xml clean install -DskipTests -Dfast \
    		-Daws.sdk.version=1.11.319 \
            -Daws.kinesis-kcl.version=1.9.0 \
            -Daws.kinesis-kpl.version=0.12.9 \
            -Daws.dynamodbstreams-kinesis-adapter.version=1.4.0 \
            -Pinclude-kinesis \
            -pl flink-connectors/flink-connector-kinesis

WORKDIR /app

COPY pom.xml /app/
# for Caching maven local repository
# Prefetch maven dependency for caching
RUN mvn -B -f /app/pom.xml -s /usr/share/maven/ref/settings-docker.xml dependency:go-offline
RUN mvn -B -f /app/pom.xml -s /usr/share/maven/ref/settings-docker.xml dependency:resolve-plugins dependency:resolve

COPY src /app/src/
RUN mvn -B -f /app/pom.xml -s /usr/share/maven/ref/settings-docker.xml clean package

#
# Package stage
#
FROM flink:1.8.3-scala_2.11
COPY --from=build /app/target/flink-aggregate-splited-record-example-1.0-SNAPSHOT.jar /app/app.jar

RUN set -eux; \
    dpkgArch="$(dpkg --print-architecture)"; \
    apt-get update -yqq \
    && apt-get install -yqq --no-install-recommends \
       netcat-openbsd \
       awscli

RUN set -eux \
    && apt-get clean \
    && rm -rf \
        /var/lib/apt/lists/* \
        /tmp/* \
        /var/tmp/* \
        /usr/share/man \
        /usr/share/doc \
        /usr/share/doc-base

ENV TINI_VERSION v0.18.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/tini", "--", "/docker-entrypoint.sh"]

EXPOSE 8080