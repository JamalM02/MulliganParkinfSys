#!/bin/bash


# Start RabbitMQ server
docker-entrypoint.sh rabbitmq-server &

# Wait for RabbitMQ to start
sleep 10

# Enable necessary RabbitMQ plugins
rabbitmq-plugins enable rabbitmq_management
rabbitmqctl set_policy ha-all ".*" '{"ha-mode":"all"}'


# Keep the container running
wait
