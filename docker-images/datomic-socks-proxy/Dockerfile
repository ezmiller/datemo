FROM python:3-slim

# The datomic socks proxy depends on the aws cli
RUN pip install awscli

RUN apt-get update
RUN apt-get install -y openssh-server

ADD ./datomic-socks-proxy /
RUN /bin/bash -c 'chmod u+x /datomic-socks-proxy'

Add ./docker-entrypoint.sh /
RUN /bin/bash -c 'chmod u+x /docker-entrypoint.sh'

ENTRYPOINT /docker-entrypoint.sh
