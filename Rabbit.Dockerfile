FROM openjdk:19-jdk-slim

WORKDIR /app

# Install dos2unix for converting scripts to Unix format
RUN apt-get update && apt-get install -y dos2unix && apt-get clean

# Copy the application JAR
COPY  build/libs/5785-ds-ass3-matan-jamal-oran-1.0.jar .

# Copy RabbitMQ initialization scripts
COPY rabbitmq-init /etc/rabbitmq-init

# Convert scripts to Unix format
RUN dos2unix /etc/rabbitmq-init/*.sh

# Make the scripts executable
RUN chmod +x /etc/rabbitmq-init/*.sh

# Use a non-root user for better security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

CMD ["java", "-jar", "5785-ds-ass3-matan-jamal-oran-1.0.jar"]
