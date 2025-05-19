# Distributed URL Shortener

This is a distributed URL shortener system implemented in Java. It consists of multiple HTTP servers backed by SQLite databases and a load-balancing proxy that distributes incoming requests. The system includes mechanisms for basic caching, health checks, and load testing.

---

## Components

### 1. `serverSqlite/` - URL Shortener Server

* A lightweight HTTP server that:

  * Receives `POST` requests to shorten URLs
  * Receives `GET` requests for redirection
  * Uses SQLite as its persistent store
* Key Files:

  * `URLShortner.java`: Handles HTTP logic
  * `URLShortnerDB.java`: Manages SQLite operations
  * `sqlite-jdbc-3.39.3.0.jar`: SQLite JDBC connector
  * `schema.sql`: SQL schema setup
  * `runit.bash`: Script to run the server
  * `reset.bash`: Resets the database

### 2. `proxyServer/` - Load Balancing Proxy Server

* Sits in front of all `serverSqlite` instances and:

  * Distributes incoming traffic based on round-robin or availability
  * Maintains a basic cache
  * Periodically checks the health of backends
* Key Files:

  * `LoadBalancingProxyServer.java`: Main logic for proxying and caching
  * `start_servers.sh`: Starts all backend servers
  * `host_servers.txt`: List of backend server addresses
  * `thread_pool_size.txt`: Configures thread pool size for concurrency

### 3. `LoadTest/` - Load Testing Suite

* Simulates concurrent users to evaluate server performance
* Key Files:

  * `LoadTest.java`: Client to send requests
  * `LoadTest1.bash`, `LoadTest2.bash`, `LoadTest3.bash`: Parallel test scripts

---

## How to Run the System

### Prerequisites

* Java 8 or higher
* Bash shell
* `sqlite3` installed

### Step-by-step

#### 1. Compile Java Code

```bash
# Compile server
cd serverSqlite
javac -cp .:sqlite-jdbc-3.39.3.0.jar URLShortner.java URLShortnerDB.java

# Compile proxy
cd ../proxyServer
javac LoadBalancingProxyServer.java

# Compile load test
cd ../LoadTest
javac LoadTest.java
```

#### 2. Start Backend Servers

```bash
cd ../proxyServer
./start_servers.sh
```

This will start the SQLite-based URL shortener servers as described in `host_servers.txt`.

#### 3. Run the Proxy Server

```bash
java LoadBalancingProxyServer
```

#### 4. Run Load Tests (optional)

```bash
cd ../LoadTest
./LoadTest1.bash &
./LoadTest2.bash &
./LoadTest3.bash &
```

---

## Configuration

* **`host_servers.txt`**: List of backend servers the proxy can forward to. One `host:port` per line.
* **`thread_pool_size.txt`**: Determines how many threads the proxy uses to handle concurrent connections.
* **Database Reset**: Use `reset.bash` inside `serverSqlite/` to clear the databases.

---

## Notes

* System does not use Docker or container orchestration.
* Failover is manually configured based on health checks and host list.
* SQLite makes it easy to replicate state but is not ideal for massive scalability.
