# AI URL Shortener

## Overview
The AI URL Shortener project is designed to provide a robust and scalable solution for shortening URLs. This application leverages modern technologies and best practices to ensure high performance and maintainability.

## Features
- URL shortening functionality
- Integration with AI components for enhanced features
- RESTful API for easy access and integration
- Database migration support
- Docker support for containerization

## Project Structure
The project is organized into several key directories:

- **.github/workflows**: Contains CI/CD workflow configurations.
- **docs**: Documentation files outlining traceability, architecture, and use cases.
- **src/main/java/com/schwab/urlshortener**: The main application code, including controllers, services, repositories, models, and DTOs.
- **src/main/resources**: Configuration files and database migration scripts.
- **src/test/java**: Test classes for unit and integration testing.
- **Dockerfile**: Instructions for building the Docker image.
- **docker-compose.yml**: Configuration for running the application in a Docker environment.
- **pom.xml**: Maven configuration file for managing dependencies and build settings.

## Getting Started
To get started with the AI URL Shortener project, follow these steps:

1. **Clone the repository**:
   ```
   git clone https://github.com/yourusername/ai-url-shortener.git
   cd ai-url-shortener
   ```

2. **Build the project**:
   ```
   mvn clean install
   ```

3. **Run the application**:
   ```
   mvn spring-boot:run
   ```

4. **Access the API**: The API will be available at `http://localhost:8080`.

## Contributing
Contributions are welcome! Please read the [CONTRIBUTING.md](docs/CONTRIBUTING.md) for guidelines on how to contribute to this project.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.