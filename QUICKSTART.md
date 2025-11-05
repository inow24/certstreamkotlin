# CertStream Kotlin - Quick Start Guide

## What is This?

This is a complete Kotlin implementation of a Certificate Transparency (CT) log aggregator and streaming service, matching the Python implementation in `/Users/mo/work/custom_certstream`.

## Features

✅ **Real-time Certificate Monitoring** - Monitors multiple CT logs simultaneously  
✅ **Three Stream Types** - Full, Lite, and Domains-only WebSocket streams  
✅ **HTTP API** - REST endpoints for latest certificates, examples, and statistics  
✅ **High Performance** - Kotlin coroutines for efficient async operations  
✅ **Production Ready** - Docker support, logging, health checks  

## Quick Start (3 Steps)

### 1. Build the Project

```bash
mvn clean compile
```

### 2. Run the Server

```bash
mvn exec:java
```

### 3. Test the Endpoints

In another terminal:

```bash
# Check health
curl http://localhost:9000/health

# Get statistics
curl http://localhost:9000/stats

# Get latest certificates
curl http://localhost:9000/latest.json
```

## What You'll See

When the server starts, you'll see:

```
Starting CertStream server...
Configuration:
  - Host: 0.0.0.0
  - Port: 9000
  - Poll interval: 10s
  - Batch size: 256
  - Buffer size: 25
  - Max workers: 50

HTTP server started on http://0.0.0.0:9000
WebSocket lite stream started on ws://0.0.0.0:9001/
WebSocket full stream started on ws://0.0.0.0:9002/
WebSocket domains-only stream started on ws://0.0.0.0:9003/

Starting CT log watchers...
Found 50 CT logs
CertStream server is running!
```

## Available Endpoints

### HTTP (Port 9000)
- `GET /health` - Health check
- `GET /stats` - Server statistics
- `GET /latest.json` - 25 most recent certificates
- `GET /example.json` - Example certificate

### WebSocket
- `ws://localhost:9001/` - Lite stream (no DER data)
- `ws://localhost:9002/` - Full stream (complete data)
- `ws://localhost:9003/` - Domains-only stream (minimal data)

## Testing WebSocket Streams

### Using websocat (recommended)

Install websocat:
```bash
brew install websocat  # macOS
```

Connect to a stream:
```bash
websocat ws://localhost:9001/
```

### Using the Test Client

```bash
kotlinc TestClient.kt -include-runtime -d TestClient.jar
java -jar TestClient.jar lite
```

## Docker Deployment

```bash
# Build and run with Docker Compose
docker-compose up --build

# Or manually
docker build -t certstream-kotlin .
docker run -p 9000:9000 -p 9001:9001 -p 9002:9002 -p 9003:9003 certstream-kotlin
```

## Project Structure

```
certstream-kotlin/
├── src/main/kotlin/com/certstream/
│   ├── Main.kt                    # Application entry point
│   ├── Config.kt                  # Configuration settings
│   ├── CertificateBuffer.kt       # Ring buffer for certificates
│   ├── CertificateParser.kt       # X.509 certificate parsing
│   ├── CTLogWatcher.kt            # CT log monitoring
│   ├── ClientManager.kt           # WebSocket client management
│   ├── Server.kt                  # HTTP/WebSocket server
│   └── models/Models.kt           # Data models
├── src/main/resources/
│   └── logback.xml                # Logging configuration
├── build.gradle.kts               # Build configuration
├── Dockerfile                     # Docker image
├── docker-compose.yml             # Docker Compose config
├── README.md                      # Project overview
├── USAGE.md                       # Detailed usage guide
├── IMPLEMENTATION.md              # Implementation details
└── QUICKSTART.md                  # This file
```

## Comparison with Python Version

| Feature | Python | Kotlin |
|---------|--------|--------|
| Async Framework | asyncio | Coroutines |
| HTTP Client | aiohttp | Ktor |
| WebSocket | websockets | Ktor WebSockets |
| Certificate Parsing | cryptography | Java Crypto API |
| Serialization | json | kotlinx.serialization |
| Type Safety | Dynamic | Static |
| Performance | Good | Excellent (after JVM warmup) |

## Common Commands

```bash
# Build the project
mvn clean compile

# Run the server
mvn exec:java

# Create fat JAR
mvn clean package

# Run the fat JAR
java -jar target/certstream-kotlin-1.0.0-all.jar

# Run with Docker
docker-compose up

# View logs
tail -f certstream.log

# Check statistics
watch -n 5 'curl -s http://localhost:9000/stats | jq'
```

## Configuration

Edit `src/main/kotlin/com/certstream/Config.kt`:

```kotlin
object Config {
    const val HOST = "0.0.0.0"              // Server host
    const val PORT = 9000                    // HTTP port
    const val POLL_INTERVAL_MS = 10_000L     // CT log poll interval
    const val BATCH_SIZE = 256               // Certificates per batch
    const val CERTIFICATE_BUFFER_SIZE = 25   // Recent certs buffer
    const val MAX_WORKERS = 50               // Max CT log watchers
}
```

## Troubleshooting

### Build Fails
```bash
# Clean and rebuild
gradle clean build
```

### Port Already in Use
Change the port in `Config.kt` or kill the process:
```bash
lsof -ti:9000 | xargs kill -9
```

### Out of Memory
Increase JVM heap:
```bash
java -Xmx4g -jar build/libs/certstream-kotlin-1.0.0-all.jar
```

## Next Steps

1. **Read USAGE.md** for detailed API documentation
2. **Read IMPLEMENTATION.md** for architecture details
3. **Customize Config.kt** for your needs
4. **Deploy with Docker** for production use

## Support

For issues or questions:
1. Check the logs: `tail -f certstream.log`
2. Review the statistics: `curl http://localhost:9000/stats`
3. Verify CT log connectivity
4. Check system resources (memory, network)

## License

MIT License - Same as the Python implementation
