# Plex sobre Docker en Raspberry

Con este repo podes crear tu propio server que descarga tus series y peliculas automáticamente, y cuando finaliza, las copia al directorio `media/` donde Plex las encuentra y las agrega a tu biblioteca.

También agregué un pequeño server samba por si querés compartir los archivos por red

Todo esto es parte de unos tutoriales que estoy subiendo a [Youtube](https://www.youtube.com/playlist?list=PLqRCtm0kbeHCEoCM8TR3VLQdoyR2W1_wv)

## Requerimientos iniciales

Agregar tu usuario (cambiar `kbs` con tu nombre de usuario)

```
sudo useradd kbs -G sudo
```

Agregar esto al sudoers para correr sudo sin password

```
%sudo   ALL=(ALL:ALL) NOPASSWD:ALL
```

Agregar esta linea a `sshd_config` para que sólo tu usuario pueda hacer ssh

```
echo "AllowUsers kbs" | sudo tee -a /etc/ssh/sshd_config
sudo systemctl enable ssh && sudo systemctl start ssh
```

Instalar paquetes básicos

```
sudo apt-get update && sudo apt-get install -y \
     apt-transport-https \
     ca-certificates \
     curl \
     gnupg2 \
     software-properties-common \
     vim \
     fail2ban \
     ntfs-3g
```

Instalar Docker

```
curl -fsSL https://download.docker.com/linux/debian/gpg | sudo apt-key add -
sudo apt-key fingerprint 0EBFCD88
echo "deb [arch=armhf] https://download.docker.com/linux/debian \
     $(lsb_release -cs) stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update && sudo apt-get install -y docker-ce docker-compose
```

Modificá tu docker config para que guarde los temps en el disco:

```
sudo vim /etc/default/docker
# Agregar esta linea al final con la ruta de tu disco externo montado
export DOCKER_TMPDIR="/mnt/storage/docker-tmp"
```

Agregar tu usuario al grupo docker 

```
# Add kbs to docker group
sudo usermod -a -G docker kbs
#(logout and login)
docker-compose up -d
```

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
