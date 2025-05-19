import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import java.util.Map.Entry;

public class LoadBalancingProxyServer {

    // List of 4 host servers
    static List<String> hostServers =new ArrayList<>();
    /* 
    Arrays.asList(
        "142.1.46.98", 
        "142.1.46.99", 
        "142.1.46.100", 
        "142.1.46.38"   
    );
    */

    static int remotePort = 8086;  // All servers listen on the same port
    static int localPort = 8087;   // Proxy listens on port 8087
    static int THREAD_POOL_SIZE = 6;  // Number of threads in the thread pool
    
    private static ServerSocket serverSocket;
    private static ExecutorService threadPool;

    // Map to hold the health status of each server
    private static final ConcurrentHashMap<String, ServerStatus> serverStatus = new ConcurrentHashMap<>();
    static final int HEALTH_CHECK_INTERVAL = 5000;

     // Cache for storing URL mappings (short URL -> full response from server)
     private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    static final long CACHE_EXPIRATION_MS = 60000;  // Cache expiration time 

    // Maximum number of entries in the cache to prevent overfilling
    static final int MAX_CACHE_SIZE = 10000;  // max 100 cached entries

     // Hash ring for consistent hashing
    private static final SortedMap<Integer, String> hashRing = new TreeMap<>();
    private static final int VIRTUAL_NODE_COUNT = 100; //Virtual nodes per server
        

     

