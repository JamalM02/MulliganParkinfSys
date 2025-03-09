#!/bin/bash


# Start RabbitMQ server
docker-entrypoint.sh rabbitmq-server &

# Wait for RabbitMQ to start
sleep 15

# Join cluster
rabbitmqctl stop_app
rabbitmqctl join_cluster --ram rabbit@rabbitmq1 || rabbitmqctl join_cluster --ram rabbit@rabbitmq3
rabbitmqctl start_app

# Keep the container running
wait
