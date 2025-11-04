#!/bin/bash

# Build and run the CertStream Kotlin server

echo "Building CertStream Kotlin..."
gradle build

if [ $? -eq 0 ]; then
    echo "Build successful! Starting server..."
    gradle run
else
    echo "Build failed!"
    exit 1
fi
