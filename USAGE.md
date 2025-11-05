# CertStream Kotlin - Usage Guide

## Quick Start

### Running Locally

1. **Build and run with Maven:**
   ```bash
   mvn exec:java
   ```

2. **Or use the start script:**
   ```bash
   ./start.sh
   ```

3. **Build a fat JAR and run:**
   ```bash
   mvn clean package
   java -jar target/certstream-kotlin-1.0.0-all.jar
   ```

### Running with Docker

1. **Build and run with Docker Compose:**
   ```bash
   docker-compose up --build
   ```

2. **Or build and run manually:**
   ```bash
   docker build -t certstream-kotlin .
   docker run -p 9000:9000 -p 9001:9001 -p 9002:9002 -p 9003:9003 certstream-kotlin
   ```

## API Endpoints

### HTTP Endpoints

#### Get Latest Certificates
```bash
curl http://localhost:9000/latest.json
```
Returns the 25 most recent certificates.

#### Get Example Certificate
```bash
curl http://localhost:9000/example.json
```
Returns a single example certificate.

#### Get Statistics
```bash
curl http://localhost:9000/stats
```
Returns server statistics including:
- Buffer statistics (size, total processed, uptime)
- Client statistics (connected clients per stream type)
- Configuration settings

#### Health Check
```bash
curl http://localhost:9000/health
```
Returns `{"status": "ok"}` if the server is running.

### WebSocket Endpoints

#### Lite Stream (Port 9001)
Certificate data without DER-encoded certificates (smaller payload).

```bash
# Using websocat
websocat ws://localhost:9001/

# Using wscat
wscat -c ws://localhost:9001/
```

#### Full Stream (Port 9002)
Complete certificate data including DER-encoded certificates.

```bash
websocat ws://localhost:9002/
```

#### Domains-Only Stream (Port 9003)
Minimal payload with just domain names and metadata.

```bash
websocat ws://localhost:9003/
```

## Testing with the Test Client

A simple test client is included to demonstrate WebSocket connectivity:

```bash
# Connect to lite stream
kotlinc TestClient.kt -include-runtime -d TestClient.jar
java -jar TestClient.jar lite

# Connect to full stream
java -jar TestClient.jar full

# Connect to domains-only stream
java -jar TestClient.jar domains
```

## Configuration

Edit `src/main/kotlin/com/certstream/Config.kt` to customize:

```kotlin
object Config {
    // Server settings
    const val HOST = "0.0.0.0"
    const val PORT = 9000
    
    // CT Log settings
    const val CT_LOG_LIST_URL = "https://www.gstatic.com/ct/log_list/v3/log_list.json"
    const val POLL_INTERVAL_MS = 10_000L  // 10 seconds
    const val BATCH_SIZE = 256
    
    // Buffer settings
    const val CERTIFICATE_BUFFER_SIZE = 25
    
    // Client settings
    const val CLIENT_PING_TIMEOUT_MS = 60_000L  // 60 seconds
    const val MAX_CLIENTS_PER_ENDPOINT = 1000
    const val CLIENT_QUEUE_SIZE = 100
    
    // Performance settings
    const val MAX_WORKERS = 50  // Max concurrent CT log watchers
}
```

## Certificate Data Format

### Full Stream Format
```json
{
  "message_type": "certificate_update",
  "data": {
    "update_type": "X509LogEntry",
    "leaf_cert": {
      "subject": {
        "CN": "example.com",
        "O": "Example Organization"
      },
      "extensions": {
        "subjectAltName": "DNS:example.com,DNS:www.example.com"
      },
      "not_before": 1699564800.0,
      "not_after": 1731187200.0,
      "serial_number": "123456789",
      "fingerprint": "abc123...",
      "as_der": "base64_encoded_certificate",
      "all_domains": ["example.com", "www.example.com"]
    },
    "chain": [
      {
        "subject": {"CN": "Intermediate CA"},
        "as_der": "base64_encoded_certificate"
      }
    ],
    "cert_index": 12345,
    "seen": 1699564800.0,
    "source": {
      "url": "https://ct.googleapis.com/logs/argon2024/",
      "name": "Google 'Argon2024' log"
    }
  }
}
```

### Lite Stream Format
Same as full stream but without `as_der` fields (certificate DER encoding removed).

### Domains-Only Format
```json
{
  "message_type": "certificate_update",
  "data": {
    "domains": ["example.com", "www.example.com"],
    "seen": 1699564800.0,
    "source": {
      "url": "https://ct.googleapis.com/logs/argon2024/",
      "name": "Google 'Argon2024' log"
    }
  }
}
```

## Performance Tuning

### JVM Options
When running the JAR directly, you can set JVM options:

```bash
java -Xmx2g -Xms512m -jar build/libs/certstream-kotlin-1.0.0-all.jar
```

### Docker Memory Limits
In `docker-compose.yml`:

```yaml
environment:
  - JAVA_OPTS=-Xmx2g -Xms512m
```

### Configuration Tuning

- **POLL_INTERVAL_MS**: Lower values = more frequent polling = higher load
- **BATCH_SIZE**: Larger batches = fewer requests but more memory
- **MAX_WORKERS**: More workers = more CT logs monitored = higher throughput
- **CERTIFICATE_BUFFER_SIZE**: Larger buffer = more certificates in `/latest.json`

## Monitoring

### Logs
Logs are written to:
- Console (stdout)
- `certstream.log` file

### Statistics Endpoint
Monitor server health via `/stats`:

```bash
watch -n 5 'curl -s http://localhost:9000/stats | jq'
```

## Troubleshooting

### Port Already in Use
If ports 9000-9003 are already in use, edit `Config.kt` to change the port numbers.

### Out of Memory
Increase JVM heap size:
```bash
java -Xmx4g -jar build/libs/certstream-kotlin-1.0.0-all.jar
```

### Too Many Open Files
Increase file descriptor limit:
```bash
ulimit -n 65536
```

### Slow Certificate Processing
- Reduce `MAX_WORKERS` to limit concurrent CT log watchers
- Increase `POLL_INTERVAL_MS` to reduce polling frequency
- Check network connectivity to CT log servers

## Development

### Building
```bash
mvn clean compile
```

### Running Tests
```bash
mvn test
```

### Creating Fat JAR
```bash
mvn clean package
```

### Code Style
The project follows Kotlin coding conventions.
