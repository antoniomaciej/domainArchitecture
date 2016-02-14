#!/usr/bin/env bash

docker run --name eventStoreTestPostgres \
    -p 5432:5432 \
    -e POSTGRES_DB=eventStoreTestPostgres \
    -e POSTGRES_USER=eventStorePostgres \
    -e POSTGRES_PASSWORD=mysecretpasswordPostgres \
    -d postgres


docker run --name eventStoreTestMysql \
    -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD=mysecretpasswordMySql \
    -e MYSQL_USER=eventStoreMySql \
    -e MYSQL_PASSWORD=mysecretpasswordMySql \
    -e MYSQL_DATABASE=eventStoreTestMySql \
    -d mysql