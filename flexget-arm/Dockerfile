FROM armhf/alpine
MAINTAINER bren

RUN apk add --update \
  tzdata \
  python \
  py-pip \
  ca-certificates \
  && pip install --no-cache-dir --disable-pip-version-check flexget==2.21.0 transmissionrpc==0.11 \
  && rm -rf /var/cache/apk/*

RUN apk add --update --repository http://dl-cdn.alpinelinux.org/alpine/edge/main tinyxml2 && \
    apk add --update --repository http://dl-cdn.alpinelinux.org/alpine/edge/community mediainfo && \
    apk add --update ffmpeg && \
    rm -rf /var/cache/apk/*

# Copy local files
COPY files/ /

# add default volumes
VOLUME /config /data
WORKDIR /config

# expose port for flexget webui
EXPOSE 3539 3539/tcp

# run init.sh to set uid, gid, permissions and to launch flexget
RUN chmod +x /scripts/init.sh
CMD ["/scripts/init.sh"]
