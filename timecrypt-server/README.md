# TimeCrypt server

## Tests
There are several end to end test of the server. **Watch out**: Due to the lack of guaranteed consistency in Cassandra it can happen that a test fails due to race conditions. Please re-run the test a few times before reporting a bug.

## Configuration
The TimeCrypt server takes several configuration options via Environment variables:

 - `TIMECRYPT_PORT`: The Port that the server should be listening on.
 - `TIMECRYPT_SERVER_GROUP_THREADS`: Webserver number of threads in netty
 - `TIMECRYPT_WORKER_GROUP_THREADS`: Webserver number of worker threads in netty
 - `TIMECRYPT_EVENT_EXECUTOR_THREADS`: Number of threads that handle the async write to DB
 - `TIMECRYPT_TREE_CACHE`: Size of the Tree metadata cache (Cached from the DB)
 - `TIMECRYPT_BLOCK_CACHE`: Size of the cache in number of blocks that ate stored (Cached from the DB)
 - `TIMECRYPT_K_FACTOR`: Granularity of the index: How many metadata are stored by node of the TimeCrypt tree
 - `TIMECRYPT_IN_MEMORY`: Do not attempt to connect to a Cassandra server - keep the data only in memory.
 - `TIMECRYPT_CASSANDRA_HOST`: The Hostname or IP of your local Cassandra instance (default: localhost)
 - `TIMECRYPT_CASSANDRA_PORT`: The Port of your local Cassandra instance (default: 9042)
 - `TIMECRYPT_CASSANDRA_MIN_CONNECTIONS`: The minimum number of Cassandra connections that the TimeCrypt server will open.
 - `TIMECRYPT_CASSANDRA_MAX_CONNECTIONS`:The maximum number of Cassandra connections that the TimeCrypt server will open.
 - `TIMECRYPT_CASSANDRA_MAX_CONNECTIONS`:The maximum number of Cassandra connections that the TimeCrypt server will open.
 - `TIMECRYPT_SERVER_INTERFACE`: The implementation of the TimeCrypt server. Can be either: `NETTY_SERVER_INTERFACE` or `GRPC_SERVER_INTERFACE`. The `GRPC_SERVER_INTERFACE` is the default.
