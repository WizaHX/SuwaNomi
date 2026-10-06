# Upstream runtime includes Java, CEF libraries, Xvfb, tini and the standard startup scripts.
FROM ghcr.io/suwayomi/suwayomi-server:preview@sha256:cd75d94a62e83b6cde003475b062022ada55080269b356d32a9b6806f256f321

ARG SOURCE_URL=https://github.com/WizaHX/SuwaNomi
LABEL org.opencontainers.image.title="SuwaNomi" \
      org.opencontainers.image.description="SuwaNomi server and WebUI" \
      org.opencontainers.image.source=$SOURCE_URL

COPY --chown=1000:1000 .docker-build/server.jar /home/suwayomi/startup/tachidesk_latest.jar
COPY --chown=1000:1000 .docker-build/webui/ /opt/suwanomi/webui/
COPY --chmod=755 docker/start.sh /opt/suwanomi/start.sh

# Retain upstream's non-root user, tini entrypoint and persistent-data location.
CMD ["/opt/suwanomi/start.sh"]
