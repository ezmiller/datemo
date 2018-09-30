Datemo Nginx Proxy
==================

This is a "reverse proxy" that stands in front of the datemo
web server.

This container mainly relies on the nginx docker container:

https://hub.docker.com/_/nginx/

To use this container, you just add an `nginx.conf` file to
the container. Nginx starts up when you run the container.

The host ip, or domain name, of the machine where this is
being run is specified (hard-coded) into the `nginx.conf`
file.

In order for this to work, when running this inside a docker
swarm, the published port needs to be set to `mode=host`. That
means, that the service is added with that setting, e.g.:

```
docker service create \
  --name my-web \
  --publish published=8080,target=80,mode=host \
  --replicas 2 \
  nginx
```

Or in the docker-compose.yml file, using version `3.2`:

```
version: "3.2"

...
...
...

services:
  web:
    image: ezmiller/datemo-nginx-proxy:latest
    ports:
      - mode: host
        target: 80
        published: 80

```
