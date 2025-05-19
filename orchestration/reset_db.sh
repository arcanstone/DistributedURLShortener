#!/bin/bash

# List of server IP addresses
servers=("" "" "" "")

# Path to the serverSqlite directory on the remote servers
script_dir=

# Loop through each server and SSH to run the runit.bash script
for server in "${servers[@]}"; do
    echo "Connecting to $server..."
    ssh $server "cd $script_dir && bash reset.bash" &
done

echo "reset example.db on all servers"
