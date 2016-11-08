#!/bin/bash
docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
docker-compose build
docker-compose up
