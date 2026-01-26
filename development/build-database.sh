#!/bin/bash

cd ./development/postgres18-wal2json
docker build --no-cache -t ghcr.io/leonfelipecordero/postgres-wal2json:18 .