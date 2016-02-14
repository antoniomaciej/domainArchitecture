#!/bin/bash

./eventSourcing/src/docker/startTestDatabases.sh

sbt clean coverage test

sbt coverageAggregate

./eventSourcing/src/docker/stopTestDatabases.sh
