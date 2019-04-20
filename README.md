# Plex sobre Docker en Raspberry

--
Con este repo podes crear tu propio server que descarga tus series y peliculas automáticamente, y cuando finaliza, las copia al directorio `media/` donde Plex las encuentra y las agrega a tu biblioteca.

También agregué un pequeño server samba por si querés compartir los archivos por red

Todo esto es parte de unos tutoriales que estoy subiendo a [Youtube](https://www.youtube.com/playlist?list=PLqRCtm0kbeHCEoCM8TR3VLQdoyR2W1_wv)

## Cómo correrlo

Simplemente bajate este repo y modificá las rutas de tus archivos en el docker-compose.yaml:


```
version: "2"

services:

  samba:
    image: dperson/samba:rpi
    restart: always
    command: '-u "pi;password" -s "media;/media;yes;no" -s "downloads;/downloads;yes;no"'  <--- estos son los directorios que vamos a compartir con samba y sus credenciales
    stdin_open: true
    tty: true
    ports:
      - 139:130
      - 445:445
    volumes:
      - /usr/share/zoneinfo/America/Argentina/Mendoza:/etc/localtime   <--- modifica esto con tu zona horaria 
      - /home/pi/media:/media              <--- ruta donde van a ir los archivos renombrados por filebot
      - /home/pi/downloads:/downloads      <--- ruta donde se descargan los archivos del torrent antes de renombrarse

  rtorrent:
    image: pablokbs/rutorrent-armhf
    ports:
      - 80:80
      - 51413:51413
      - 6881:6881/udp
    volumes:
      - /home/pi/torrents-config:/config  <--- asegurarse que esta sea la ruta de tu directorio descargado
      - /home/pi/media:/home/pi/media     <--- ruta donde van a ir los archivos renombrados por filebot
      - /home/pi/downloads:/downloads     <--- ruta donde se descargan los archivos del torrent antes de renombrarse
      - /var/run/docker.sock:/var/run/docker.sock:ro
    restart: always

  plex:
    image: jaymoulin/plex:1.14.1
    ports:
      - 32400:32400
      - 33400:33400
    volumes:
      - /home/pi/plex:/root/Library/Application Support/Plex Media Server  <--- este directorio es donde se guardan las configuraciones de plex y los archivos temporales
      - /home/pi/media:/media  <--- ruta donde van a ir los archivos renombrados por filebot
    restart: always
    network_mode: "host"
```