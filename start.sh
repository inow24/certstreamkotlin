#!/bin/bash

# Build and run the CertStream Kotlin server

echo "Building CertStream Kotlin..."
mvn clean compile

if [ $? -eq 0 ]; then
    echo "Build successful! Starting server..."
    mvn exec:java
else
    echo "Build failed!"
    exit 1
fi
