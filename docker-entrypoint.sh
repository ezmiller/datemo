#!/bin/bash

echo "Reading secrets..."
read -d $'\x04' aws_access_key < "/run/secrets/aws_access_key"
read -d $'\x04' aws_secret_access_key < "/run/secrets/aws_secret_access_key"
echo "Done"

database_uri="$DATEMO_DB_URI?aws_access_key_id=$aws_access_key&aws_secret_key=$aws_secret_access_key"

echo "Starting datemo..."
java -DDATABASE.URI=$database_uri -jar /datemo-0.1.2-standalone.jar
