#!/bin/bash

# Path to the file containing the server names/IP addresses
servers_file="host_servers.txt"

# Path to the serverSqlite directory on the remote servers
script_dir=

# Read server IP addresses from the file into an array
mapfile -t servers < "$servers_file"  # Reads lines from the file into an array

# Loop through each server and SSH to run the runit.bash script
for server in "${servers[@]}"; do
    echo "Connecting to $server..."
    ssh $server "cd $script_dir && bash runit.bash" &
done

echo "Started runit.bash on all servers."
