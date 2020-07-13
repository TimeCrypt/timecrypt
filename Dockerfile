# @Copyright    ETH Zürich, see AUTHORS file for more
# Licensed under the Apache License, Version 2.0, see LICENSE file for more details.

# Use a multistep build so we don't have to install the build dependencies (JDK) in the container.
FROM openjdk:11-jdk-buster as build

RUN apt-get update \
 && apt-get install -y maven libssl-dev cmake build-essential

COPY . /build

WORKDIR /build

# Skip tests because of possible race conditions in the cassandra build that might mess up the build
RUN cat /proc/cpuinfo | grep -iq aes && mvn package -DskipTests || mvn package -P!aesni-native -DskipTests

################################################################################

FROM openjdk:11-jre-buster

RUN apt-get update \
 && apt-get install -y wait-for-it dos2unix

ENV CLIENT_JAR_NAME "/timecrypt-client-jar-with-dependencies.jar"
ENV TESTBED_JAR_NAME "/timecrypt-testbed-jar-with-dependencies.jar"
ENV SERVER_JAR_NAME "/timecrypt-server-jar-with-dependencies.jar"
ENV EXAMPLE1_JAR_NAME "/timecrypt-example-usage-jar-with-dependencies.jar"

COPY docker-start.sh /docker-start.sh
RUN chmod u+x /docker-start.sh && dos2unix /docker-start.sh && apt-get --purge remove -y dos2unix && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/timecrypt-client/target/$TESTBED_JAR_NAME $TESTBED_JAR_NAME
COPY --from=build /build/timecrypt-client/target/$CLIENT_JAR_NAME $CLIENT_JAR_NAME
COPY --from=build /build/timecrypt-server/target/$SERVER_JAR_NAME $SERVER_JAR_NAME
COPY --from=build /build/timecrypt-examples/target/$EXAMPLE1_JAR_NAME $EXAMPLE1_JAR_NAME

ENTRYPOINT ["/docker-start.sh"]
