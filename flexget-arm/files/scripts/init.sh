#!/bin/sh
set -e

# Timezone setting
if [ -n "${TZ}" ]; then
  echo "[Init] Local timezone to ${TZ}"
  echo "${TZ}" > /etc/timezone
  cp /usr/share/zoneinfo/"${TZ}" /etc/localtime
fi

# PUID and PGUID
cd /config || exit

echo "[Init] Setting permissions on files/folders inside container"

if [ -n "${PUID}" ] && [ -n "${PGID}" ]; then
  if [ -z "$(getent group "${PGID}")" ]; then
    addgroup -g "${PGID}" flexget
  fi
  
  flex_group=$(getent group "${PGID}" | cut -d: -f1)

  if [ -z "$(getent passwd "${PUID}")" ]; then
    adduser -D -H -u "${PUID}" flexget "${flex_group}"
  fi

  flex_user=$(getent passwd "${PUID}" | cut -d: -f1)  

  chown -R "${flex_user}":"${flex_group}" /config
  chmod -R 775 /config
fi

# Remove lockfile if exists
if [ -f /config/.config-lock ]; then
  echo "[Init] Removing lockfile"
  rm -f /config/.config-lock
fi

# Check if config.yml exists. If not, copy in
if [ -f /config/config.yml ]; then
  echo "[Init] Using existing config.yml"
else
  echo "[Init] New config.yml from template"
  cp /scripts/config.example.yml /config/config.yml
  if [ -n "$flex_user" ]; then
    chown "${flex_user}":"${flex_group}" /config/config.yml
  fi
fi

# Set FG_WEBUI_PASSWD
if [[ -z "${FG_WEBUI_PASSWD}" ]]; then
  echo "[Init] Using default FG_WEBUI_PASSWD: f1exgetp@ss"
  FG_WEBUI_PASSWD="f1exgetp@ss"
else
  echo "[Init] Using userdefined FG_WEBUI_PASSWD:" \
  "${FG_WEBUI_PASSWD}"
fi
flexget web passwd "${FG_WEBUI_PASSWD}"

echo "[Init] Starting flexget daemon"
if [ -n "$flex_user" ]; then
  exec su "${flex_user}" -m -c \
  'flexget -c /config/config.yml --loglevel info daemon start'
else
  exec flexget -c /config/config.yml --loglevel info daemon start
fi

