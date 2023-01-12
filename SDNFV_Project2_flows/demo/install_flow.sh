#!/bin/bash

ls *.json | while read line
do 
sid="$( printf "%016d" "$(echo $line | grep -oP 'flows-s\K\d+')" )"
ordinal="$(echo $line | grep -oP 'flows-s\d+-\K\d+')"

curl 'http://localhost:8181/onos/v1/flows/of%3A'$sid'?appId=sdn' \
  -H 'Accept: application/json' \
  -H 'Accept-Language: en-US,en;q=0.9,zh-TW;q=0.8,zh;q=0.7,zh-CN;q=0.6' \
  -H 'Authorization: Basic b25vczpyb2Nrcw==' \
  -H 'Content-Type: application/json' \
  --data-raw "$(cat $line)" \
  --compressed

sleep 1
done