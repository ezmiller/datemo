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
# datomic SOCKS script runs. I think this is okay.
mkdir /root/.ssh
cat > /root/.ssh/config << EOF
Host *
StrictHostKeyChecking no
EOF

echo "Starting Datomic SOCKS proxy..."
./datomic-socks-proxy -p default --port $PORT $CLOUD_STACK_NAME
