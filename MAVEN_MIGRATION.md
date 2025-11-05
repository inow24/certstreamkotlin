# Maven Migration Complete

The project has been successfully migrated from Gradle to Maven.

## What Changed

### New Files
- ✅ **pom.xml** - Maven project configuration
- ✅ **.mvn/** - Maven wrapper directory
- ✅ **mvnw** - Maven wrapper script (Unix/Mac)
- ✅ **mvnw.cmd** - Maven wrapper script (Windows)

### Updated Files
- ✅ **README.md** - Updated with Maven commands
- ✅ **USAGE.md** - Updated with Maven commands
- ✅ **QUICKSTART.md** - Updated with Maven commands
- ✅ **IMPLEMENTATION.md** - Updated with Maven references
- ✅ **Dockerfile** - Updated to use Maven build
- ✅ **start.sh** - Updated to use Maven commands

### Old Files (Can be removed)
- ❌ **build.gradle.kts** - No longer needed
- ❌ **settings.gradle.kts** - No longer needed
- ❌ **gradle.properties** - No longer needed
- ❌ **.gradle/** - No longer needed
- ❌ **gradle/** - No longer needed

## Maven Commands

### Build the Project
```bash
mvn clean compile
```

### Run the Application
```bash
mvn exec:java
```

### Create Fat JAR
```bash
mvn clean package
```

### Run the Fat JAR
```bash
java -jar target/certstream-kotlin-1.0.0-all.jar
```

### Run Tests
```bash
mvn test
```

### Clean Build
```bash
mvn clean
```

## Maven Wrapper

The project now includes Maven wrapper, so you can use `./mvnw` instead of `mvn`:

```bash
./mvnw clean compile
./mvnw exec:java
./mvnw clean package
```

This ensures everyone uses the same Maven version (3.9.11).

## Dependencies

All dependencies from Gradle have been migrated to Maven:

- **Kotlin** 1.9.21
- **Kotlin Coroutines** 1.7.3
- **Kotlinx Serialization** 1.6.2
- **Ktor** 2.3.7 (Server + Client + WebSockets)
- **Logback** 1.4.14
- **Kotlin Logging** 3.0.5
- **Bouncy Castle** 1.77

## Build Configuration

### Kotlin Compilation
- JVM Target: Java 17
- Serialization plugin enabled
- Source directory: `src/main/kotlin`

### Fat JAR
- Maven Shade Plugin creates `certstream-kotlin-1.0.0-all.jar`
- Main class: `com.certstream.MainKt`
- All dependencies included
- Output directory: `target/`

## Docker

The Dockerfile has been updated to use Maven:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
# ... build with Maven
FROM eclipse-temurin:17-jre-alpine
# ... run the JAR
```

## Verification

Build was tested and successful:
```
[INFO] BUILD SUCCESS
[INFO] Total time:  8.164 s
```

## Migration Benefits

1. **Industry Standard** - Maven is widely used in enterprise Java/Kotlin projects
2. **Better IDE Support** - Most IDEs have excellent Maven integration
3. **Dependency Management** - Maven Central is the standard repository
4. **Build Lifecycle** - Clear, standardized build phases
5. **Plugin Ecosystem** - Extensive plugin ecosystem

## Next Steps

1. ✅ Test the application: `mvn exec:java`
2. ✅ Build the fat JAR: `mvn clean package`
3. ✅ Run the JAR: `java -jar target/certstream-kotlin-1.0.0-all.jar`
4. ✅ Test Docker build: `docker-compose up --build`
5. Optional: Remove old Gradle files

## Removing Old Gradle Files

If you want to clean up the old Gradle files:

```bash
rm -rf .gradle build gradle
rm build.gradle.kts settings.gradle.kts gradle.properties
```

## Comparison

| Feature | Gradle | Maven |
|---------|--------|-------|
| Config File | build.gradle.kts | pom.xml |
| Build Command | `gradle build` | `mvn compile` |
| Run Command | `gradle run` | `mvn exec:java` |
| Fat JAR | `gradle fatJar` | `mvn package` |
| Output Dir | `build/` | `target/` |
| Wrapper | `./gradlew` | `./mvnw` |

## Support

The project now fully supports Maven. All documentation has been updated to reflect Maven commands.
