#!/usr/bin/env bash

docker run --name eventStoreTestPostgres \
    -p 5432:5432 \
    -e POSTGRES_DB=event_store \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD= \
    -d postgres


docker run --name eventStoreTestMysql \
    -p 3306:3306 \
    -e MYSQL_USER=root \
    -e MYSQL_ALLOW_EMPTY_PASSWORD=true \
    -e MYSQL_DATABASE=eventStoreTestMySql \
    -d mysql