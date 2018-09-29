FROM alpine:3.7

RUN apk update \
&& apk upgrade \
&& apk add --no-cache bash \
&& apk add --no-cache curl \
&& apk add --no-cache openssh \
&& apk add --no-cache openjdk8-jre

RUN apk add --no-cache python3 \
&& python3 -m ensurepip \
&& pip3 install --upgrade pip setuptools \
&& rm -r /usr/lib/python*/ensurepip && \
if [ ! -e /usr/bin/pip ]; then ln -s pip3 /usr/bin/pip ; fi && \
if [[ ! -e /usr/bin/python ]]; then ln -sf /usr/bin/python3 /usr/bin/python; fi && \
rm -r /root/.cache

# Datomic SOCKS proxy to get to the Datomic DB in the cloud
# Eventually this whole app could be run as a Datomic Ion instead.
RUN wget -O datomic-socks-proxy 'https://docs.datomic.com/cloud/files/datomic-socks-proxy' && \
    chmod +x datomic-socks-proxy

# datomic-socks-proxy requires AWS CLI
RUN pip3 install awscli --upgrade

ADD ./docker-entrypoint.sh /
RUN /bin/bash -c 'chmod u+x /docker-entrypoint.sh'

# Add datemo app jar file
ADD ./target/datemo-0.2-standalone.jar /

ENV PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "/docker-entrypoint.sh"]

