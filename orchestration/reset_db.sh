#!/bin/bash

# List of server IP addresses
servers=("dh2010pc29" "dh2010pc30" "dh2010pc31" "dh2020pc04")

# Path to the serverSqlite directory on the remote servers
script_dir="/student/henriq93/Documents/409assign/a1group92/a1/serverSqlite"

# Loop through each server and SSH to run the runit.bash script
for server in "${servers[@]}"; do
    echo "Connecting to $server..."
    ssh $server "cd $script_dir && bash reset.bash" &
done

echo "reset example.db on all servers"
