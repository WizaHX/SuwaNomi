# Upstream runtime includes Java, CEF libraries, Xvfb, tini and the standard startup scripts.
FROM ghcr.io/suwayomi/suwayomi-server:preview@sha256:cd75d94a62e83b6cde003475b062022ada55080269b356d32a9b6806f256f321 AS runtime

COPY --chown=1000:1000 .docker-build/server.jar /home/suwayomi/startup/tachidesk_latest.jar

# Copy the merged filesystem so the replaced upstream JAR is not retained in a lower layer.
FROM scratch
COPY --from=runtime / /

# Image configuration is not copied with files; match the pinned runtime on both architectures.
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    JAVA_VERSION=jdk-25.0.4+7 \
    HOME=/home/suwayomi
USER suwayomi
WORKDIR /home/suwayomi
EXPOSE 4567
ENTRYPOINT ["tini", "--"]

ARG SOURCE_URL=https://github.com/WizaHX/SuwaNomi
LABEL org.opencontainers.image.title="SuwaNomi" \
      org.opencontainers.image.description="SuwaNomi server and WebUI" \
      org.opencontainers.image.source=$SOURCE_URL \
      org.opencontainers.image.licenses="MPL-2.0"

COPY --chown=1000:1000 .docker-build/webui/ /opt/suwanomi/webui/
COPY --chmod=755 docker/start.sh /opt/suwanomi/start.sh

CMD ["/opt/suwanomi/start.sh"]
