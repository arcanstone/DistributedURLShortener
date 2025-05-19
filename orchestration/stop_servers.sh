#!/bin/bash



# Path to the file containing the server names/IP addresses
servers_file="/student/henriq93/Documents/409assign/a1group92/a1/proxyServer/host_servers.txt"

# Read server IP addresses from the file into an array
mapfile -t servers < "$servers_file"  # Reads lines from the file into an array

# The port where the server is running
port=8086

# Loop through each server, find the PID of the process running on the given port, and stop it
for server in "${servers[@]}"; do
    echo "Stopping server on $server..."
    ssh $server "
        pid=\$(lsof -ti :$port)  # Get the PID of the process using port 8086
        if [ -n \"\$pid\" ]; then
            echo \"Found process on port $port with PID \$pid, sending SIGINT (Ctrl+C)...\"
            kill -SIGINT \$pid  # Send SIGINT (equivalent to Ctrl+C) to gracefully stop the process
        else
            echo \"No process found on port $port\"
        fi
    " &
done

echo "Stop signal sent to all servers."