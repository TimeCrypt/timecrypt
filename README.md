# TimeCrypt

[![Build Status](https://travis-ci.org/TimeCrypt/timecrypt.svg?branch=master)](https://travis-ci.org/TimeCrypt/timecrypt)

Implementation of TimeCrypt, see [the Paper](https://www.usenix.org/system/files/nsdi20-paper-burkhalter.pdf) for more details.
TimeCrypt is an end-to-end encrypted time-series data store where the storage server does not see any data in the clear.
The time-series streams are encrypted on the *client-side*, and only authorized parties can decrypt and verify queries.
Features:

- TimeCrypt provides a standard time-series query interface to perform statistical range queries (e.g., average, count, sum, standard deviation variance, etc.). The server computes these queries on encrypted data and does not see any data in the clear. 

- TimeCrypt integrates cryptographic-access control mechanisms to authorize third-party access based on time-intervals and aggregate resolution. 



## Repository Structure
This repository is split into three parts:
- [**timecrypt-crypto**](timecrypt-crypto): The cryptographic library for TimeCrypt. It contains of the partially homomorphic-encryption-based access control construction (HEAC) for different key lengths as well as the key derivation tree implementations.
- [**timecrypt-server**](timecrypt-server): TimeCrypt server implementation that stores data streams either in Cassandra or Memory. 
- [**timecrypt-client**](timecrypt-client): Implements the TimeCrypt client. The client provides a [Java-API](timecrypt-client/src/main/java/ch/ethz/dsg/timecrypt/TimeCryptClient.java) for interacting with TimeCrypt as well as an [interactive (the cli-client)](timecrypt-client/src/main/java/ch/ethz/dsg/timecrypt/CliClient.java) and [non-interactive CLI implementation (the testbed)](timecrypt-client/src/main/java/ch/ethz/dsg/timecrypt/TestBed.java) that simulates a producer like an IoT device.
- [**timecrypt-examples**](timecrypt-examples): Examples of how to use the TimeCrypt API in Java. [(Example1)](timecrypt-examples/src/main/java/ch/ethz/dsg/timecrypt/BasicTCUsage.java)



## Quickstart with Docker

The easiest way to get a TimeCrypt server running in no time is to start it with Docker using `docker-compose`.

```
docker-compose up --build
```

`docker-compose` builds the project inside a Docker container, creates a Docker network for the server and Cassandra, and starts both containers.

After a successful build, the TimeCrypt server should be running.
To test and connect with the server, one needs to interact with the timecrypt-client.
The implementation provides some examples on how to use the java driver, a CLI-client, and a dummy data producer (testbed).

The following command runs [(Example1)](timecrypt-examples/src/main/java/ch/ethz/dsg/timecrypt/BasicTCUsage.java):

```
docker run --network=timecrypt-network eth/timecrypt example1
```


To see a dummy data producer in action, run the testbed client in Docker with:

```
docker run --network=timecrypt-network eth/timecrypt producer
```

For an interactive CLI client session run:
```
docker run --network=timecrypt-network -it eth/timecrypt client
```
The client will automatically create a new key store for the cryptographic material of TimeCrypt.

## Building TimeCrypt Libraries
To build the project without Docker you will need the following prerequisites on your system:
- Maven
- A JDK >= Java version 11
- cmake + c compiler  (can be skipped by deactivating the `aesni-native` profile of maven (`-P!aesni-native``))

The project jar libraries can be build with maven.
```
mvn package
```
it will resolve all dependencies and build the project. Afterwards the project ccontains the following java libraries
 - **Server:** `timecrypt-server/target/timecrypt-server-jar-with-dependencies.jar`
 - **Client:** `timecrypt-client/target/timecrypt-client-jar-with-dependencies.jar`
 - **Producer / Testbed:** `timecrypt-client/target/timecrypt-testbed-jar-with-dependencies.jar`
 - **Bench Client:** `timecrypt-client/target/timecrypt-bench-client-jar-with-dependencies.jar`

To install the TimeCrypt libraries in the local maven repository run:
```
mvn install
```

If your system does not support AES-NI you can disable it during build with the profile switch (`-P!aesni-native`) e.g.:

```
mvn package -P \!aesni-native

```

### Run the TimeCrypt Server
The implementation provides two shell scripts to run a server after a successful build [Link](timecrypt-server/scripts).
```
run_timecrypt_in_memory_server.sh
```
Runs the TimeCrypt server with in-memory storage (i.e., no persistence). 
```
run_timecrypt_cassandra.sh
```
Runs the TimeCrypt server with Cassandra as a storage-backend. 
Inspect the scripts to configure the server parameters accordingly.


### CLI Client
```
timecrypt-client/scripts/run_cli_client.sh
```
Runs the cli client.

### CLI Client
```
timecrypt-client/scripts/run_bench_client.sh
```
Runs the benchmark client.


### Java Driver 
TimeCrypt provides a Java driver to interact with the TimeCrypt server.
To use the driver, add the following library to your project.
```
<dependency>
    <groupId>ch.ethz.dsg.timecrypt</groupId>
    <artifactId>timecrypt-client</artifactId>
    <version>1.0</version>
</dependency>
```
Example of how to use the library.
```
timecrypt-examples/scripts/run_example1.sh
```
Runs the example.
Create a `TimeCryptClient` object to interact with a TimeCrypt server.
```java 
TimeCryptProfile profile = new LocalTimeCryptProfile(null, "myUser", "..", SERVER_ADDRESS, SERVER_PORT);
TimeCryptKeystore keystore = LocalTimeCryptKeystore.createLocalKeystore(null, DUMMY_PASSWORD.toCharArray());
TimeCryptClient tcClient = new TimeCryptClient(keystore, profile);
```
Create a new time-series stream.
```java
long streamID = tcClient.createStream(
                "HeartRate Stream",
                "This stream stores my heart rate",
                TimeUtil.Precision.ONE_SECOND,
                Collections.singletonList(TimeUtil.Precision.TEN_SECONDS),
                DefaultConfigs.getDefaultMetaDataConfig(),
                DefaultConfigs.getDefaultEncryptionScheme(),
                null,
                new Date(startDate)); 
```
Insert past data, must be inserted in-order:
```java 
InsertHandler handler = tcClient.getHandlerForBackupInsert(streamID, new Date(startDate));
handler.writeDataPointToStream(new DataPoint(new Date(timestamp), 100));
handler.terminate();
```
Insert live data:
```java 
InsertHandler liveHandler = tcClient.getHandlerForLiveInsert(streamID);
liveHandler.writeDataPointToStream(new DataPoint(new Date(), 10));
//... periodically insert 
liveHandler.flush();
```
Query average, variance, standard deviation over the the time range `[startDate, endDate)`.
```java 
Interval result = tcClient.performQuery(streamID, startDate, endDate,
                    Arrays.asList(Query.SupportedOperation.AVG, Query.SupportedOperation.VAR, Query.SupportedOperation.STD),
                    false);
System.out.println(String.format("Result [%s, %s] AVG %f, VAR %f, STD %f", x.getFrom(), x.getTo(), x.getValueAt(0), x.getValueAt(1), x.getValueAt(2)));
```

## Disclaimer
This implementation is a research prototype and should not be used in production.