    public static void main(String[] args) throws IOException {
        // Create a fixed thread pool
        loadHostServers("host_servers.txt");
        loadThreadPoolSize("thread_pool_size.txt");
        threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown initiated... waiting for active requests to complete.");
            try {
                // Stop accepting new connections
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                
                // Shutdown the thread pool and wait for existing tasks to finish
                threadPool.shutdown();
                
                // Wait for all active requests to finish
                
                System.out.println("All requests completed. Server shutting down.");
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        startHealthChecks();

        try {
            serverSocket = new ServerSocket(localPort);
            System.out.println("Starting load-balancing proxy on port: " + localPort);

            // Continuously accept incoming client connections
            while (!serverSocket.isClosed()) {
                // Accept the client connection
                Socket clientSocket = serverSocket.accept();

                // Submit the task of handling this client connection to the thread pool
                threadPool.submit(() -> {
                    handleClient(clientSocket);
                });
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Build the consistent hash ring.
     */
    private static void buildConsistentHashRing() {
        for (String server : hostServers) {
            for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
                int hash = hash((server + "VN" + i)); // Create virtual nodes for each server
                hashRing.put(hash, server);
            }
        }
        System.out.println("Built consistent hash ring with virtual nodes.");
    }

    /**
     * Hash function for consistent hashing.
     */
    private static int hash(String key) {
        return Math.abs(key.hashCode()); // Use a simple hashCode function, modify as needed
    }


     /**
     * Get the server for the given short URL using consistent hashing.
     */
    public static String getHostForUrl(String shortUrl) {
        int hash = hash(shortUrl); // Hash the short URL
        SortedMap<Integer, String> tailMap = hashRing.tailMap(hash); // Find first server clockwise
        if (!tailMap.isEmpty()) {
            return tailMap.get(tailMap.firstKey());
        }
        // If no server is found in the tail, wrap around to the first server
        return hashRing.get(hashRing.firstKey());
    }




    /**
     * Hashes the short URL and maps it to a server using modulo operation.
     
    public static String getHostForUrl(String shortUrl) {
        int hash = shortUrl.hashCode();              // Get the hash of the short URL
        int serverIndex = Math.abs(hash % 4);        // Map hash to one of the 4 servers
        return hostServers.get(serverIndex);         // Return the appropriate server host
    }
        */

    public static String getOtherServers(String hostname) {
        //String hostname = getHostForUrl(shortUrl);

        int newindx = (hostServers.indexOf(hostname) +1) % hostServers.size();
        String newhost = hostServers.get(newindx);

        return newhost;

    }

    /**
     * Handles a client connection by forwarding the request to the appropriate host server.
     */
    public static void handleClient(Socket client) {
        try (client) {
            final InputStream streamFromClient = client.getInputStream();
            final OutputStream streamToClient = client.getOutputStream();

            // Read the client's request line (first line of the HTTP request)
            BufferedReader reader = new BufferedReader(new InputStreamReader(streamFromClient));
            String requestLine = reader.readLine();

            // Extract the short URL from the request (for both PUT and GET)
            String shortUrl = extractShortUrl(requestLine);

            // Determine which server to forward the request to using the hash function
            String host = getHostForUrl(shortUrl);

            // Make a connection to the determined host server

            String host2 = getOtherServers(host);
            
            String requestType = getRequestType(requestLine);

            if(requestLine!= null){

                if (requestLine.startsWith("GET /status")) {
                    serveStatusPage(streamToClient);
                    return;
                }
                else if(requestLine.startsWith("GET /add-server")){
                    handleAddServerRequest(client, requestLine);
                    return;
                }

            }
            

            if ("GET".equalsIgnoreCase(requestType)) {
                // Check cache first
                CacheEntry cachedResponse = cache.get(shortUrl);
                if (cachedResponse != null && !isCacheExpired(cachedResponse)) {
                    // Serve from cache
                    System.out.println("Serving from cache for short URL: " + shortUrl);
                    streamToClient.write(cachedResponse.getResponse().getBytes());
                    streamToClient.flush();
                    return;
                }
            }

            if("PUT".equals(requestType)){
                handleRequestToServer(client, requestLine,  streamToClient, host, true, false);
                handleRequestToServer(client, requestLine, streamToClient, host2, false, false);
                cache.remove(shortUrl);
            }
            else if("GET".equals(requestType)){
                handleRequestToServer(client, requestLine, streamToClient, host, true, false);
            }
            streamToClient.close();
            

        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    public static void handleAddServerRequest(Socket client, String requestLine) {
        try (PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
    
            // Assuming the new server's address is sent via a GET request, such as GET /add-server?host=newserver.com
            String newServerHost = extractNewServerHost(requestLine);
    
            if (newServerHost != null && !newServerHost.isEmpty()) {
                // Add the new server to the list of servers
                if (!hostServers.contains(newServerHost)) {
                    hostServers.add(newServerHost);
                    System.out.println("Added new server: " + newServerHost);
    
                    // Rebuild the consistent hash ring to include the new server's virtual nodes
                    addServerToHashRing(newServerHost);
    
                    // Respond to the client
                    out.println("HTTP/1.1 200 OK");
                    out.println("Content-Type: text/plain");
                    out.println();
                    out.println("Server " + newServerHost + " added successfully.");
                } else {
                    out.println("HTTP/1.1 400 Bad Request");
                    out.println("Content-Type: text/plain");
                    out.println();
                    out.println("Server " + newServerHost + " already exists.");
                }
            } else {
                out.println("HTTP/1.1 400 Bad Request");
                out.println("Content-Type: text/plain");
                out.println();
                out.println("Invalid server host.");
            }
    
        } catch (IOException e) {
            System.err.println("Error handling add server request: " + e.getMessage());
        }
    }

    /**
     * Extracts the new server host from the request line.
     * For example: GET /add-server?host=newserver.com
     */
    private static String extractNewServerHost(String requestLine) {
        Pattern pattern = Pattern.compile("/add-server\\?host=([^\\s]+)");
        Matcher matcher = pattern.matcher(requestLine);

        if (matcher.find()) {
            return matcher.group(1);  // Return the host parameter from the query
        }
        return null;  // Return null if no valid host is found
    }

    /**
     * Adds the new server to the consistent hash ring.
     */
    private static void addServerToHashRing(String newServerHost) {
        for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
            int hash = hash(newServerHost + "VN" + i); // Hash for the new virtual node
            hashRing.put(hash, newServerHost); // Add new server's virtual node to the hash ring
        }
        System.out.println("Added new server's virtual nodes to the consistent hash ring.");
    }



    
    /**
     * Handles sending the request to a given server.
     */
    public static void handleRequestToServer(Socket client, String requestLine,  OutputStream streamToClient, String host, boolean toClient, boolean fallback) {
        boolean isGetRequest = requestLine.startsWith("GET");
        String shortUrl = extractShortUrl(requestLine);

        if (isGetRequest && cache.size() >= MAX_CACHE_SIZE) {
            // Evict the oldest cache entry if the limit is exceeded
            evictOldestCacheEntry();
        }
    


        try (Socket server = new Socket(host, remotePort)) {
            System.out.println("Forwarding request for short URL to server: " + host);

            // Get server streams
            final InputStream streamFromServer = server.getInputStream();
            final OutputStream streamToServer = server.getOutputStream();

            // Forward the first line of the request (request line)
            streamToServer.write((requestLine + "\r\n").getBytes());
            streamToServer.flush();

            int bytesRead;
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
            byte[] request = new byte[1024];

            // Read the server's responses and pass them back to the client
            byte[] reply = new byte[4096];
            while ((bytesRead = streamFromServer.read(reply)) != -1) {
                if(toClient){
                    streamToClient.write(reply, 0, bytesRead);
                    streamToClient.flush();
                    responseBuffer.write(reply, 0, bytesRead);
                }
            }

            if (isGetRequest && toClient) {
                // Cache the response
                String responseString = responseBuffer.toString();
                cache.put(shortUrl, new CacheEntry(responseString, System.currentTimeMillis()));
                System.out.println("Cached response for short URL: " + shortUrl);
            }

            // Close the server stream
            //streamToServer.close();
            //streamToClient.close();
            //server.close();

        } catch (IOException e) {
            if(!fallback){
                handleRequestToServer(client, requestLine, streamToClient, getOtherServers(host), toClient, true);
                return;
            }

            System.err.println("Error connecting to server " + host + ": " + e.getMessage());
            PrintWriter out = new PrintWriter(streamToClient);
            out.print("Proxy server cannot connect to " + host + ":" + remotePort + ":\n" + e + "\n");
            out.flush();
        }
    }

    /**
     * Evicts the oldest cache entry to maintain the cache size limit.
     */
    public static void evictOldestCacheEntry() {
        Optional<Entry<String, CacheEntry>> oldestEntry = cache.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().getTimestamp()));

        if (oldestEntry.isPresent()) {
            String oldestKey = oldestEntry.get().getKey();
            cache.remove(oldestKey);
            System.out.println("Evicted oldest cache entry for short URL: " + oldestKey);
        }
    }


    /**
     * Extracts the request type (e.g., GET, PUT) from the HTTP request line.
     */
    public static String getRequestType(String requestLine) {
        if (requestLine != null && !requestLine.isEmpty()) {
            return requestLine.split(" ")[0];  // The first part of the request line is the request type
        }
        return "";
    }


    /**
     * Extracts the short URL from the HTTP request line.
     * For example, given "PUT /?short=shortURL&long=longURL HTTP/1.1",
     * this method will return "shortURL".
     */
    public static String extractShortUrl(String requestLine) {
        // First, try to match the short URL in a query parameter (e.g., /?short=abc)
        Pattern queryParamPattern = Pattern.compile("/\\?short=([^&]+)");
        Matcher queryMatcher = queryParamPattern.matcher(requestLine);
        
        if (queryMatcher.find()) {
            return queryMatcher.group(1);  // Return the short URL from the query parameter
        }
        
        // If no query parameter match, try to match a short URL in the path (e.g., /abc)
        Pattern pathPattern = Pattern.compile("/([^/?]+)");
        Matcher pathMatcher = pathPattern.matcher(requestLine);
        
        if (pathMatcher.find()) {
            return pathMatcher.group(1);  // Return the short URL from the path
        }
        
        return "";  // Default if no short URL is found
    }




    public static void startHealthChecks() {
        ScheduledExecutorService healthCheckScheduler = Executors.newScheduledThreadPool(1);
        healthCheckScheduler.scheduleAtFixedRate(() -> {
            for (String host : hostServers) {
                try (Socket socket = new Socket()) {
                    long startTime = System.nanoTime();
    
                    // Connect to the server
                    socket.connect(new InetSocketAddress(host, remotePort), 2000);
    
                    // Send a simple HTTP GET request
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("GET /abc HTTP/1.1");
                    //out.println("Host: " + host);
                    //out.println("Connection: close");
                    //out.println(); // End of the request
    
                    // Read the server's response
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String inputLine;
                    boolean healthy = false;
    
                    while ((inputLine = in.readLine()) != null) {
                        if (inputLine.contains("HTTP/1.1 404 OK") || inputLine.contains("Temporary Redirect") || inputLine.contains("307") || inputLine.contains("404") || inputLine.contains("201")) {
                            healthy = true;  // Server responded with a 200 status
                            break;
                        }
                    }
    
                    long endTime = System.nanoTime();
                    long responseTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
                    //responseTime=4;
    
                    // Update server health status
                    serverStatus.put(host, new ServerStatus(healthy, healthy ? responseTime : -1));
    
                } catch (IOException e) {
                    // If there is an error, mark the server as down
                    serverStatus.put(host, new ServerStatus(false, -1));
                    System.err.println("Server " + host + " is down, attempting to restart...");
                    attemptServerRestart(host);
                }
            }
        }, 0, HEALTH_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Attempts to restart a downed server by running the "runit.sh" bash script via SSH.
     */
    public static void attemptServerRestart(String host) {
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "start_servers.sh", host);
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {


                System.out.println("Successfully restarted server " + host + " via runit.sh.");
            } else {
                System.err.println("Failed to restart server " + host + ". Exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error attempting to restart server " + host + ": " + e.getMessage());
        }
    }

    /**
     * Starts a periodic task to clean up expired cache entries.
     */
    public static void startCacheCleanupTask() {
        ScheduledExecutorService cacheCleanupScheduler = Executors.newScheduledThreadPool(1);
        cacheCleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                if ((now - entry.getValue().getTimestamp()) > CACHE_EXPIRATION_MS) {
                    cache.remove(entry.getKey());
                    System.out.println("Cache entry expired and removed for short URL: " + entry.getKey());
                }
            }
        }, CACHE_EXPIRATION_MS, CACHE_EXPIRATION_MS, TimeUnit.MILLISECONDS);
    }

