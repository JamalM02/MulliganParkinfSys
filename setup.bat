#!/bin/bash

set -e  # Exit on error

echo "Loading configuration..."
source config.env

# Check for Docker
if ! command -v docker &> /dev/null
then
    echo "Docker is not installed. Please install Docker first."
    exit 1
fi

# Check for Docker Compose
if ! command -v docker-compose &> /dev/null
then
    echo "Docker Compose is not installed. Please install it first."
    exit 1
fi

echo "Starting services..."
docker-compose up -d

echo "Services are running!"

     The  setup.sh  script is responsible for starting the services. It first loads the configuration from the  config.env  file, then checks if Docker and Docker Compose are installed. If they are, it starts the services using  docker-compose up -d .