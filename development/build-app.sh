#!/bin/bash

./gradlew clean build -x test

docker build -f src/main/docker/Dockerfile.jvm -t ghcr.io/leonfelipecordero/sintropy-engine:latest .