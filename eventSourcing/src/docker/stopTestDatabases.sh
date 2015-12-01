#!/usr/bin/env bash

docker stop eventStoreTestPostgres
docker kill eventStoreTestPostgres
docker rm eventStoreTestPostgres

docker stop eventStoreTestMysql
docker kill eventStoreTestMysql
docker rm eventStoreTestMysql
