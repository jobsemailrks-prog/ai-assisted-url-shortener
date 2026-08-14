FROM openjdk:11-jre-slim

# Set the working directory
WORKDIR /app

# Copy the Maven build output to the container
COPY target/urlshortener-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]