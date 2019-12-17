FROM node:8.16.2-slim

LABEL maintainer="Yongjin Jung <ainvyu@gmail.com>"

ENV LEVELDB_STORE_PATH /var/lib/data

RUN mkdir $LEVELDB_STORE_PATH

USER root

RUN set -eux; \
    dpkgArch="$(dpkg --print-architecture)"; \
    apt-get update -yqq \
    && apt-get install -yqq --no-install-recommends \
       python

RUN set -eux \
    && apt-get clean \
    && rm -rf \
        /var/lib/apt/lists/* \
        /tmp/* \
        /var/tmp/* \
        /usr/share/man \
        /usr/share/doc \
        /usr/share/doc-base

RUN npm install -g kinesalite@3.3.0 --unsafe

EXPOSE 4567
#VOLUME $LEVELDB_STORE_PATH

ENV TINI_VERSION v0.18.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini

ENTRYPOINT ["/tini", "--", "/usr/local/lib/node_modules/kinesalite/cli.js", "--path", "/var/lib/data"]
CMD ["--port", "4567", "--createStreamMs", "500", "--deleteStreamMs", "500", "--updateStreamMs", "500", "--shardLimit", "256"]