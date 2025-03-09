#!/bin/bash

set -e  # Exit on error

echo "Stopping services..."
docker-compose down

echo "Services stopped!"