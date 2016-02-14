#!/usr/bin/env bash

docker run --name eventStoreTestPostgres \
    -p 5432:5432 \
    -e POSTGRES_DB=eventStoreTestPostgres \
    -e POSTGRES_USER=eventStoreTestUser \
    -e POSTGRES_PASSWORD=eventStoreTestPassword \
    -d postgres


docker run --name eventStoreTestMysql \
    -p 3306:3306 \
    -e MYSQL_USER=root \
    -e MYSQL_ALLOW_EMPTY_PASSWORD=true \
    -e MYSQL_DATABASE=eventStoreTestMySql \
    -d mysql