#!/bin/bash
docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
docker build -t kilda/storm:latest services/storm/
docker-compose build
docker-compose up
