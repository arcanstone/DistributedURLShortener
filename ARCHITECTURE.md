# Architecture Report: Distributed URL Shortener

This document summarizes the architecture and system design decisions behind the distributed URL shortener system.

---

## Architecture Overview

The system consists of:

* A **Load Balancing Proxy Server** (`LoadBalancingProxyServer.java`)
* Four **Database Servers** (`URLShortner.java` instances)
* **SQLite** as the persistent backend

### Ports

* **Proxy Server**: `8087`
* **Database Servers**: `8086`

---

## Consistency

* Requests for the same short URL return consistent results within \~4 milliseconds.
* Due to multithreading and minimal latency, a `GET` may be handled before a `PUT` request arrives at the storage node, but they are nearly simultaneous.

## Availability

* Proxy server is essential to route and monitor requests.
* Each short URL is stored on **two nodes** for redundancy.

## Partition Tolerance

* No inter-node communication. All communication is from the proxy to database nodes.

---

## Data Partitioning & Replication

* The system uses **modular hashing**: `hash(shortURL) % 4` to select the primary server.
* `PUT` requests also send a copy to a secondary server for fault tolerance.
* Thus, each URL is stored on **two out of four** nodes.

---

## Caching

* Proxy uses a **ConcurrentHashMap** with an **LRU eviction policy**.
* Cache holds up to **10,000 entries**, resets every minute.
* Accelerates `GET` requests and updates on `PUT`.

---

## Disaster Recovery

### Process Recovery

* The proxy **monitors** each database node every 5 seconds.
* If a server is down, the monitor attempts to restart it via SSH.

### Data Recovery

* Data is never stored on a single node.
* A failure of two replicas leads to data loss, otherwise recovery is possible.

---

## Orchestration

* Scripts are provided to:

  * Start all servers: `start_servers.sh`
  * Stop all servers
  * Reset all databases: `reset.bash`

---

## Health Check

* Server status and latency can be checked at:

  * `http://localhost:8087/status`

---

## Scalability

### Horizontal

* Add new server entries to `host_servers.txt` while the proxy is down.

### Vertical

* Tune thread count in `thread_pool_size.txt` to better utilize system resources.

---

## Notes

* All communication occurs via the proxy.
* SQLite is used for its simplicity but may limit scaling in real-world deployment.
* Thread count of 6 is used intentionally, leaving some capacity for database workloads.

---

This document expands on the system design described in the primary `README.md`. For setup instructions and component details, please refer to that file.
