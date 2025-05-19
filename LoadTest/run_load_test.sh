#!/bin/bash

# Function to run LoadTest and capture performance metrics
run_load_test() {
    local host=$1
    local port=$2
    local seed=$3
    local request_type=$4
    local num_requests=$5
    local test_id=$6

    # Record the start time
    start_time=$(date +%s%3N)

    # Run the LoadTest with the given parameters
    java LoadTest "$host" "$port" "$seed" "$request_type" "$num_requests" &

    # Capture the process ID of the test
    pid=$!

    # Wait for the process to finish
    wait $pid

    # Record the end time
    end_time=$(date +%s%3N)

    # Calculate the total time taken in milliseconds
    response_time=$((end_time - start_time))

    # Log performance metrics (append to CSV file)
    echo "$test_id,$request_type,$response_time" >> performance_data.csv
}

# Initialize CSV file with headers
echo "TestID,RequestType,ResponseTime(ms)" > performance_data.csv

# Run Load1 test (PUT requests)
echo "Running Load1 test (PUT requests)..."
run_load_test 127.0.0.1 8087 11 PUT 1000 "Load1-Test1"
run_load_test 127.0.0.1 8087 12 PUT 1000 "Load1-Test2"
run_load_test 127.0.0.1 8087 13 PUT 1000 "Load1-Test3"
run_load_test 127.0.0.1 8087 14 PUT 1000 "Load1-Test4"

# Run Load2 test (GET requests)
echo "Running Load2 test (GET requests)..."
run_load_test 127.0.0.1 8087 11 GET 1000 "Load2-Test1"
run_load_test 127.0.0.1 8087 12 GET 1000 "Load2-Test2"
run_load_test 127.0.0.1 8087 100 GET 1000 "Load2-Test3"
run_load_test 127.0.0.1 8087 101 GET 1000 "Load2-Test4"

echo "Load testing completed. Performance data written to performance_data.csv."
