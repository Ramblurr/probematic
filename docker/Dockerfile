FROM docker.io/library/openjdk:17-slim-bullseye
MAINTAINER Casey Link <casey@outskirtslabs.com>

RUN set -ex; \
    export DEBIAN_FRONTEND=noninteractive; \
    apt-get update; \
    apt-get update; \
    apt-get install -y --no-install-recommends gosu vim rsync bash dnsutils procps wget ca-certificates default-mysql-client openssh-server; \
    /usr/sbin/update-ca-certificates \
    apt-get autoremove -y; \
    rm -rf /var/lib/apt/lists/*


RUN groupadd --gid 1000 probematic \
  && useradd --uid 1000 --gid probematic --shell /bin/bash --create-home --home-dir /app probematic

WORKDIR /app
COPY ./docker/scripts/entrypoint.sh /entrypoint.sh
COPY ./docker/scripts/mysql-population-check.sh /mysql-population-check.sh
RUN chmod +rx /*.sh 

ARG GIT_HASH
COPY ./target/probematic-backend-${GIT_HASH}.jar /app/probematic-backend.jar
RUN chown -R probematic:probematic /app

STOPSIGNAL SIGTERM
ENTRYPOINT ["/entrypoint.sh"]

# these are down at the bottom so changes to them don't cause a full rebuild
ARG DOCKER_IMAGE_TAG
ARG GIT_BRANCH
ARG BUILD_DATE
ENV DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG}
ENV GIT_BRANCH ${GIT_BRANCH}
ENV GIT_HASH ${GIT_HASH}
ENV BUILD_DATE ${BUILD_DATE}