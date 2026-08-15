# High-Performance URL Shortener Microservice

An enterprise-grade, high-performance URL shortener built with **Spring Boot 3**, **Java 21**, **PostgreSQL**, **Redis**, and **Docker Compose**.

---

## 🏗️ Architecture & Features

* **Base62 Bi-directional Encoding:** Converts database auto-increment IDs into compact short codes.
* **Sub-Millisecond Redis Caching:** Caches URL redirects in Redis memory to bypass database disk I/O on high-traffic links.
* **Asynchronous Analytics Engine:** Logs click metrics (IP address, User-Agent, timestamp) asynchronously (`@Async`) without blocking HTTP redirects.
* **TTL & Expiration Management:** Supports auto-expiring links via explicit expiration timestamps or relative TTLs.
* **Flyway Migrations:** Version-controlled database schema management.
* **Isolated Multi-Stage Docker Build:** Minimal runtime footprint using `eclipse-temurin:21-jre-alpine`.

---

## 🚀 Quick Start (Docker Compose)

### Prerequisites
* [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.

### Execution Command
Run the following command from the project root:

```bash
docker-compose up --build