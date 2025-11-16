#!/bin/bash

if [ $# -eq 0 ]; then
    echo "Usage: $0 <record_count>"
    echo "Example: $0 100000"
    exit 1
fi

RECORD_COUNT=$1

echo "Generating $RECORD_COUNT records..."

mvn compile exec:java -Dexec.mainClass="com.mayank.batch.SpringBatchBenchmarkApplication" \
    -Dexec.args="generate-data $RECORD_COUNT"

echo "Data generation completed. File: data/$RECORD_COUNT.csv"
