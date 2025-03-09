#!/bin/bash

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR] $1${NC}" >&2
}

warn() {
    echo -e "${YELLOW}[WARNING] $1${NC}"
}

# Function to install Docker on various Linux distributions
install_docker() {
    log "Installing Docker..."
    
    if command -v apt-get &> /dev/null; then
        # Debian/Ubuntu
        log "Detected Debian/Ubuntu system"
        sudo apt-get update
        sudo apt-get install -y ca-certificates curl gnupg
        sudo install -m 0755 -d /etc/apt/keyrings
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
        sudo chmod a+r /etc/apt/keyrings/docker.gpg
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list
        sudo apt-get update
        sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    else
        error "Unsupported distribution. Please install Docker manually."
        exit 1
    fi

    # Start and enable Docker service
    sudo systemctl start docker
    sudo systemctl enable docker

    # Add current user to docker group
    sudo usermod -aG docker $USER
    log "Docker installed successfully! Please log out and back in for group changes to take effect."
}

# Function to setup environment variables from GitHub Actions workflow
setup_env() {
    log "Setting up environment variables..."
    
    # Extract values from GitHub Actions workflow file
    if [[ -f .github/workflows/gradle.yml ]]; then
        log "Reading configuration from GitHub Actions workflow..."
        
        # Extract MongoDB URI
        MONGODB_ATLAS_URI=$(grep 'MONGODB_ATLAS_URI:' .github/workflows/gradle.yml | cut -d '"' -f 2)
        MONGODB_DATABASE=$(grep 'MONGODB_DATABASE:' .github/workflows/gradle.yml | cut -d ' ' -f 4)
        
        # Extract RabbitMQ credentials
        RABBITMQ_USER=$(grep 'RABBITMQ_USER:' .github/workflows/gradle.yml | cut -d '"' -f 2)
        RABBITMQ_PASS=$(grep 'RABBITMQ_PASS:' .github/workflows/gradle.yml | cut -d '"' -f 2)
        
        # Verify docker-compose.yml exists
        if [[ ! -f docker-compose.yml ]]; then
            error "docker-compose.yml not found"
            exit 1
        fi
        
        # Create .env file
        cat > .env << EOL
# Generated from GitHub Actions workflow
MONGODB_ATLAS_URI=${MONGODB_ATLAS_URI}
MONGODB_DATABASE=${MONGODB_DATABASE}
RABBITMQ_USER=${RABBITMQ_USER}
RABBITMQ_PASS=${RABBITMQ_PASS}
EOL
        
        log "Environment variables configured from GitHub Actions workflow"
    else
        error "GitHub Actions workflow file not found at .github/workflows/gradle.yml"
        exit 1
    fi
}

# Function to setup RabbitMQ cluster
setup_rabbitmq_cluster() {
    log "Setting up RabbitMQ cluster..."
    
    # Wait for RabbitMQ nodes to start
    sleep 30
    
    # Configure cluster
    docker exec rabbitmq-2 rabbitmqctl stop_app
    docker exec rabbitmq-2 rabbitmqctl reset
    docker exec rabbitmq-2 rabbitmqctl join_cluster rabbit@rabbitmq-1
    docker exec rabbitmq-2 rabbitmqctl start_app
    
    docker exec rabbitmq-3 rabbitmqctl stop_app
    docker exec rabbitmq-3 rabbitmqctl reset
    docker exec rabbitmq-3 rabbitmqctl join_cluster rabbit@rabbitmq-1
    docker exec rabbitmq-3 rabbitmqctl start_app
    
    # Verify cluster status
    docker exec rabbitmq-1 rabbitmqctl cluster_status
}

# Function to check services health
check_services_health() {
    local max_attempts=30
    local attempt=1
    
    log "Checking services health..."
    while [ $attempt -le $max_attempts ]; do
        if docker-compose ps | grep -q "unhealthy\|exit"; then
            error "Some services are unhealthy or have exited"
            docker-compose logs
            return 1
        elif docker-compose ps | grep -q "healthy"; then
            log "All services are healthy"
            return 0
        fi
        
        log "Waiting for services to become healthy (attempt $attempt/$max_attempts)..."
        sleep 5
        ((attempt++))
    done
    
    error "Timeout waiting for services to become healthy"
    return 1
}

# Main execution
main() {
    log "Starting setup..."

    # Check for Docker
    if ! command -v docker &> /dev/null; then
        install_docker
    fi

    # Handle command line arguments
    case "$1" in
        "stop")
            log "Stopping services..."
            docker-compose down
            log "Services stopped!"
            ;;
        "clean")
            log "Stopping services and cleaning up volumes..."
            docker-compose down -v
            rm -f .env
            log "Clean up complete!"
            ;;
        *)
            # Setup environment from GitHub Actions workflow
            setup_env
            
            log "Starting services..."
            docker-compose up -d
            
            # Setup RabbitMQ cluster
            setup_rabbitmq_cluster
            
            # Check services health
            check_services_health
            
            if [ $? -eq 0 ]; then
                log "Setup completed successfully!"
            else
                error "Setup completed with errors"
                exit 1
            fi
            ;;
    esac
}

# Execute main function
main "$@"
