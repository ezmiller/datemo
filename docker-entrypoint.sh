#!/bin/bash

echo "Running datomic SOCKS proxy..."
./datomic-socks-proxy -p us-east-2 datemo &

echo "Starting datemo..."
java -Ddatabase.db-name="production" -jar datemo-0.2-standalone.jar
