# CertStream Kotlin

A Kotlin implementation of a Certificate Transparency log aggregator and streaming service. This server monitors multiple CT logs and provides real-time certificate updates via WebSocket streams and HTTP endpoints.

## Features

- **Multiple Stream Types**:
  - **Full Stream**: Complete certificate data including DER-encoded certificates
  - **Lite Stream**: Certificate data without DER encoding (smaller payload)
  - **Domains-Only Stream**: Just domain names and metadata (minimal payload)

- **HTTP Endpoints**:
  - `/latest.json` - Get the 25 most recent certificates
  - `/example.json` - Get an example certificate
  - `/stats` - Server statistics and metrics
  - `/health` - Health check endpoint

- **WebSocket Endpoints**:
  - `ws://host:9001/` - Lite stream
  - `ws://host:9002/` - Full stream
  - `ws://host:9003/` - Domains-only stream

## Requirements

- Java 17 or higher
- Gradle 7.x or higher

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew run
```

Or build and run the JAR:

```bash
gradle fatJar
java -jar build/libs/certstream-kotlin-1.0.0-all.jar
```

## Configuration

Edit `src/main/kotlin/com/certstream/Config.kt` to modify:

- Server host and port
- CT log polling interval
- Buffer sizes
- Client limits
- Performance settings

## Architecture

The application consists of several key components:

1. **CertificateBuffer**: Ring buffer for storing recent certificates
2. **CertificateParser**: X.509 certificate parsing using Bouncy Castle
3. **CTLogWatcher**: Monitors individual CT logs for new entries
4. **CTLogManager**: Manages multiple CT log watchers
5. **ClientManager**: Handles WebSocket client connections and broadcasting
6. **CertStreamServer**: HTTP and WebSocket server using Ktor
7. **Main**: Application orchestrator and entry point

## API Examples

### HTTP Endpoints

Get latest certificates:
```bash
curl http://localhost:9000/latest.json
```

Get an example certificate:
```bash
curl http://localhost:9000/example.json
```

Get server statistics:
```bash
curl http://localhost:9000/stats
```

### WebSocket Connection

Connect to the lite stream:
```kotlin
val client = HttpClient(CIO) {
    install(WebSockets)
}

client.webSocket("ws://localhost:9001/") {
    for (frame in incoming) {
        if (frame is Frame.Text) {
            val cert = Json.decodeFromString<CertificateUpdate>(frame.readText())
            println("Received certificate for: ${cert.data.leaf_cert.all_domains}")
        }
    }
}
```

## License

MIT License
