# TimeCrypt

TimeCrypt, a system that provides scalable and real-time analytics over large volumes of encrypted time series data.
In TimeCrypt, data is encrypted end-to-end, and authorized parties can only decrypt and verify queries within their authorized access scope.

See [the Paper](https://www.usenix.org/system/files/nsdi20-paper-burkhalter.pdf) for more details.

## Structure of the repository
This repository is split into three parts:
- [**timecrypt-crypto**](timecrypt-crypto/README.md): The cryptographic library for TimeCrypt. It contains of the partially homomorphic-encryption-based access control construction (HEAC) for different key lengths as well as the key derivation tree implementations.
- [**timecrypt-server**](timecrypt-server/README.md): A prototypical implementation of a TimeCrypt server that stores its data in Cassandra. The server takes care of storing the cunks of raw data as well as the digests of aggregatable meta data.
- [**timecrypt-client**](timecrypt-client/README.md): A example implementation of a client. The client provides a [Java-API](timecrypt-client/src/main/java/ch/ethz/dsg/timecrypt/TimeCryptClient.java) for interacting with TimeCrypt as well as an [interactive (the cli-client)](timecrypt-client/src/main/java/ch/ethz/dsg/timecrypt/CliClient.java) and [non-interactive CLI implementation (the testbed)](timecrypt-client/src/main/java/ch/ethz/dsg/timecrypt/TestBed.java) that simulates a producer like an IoT device.

For more information see the individual README files in the folders.

## Quickstart
The easiest way to get a TimeCrypt server running in no time is to start it with `docker-compose`.

```
docker-compose up --build
```

This will build the project inside a Docker container, create an Docker network for the server and Cassandra and will start both.

Afterwards you have a running TimeCrypt server and just need to connect a client to it.

If you just want to see a Producer in action you can run a testbed client in Docker with:

```
docker run --network=timecrypt-network eth/timecrypt producer
```

For an interactive CLI client session run:
```
docker run --network=timecrypt-network -it eth/timecrypt client
```

The client will automatically create a new key store for the cryptographic material of TimeCrypt. The password for this key store will be taken from the `TIMECRYPT_KEYSTORE_PASSWORD` environment variable. If you want to provide an own password you can do so by

You will afterwards be prompted to create a new profile for storing the confidential data of the streams as well as connection information. For this you can safely use the provided default options.

## Build & run on local system
To build the project without Docker you will need the following prerequisites on your system:
- Maven
- A JDK >= Java version 11
- libssl-dev and cmake for building the OpenSSL support (can be skipped by deactivating the `aes-openssl-native` profile of maven (`-P \!aes-openssl-native`))

The root folder of this repository contains a multi-module build to run it start
```
mvn package
```
it will resolve all dependencies and build the project. Afterwards you can find the following JARs
 - **Server:** `timecrypt-server/target/timecrypt-server-jar-with-dependencies.jar`
 - **Client:** `timecrypt-client/target/timecrypt-testbed-jar-with-dependencies.jar`
 - **Producer / Testbed:** `timecrypt-client/target/timecrypt-client-jar-with-dependencies.jar`

### Server
To start the server you need to provide a connection to a Cassandra database (or run the server with an in memory only data storage by providing `TIMECRYPT_IN_MEMORY=true` as environment variable).
You can provide the Host and port of your Cassandra Server by providing the environment variables `TIMECRYPT_CASSANDRA_HOST` and `TIMECRYPT_CASSANDRA_PORT`. The default values for connecting to the database are host: `127.0.0.1` and port: `9042`. For more configuration options see the [README of the server](timecrypt-server/README.md).

### Client
The client provides an interactive way to create streams, add data points and execute queries on the TimeCrypt server.

On startup the client will ask you to create a Keystore and a profile.
The key store is used for secure storing all TimeCrypt related keys.
The profile will be used to store all private metadata about streams (e.g. their start timestamp or the meaning of their aggregatable meta data (digests)).
It also provides login information for the TimeCrypt server.

During the creation of the profile you can select the host and port of the TimeCrypt server.

### Testbed
The Testbed provides a variety of options for interacting with a TimeCrypt server for an overview invoke it with the `-h` option or see the [README of the client](timecrypt-client/README.md).

## AES-NI
This repository offers native encryption support for the Intel AES - New Instructions(AES-NI) which is hardware-based encryption/decryption that may provide enough acceleration to offset the application performance. However: The build might fail if your system does not support it.

AES-NI is supported from the Intel Westmere processors (mid of 2010 / beginning of 2011) onwards.
To check if you have AES-NI on your Linux-based system run:

```
cat /proc/cpuinfo | grep -iq aes && echo 'AES-NI supported' || echo 'no AES-NI support'
```

On non Linux systems you can install the `cpuid` util search for `aes` in its output as it is advised in the [AES-NI documentation](https://software.intel.com/sites/default/files/m/d/4/1/d/8/AES-NI_Java_Linux_Testing_Configuration_Case_Study.pdf).

If your system does not support AES-NI you can disable it during build with the profile switch (`-P \!aesni-native`) e.g.:

```
mvn package -P \!aesni-native

```

## Development

In order to not confuse your IDE you might want to develop in the individual `timecrypt-*` folders.
To satisfy the dependencies to the crypto library run run:
```
mvn install
```
in the root folder before development.
