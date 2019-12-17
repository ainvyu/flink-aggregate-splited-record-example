#!/bin/bash

kinesis_host="kinesis"  
kinesis_port=4567
kinesis_stream_name="test-stream"

echo "Start entrypoint"

# Wait for the kinesis docker to be running
while ! nc -z $kinesis_host $kinesis_port; do  
  echo "Kinesis is unavailable - wait 1s"
  sleep 1
done

echo "Kinesis is available"

while ! AWS_ACCESS_KEY_ID='' AWS_SECRET_ACCESS_KEY='' aws --region us-east-1 --endpoint-url http://$kinesis_host:$kinesis_port kinesis wait stream-exists --stream-name $kinesis_stream_name; do
  echo "Kinesis stream is unavailable - wait 1s"
  sleep 1
  AWS_ACCESS_KEY_ID='' AWS_SECRET_ACCESS_KEY='' aws --region us-east-1 --endpoint-url http://$kinesis_host:$kinesis_port kinesis create-stream --stream-name $kinesis_stream_name --shard-count 10
done

echo "Kinesis stream is available"

bin/flink run --jobmanager jobmanager:8081 /app/app.jar "$@"