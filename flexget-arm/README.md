# docker-flexget

Docker image for running [flexget](http://flexget.com/)

Container features are

- Lightweight alpine linux
- Python 3
- Flexget with initial settings (default ```config.yml``` and webui password)
- pre-installed plug-ins (transmissionrpc, python-telegram-bot)

Note that a default password for webui is set to ```f1exgetp@ss```.
## Usage

```
docker run -d \
    --name=<container name> \
    -p 3539:3539 \
    -v <path for data files>:/data \
    -v <path for config files>:/config \
    -e FG_WEBUI_PASSWD=<desired password> \
    -e PUID=<UID for user> \
    -e PGID=<GID for user> \
    -e TZ=<timezone> \
    wiserain/flexget
```
