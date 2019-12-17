ARG BASE_CONTAINER=jupyter/minimal-notebook:latest
FROM $BASE_CONTAINER

LABEL maintainer="Yongjin Jung <ainvyu@gmail.com>"

USER root

COPY requirements.txt requirements.txt

RUN set -eux; \
    dpkgArch="$(dpkg --print-architecture)"; \
    apt-get update -yqq \
    && apt-get install -yqq --no-install-recommends \
       curl jq dnsutils

RUN set -eux \
    && apt-get clean \
    && rm -rf \
        /var/lib/apt/lists/* \
        /tmp/* \
        /var/tmp/* \
        /usr/share/man \
        /usr/share/doc \
        /usr/share/doc-base

# Switch back to jovyan to avoid accidental container runs as root
USER $NB_UID
RUN pip install -r requirements.txt

VOLUME /home/$NB_USER/work

COPY kinesis_producer_sample.py /home/$NB_USER/work/kinesis_producer_sample.py