    static class ServerStatus {
        private boolean isUp;
        private long responseTime;

        public ServerStatus(boolean isUp, long responseTime) {
            this.isUp = isUp;
            this.responseTime = responseTime;
        }

        public boolean isUp() {
            return isUp;
        }

        public long getResponseTime() {
            return responseTime;
        }
    }

    static class CacheEntry {
        private final String response;
        private final long timestamp;

        public CacheEntry(String response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }

        public String getResponse() {
            return response;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Checks if a cache entry has expired.
     */
    public static boolean isCacheExpired(CacheEntry entry) {
        return (System.currentTimeMillis() - entry.getTimestamp()) > CACHE_EXPIRATION_MS;
    }

    
    

    public static void serveStatusPage(OutputStream streamToClient) throws IOException {
        PrintWriter out = new PrintWriter(streamToClient);
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: text/html");
        out.println();
        out.println("<html><body><h1>Server Status</h1><ul>");
        for (Map.Entry<String, ServerStatus> entry : serverStatus.entrySet()) {
            String status = entry.getValue().isUp() ? "UP" : "DOWN";
            long responseTime = entry.getValue().getResponseTime();
            String responseTimeStr = responseTime >= 0 ? responseTime + " ms" : "N/A";
            out.println("<li>" + entry.getKey() + " : " + status + " (Response Time: " + responseTimeStr + ")</li>");
        }
        out.println("</ul></body></html>");
        out.flush();
    }

    private static void loadHostServers(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                hostServers.add(line.trim()); // Add trimmed line to hostServers list
            }
            System.out.println("Loaded host servers: " + hostServers);
        } catch (IOException e) {
            System.err.println("Error reading host servers from file: " + e.getMessage());
        }
    }

    public static void loadThreadPoolSize(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();  // Assuming the file contains a single line with the integer
            THREAD_POOL_SIZE = Integer.parseInt(line.trim());
            System.out.println("Loaded thread pool size: " + THREAD_POOL_SIZE);
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error reading thread pool size from file: " + e.getMessage());
        }
    }


}
