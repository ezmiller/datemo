#!/bin/bash

echo "Reading secrets..."
read -d $'\x04' AWS_ACCESS_KEY < "/run/secrets/aws_access_key"
read -d $'\x04' AWS_SECRET_ACCESS_KEY < "/run/secrets/aws_secret_access_key"
echo "Done"

mkdir ~/.aws
cat > ~/.aws/credentials << EOF
[default]
aws_access_key_id = $AWS_ACCESS_KEY
aws_secret_access_key = $AWS_SECRET_ACCESS_KEY
EOF

cat > ~/.aws/config << EOF
[default]
region = us-east-2
EOF

# Hack to prevent ssh from trying to check host keys when the
# datomic SOCKS script runs. I think this is okay. Note the option
# 'AddressFamily inet' prevents ssh from trying to use ipv6 to connect
# which was prevening binding or some reason.
mkdir /root/.ssh
cat > /root/.ssh/config << EOF
Host *
  StrictHostKeyChecking no
  UserKnownHostsFile=/dev/null
  AddressFamily inet
EOF

echo "Starting Datomic SOCKS proxy..."
./datomic-socks-proxy -p default $DATOMIC_CLOUD_STACK_NAME &

echo "Waiting for SOCKS proxy..."

while true
do
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -x socks5h://localhost:8182 http://entry.datemo.us-east-2.datomic.net:8182/)
  if [ $STATUS -eq 200 ]; then
    echo "Datomic SOCKS Proxy ready!"
    break
  else
    echo "Got $STATUS :( Not ready yet..."
    echo $OUTPUT
  fi
  sleep 10
done

echo "Starting datemo..."
java -Ddb-name="production" -jar datemo-0.2-standalone.jar
