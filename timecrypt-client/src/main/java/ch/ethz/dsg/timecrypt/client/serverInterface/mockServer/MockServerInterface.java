/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface.mockServer;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * A Mock implementation of a TimeCrypt server interface for testing purposes.
 */
public class MockServerInterface implements ServerInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockServerInterface.class);

    private final Map<Long, MockStream> streams;
    @JsonIgnore
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    @JsonIgnore
    private String path;

    /**
     * Constructor for deserialization.
     *
     * @param streams A Map mapping stream IDs to MockStreams containing data.
     */
    @JsonCreator
    public MockServerInterface(Map<Long, MockStream> streams) {
        this.streams = streams;
    }

    public MockServerInterface() {
        this.path = null;
        streams = new HashMap<>();
    }

    /**
     * Create a new MockServer that optionally persists its content to a YAML file.
     *
     * @param path The path for persisting its content. If it is null the data will only be stored in memory.
     * @throws IOException The creation of the MockServer failed.
     */
    private MockServerInterface(String path) throws IOException {
        this.path = path;
        streams = new HashMap<>();
        synchronized (this) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(path), this);
        }
    }

    /**
     * Factory method for retrieving a MockServer that is optionally deserialized from the given path.
     *
     * @param persistencePath The Path for the persistence of this mock server. If there is already a serialized
     *                        representation of a mock server object this object will be returned.
     * @return A MockServer implementation of the Server interface.
     * @throws IOException Exception that is thrown if something fails while deserializing an existing object.
     */
    public static ServerInterface getMockServerInterface(String persistencePath) throws IOException {
        File ymlFile = new File(persistencePath);

        if (!ymlFile.exists()) {
            return new MockServerInterface(persistencePath);
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
        mapper.registerModule(new Jdk8Module());

        MockServerInterface mockServerInterface = mapper.readValue(ymlFile, MockServerInterface.class);
        mockServerInterface.setPath(ymlFile.getAbsolutePath());
        return mockServerInterface;
    }

    /**
     * Return all streams inside this mock server. This should only be used for serializing and deserializing data
     * since it is not part of the Server Interface.
     *
     * @return A Map mapping stream IDs to MockStreams containing data.
     */
    public Map<Long, MockStream> getStreams() {
        return streams;
    }

    /**
     * Set the path that this Mock server serializes to. This is needed to not store the path of the serialized
     * Mock server in the serialized object itself.
     *
     * @param path The path that this server shall serialize itself to.
     */
    private void setPath(String path) {
        this.path = path;
    }

    @Override
    public long createStream(List<StreamMetaData> metadataConfig) throws CouldNotStoreException {
        long streamId = streams.size();

        // Basic validation - also orders the meta data for future processing
        StreamMetaData[] validationArray = new StreamMetaData[metadataConfig.size()];
        for (StreamMetaData metaData : metadataConfig) {
            if (metaData.getId() >= metadataConfig.size()) {
                throw new CouldNotStoreException("Invalid ID for metadata item " + metaData);
            }
            if (metaData.getId() < 0) {
                throw new CouldNotStoreException("Invalid ID for metadata item " + metaData);
            }
            if (validationArray[metaData.getId()] == null) {
                validationArray[metaData.getId()] = metaData;
            } else {
                throw new CouldNotStoreException("Duplicate ID for metadata item " + metaData + " item with same ID:"
                        + validationArray[metaData.getId()]);
            }
        }

        streams.put(streamId, new MockStream(streamId, Arrays.asList(validationArray)));

        try {
            if (path != null) {
                synchronized (this) {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(path), this);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not write data to YAML file", e);
            throw new CouldNotStoreException("Could not write data to YAML file." + e.getMessage());
        }
        LOGGER.info("Created stream with id " + streamId);
        return streamId;
    }

    @Override
    public long getLastWrittenChunkId(long streamId) throws InvalidQueryException {
        if (streamId < streams.size()) {
            return streams.get(streamId).chunks.size() - 1;
        } else {
            throw new InvalidQueryException("No Stream with the given ID available.");
        }
    }

    @Override
    public long addChunk(long streamId, EncryptedChunk chunk, EncryptedDigest digest) throws CouldNotStoreException {
        LOGGER.debug("Adding chunk: " + chunk.toString() + " and digest " + digest.toString());

        if (streamId < streams.size()) {
            long chunkId = streams.get(streamId).addChunk(chunk, digest);
            try {
                if (path != null) {
                    synchronized (this) {
                        mapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(path), this);
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Could not write data to YAML file", e);
                throw new CouldNotStoreException("Could not write data to YAML file." + e.getMessage());
            }
            return chunkId;
        } else {
            throw new CouldNotStoreException("No Stream with the given ID available.");
        }
    }

    @Override
    public List<EncryptedChunk> getChunks(long streamId, long chunkIdFrom, long chunkIdTo)
            throws CouldNotReceiveException {

        if (streamId < streams.size()) {

            if (chunkIdFrom >= chunkIdTo) {
                throw new CouldNotReceiveException("Invalid query range - FROM (" + chunkIdFrom + ") >= TO (" +
                        chunkIdTo + ")");
            }

            MockStream stream = streams.get(streamId);
            int size = stream.getChunks().size();

            if (chunkIdFrom < 0) {
                throw new CouldNotReceiveException("Invalid query range - FROM (" + chunkIdFrom + ") < 0 ");
            }

            if (chunkIdFrom > size) {
                throw new CouldNotReceiveException("Invalid query range - FROM (" + chunkIdFrom +
                        ") > maximum chunk ID (" + size + ")");
            }

            if (chunkIdTo > size) {
                throw new CouldNotReceiveException("Invalid query range - TO (" + chunkIdTo +
                        ") > maximum chunk ID (" + size + ")");
            }

            return new ArrayList<>(streams.get(streamId).getChunks().subMap(chunkIdFrom, chunkIdTo).values());
        } else {
            throw new CouldNotReceiveException("No Stream with the given ID available.");
        }
    }

    @Override
    public void deleteStream(long streamId) throws InvalidQueryException {
        if (streams.containsKey(streamId)) {
            streams.remove(streamId);
        } else {
            throw new InvalidQueryException("No Stream with ID " + streamId);
        }
    }

    @Override
    public List<EncryptedDigest> getStatisticalData(long streamId, long chunkIdFrom, long chunkIdTo,
                                                    int granularity, List<StreamMetaData> requestMetaData)
            throws InvalidQueryException {

        long chunkIdToInclusive = chunkIdTo - 1;
        if (streamId < streams.size()) {

            MockStream stream = streams.get(streamId);
            int size = stream.getChunks().size();

            if (size < 1) {
                throw new InvalidQueryException("Invalid query - stream has no data yet");
            }

            if (chunkIdFrom >= chunkIdTo) {
                throw new InvalidQueryException("Invalid query range - FROM (" + chunkIdFrom + ") >= TO (" +
                        chunkIdTo + ")");
            }

            if (chunkIdFrom < 0) {
                throw new InvalidQueryException("Invalid query range - FROM (" + chunkIdFrom + ") < 0 ");
            }

            if (chunkIdFrom > size) {
                throw new InvalidQueryException("Invalid query range - FROM (" + chunkIdFrom +
                        ") > maximum chunk ID (" + size + ")");
            }

            // Chunk ID TO is exclusive
            if (chunkIdToInclusive > size) {
                throw new InvalidQueryException("Invalid query range - TO (" + chunkIdTo +
                        ") > maximum chunk ID (" + size + ")");
            }

            if (granularity < 1) {
                throw new InvalidQueryException("Invalid granularity " + granularity);
            }

            if ((chunkIdTo - chunkIdFrom) % (float) granularity != 0) {
                throw new InvalidQueryException("Invalid query granularity - the requested length of interval " +
                        (chunkIdTo - chunkIdFrom) + " (TO = " + chunkIdTo + ", FROM = " + chunkIdFrom +
                        ") can not be divided by requested granularity " + granularity);
            }

            if (requestMetaData.size() < 1) {
                throw new InvalidQueryException("No meta data requested " + granularity);
            }

            List<StreamMetaData> streamMetaData = streams.get(streamId).getMetaData();
            for (StreamMetaData requestedMetaData : requestMetaData) {
                if (requestedMetaData.getId() >= streamMetaData.size() || requestedMetaData.getId() < 0) {
                    throw new InvalidQueryException("Requested meta data item has invalid ID" + requestedMetaData);
                }
                if (requestedMetaData.getEncryptionScheme() !=
                        streamMetaData.get(requestedMetaData.getId()).getEncryptionScheme()) {
                    throw new InvalidQueryException("Requested meta data item has not the same encryption as the " +
                            "corresponding item on the server. Server: " + streamMetaData.get(
                            requestedMetaData.getId()) + " requested: " + requestedMetaData);
                }
            }

            SortedMap<Long, EncryptedDigest> digests = streams.get(streamId).getDigests().subMap(chunkIdFrom, chunkIdTo);
            List<EncryptedDigest> returnList = new ArrayList<>();

            float nrOfSubIntervals = (chunkIdTo - chunkIdFrom) / (float) granularity;
            for (long i = 0; i < nrOfSubIntervals; i++) {
                long from = chunkIdFrom + (i * granularity);
                long to = chunkIdFrom + ((i + 1) * granularity);
                returnList.add(aggregateDigests(requestMetaData, digests.subMap(from, (to)).values(), streamId, from, to));
            }
            return returnList;

        } else {
            throw new InvalidQueryException("No Stream with the given ID available.");
        }
    }

    private EncryptedDigest aggregateDigests(List<StreamMetaData> requestedMetadata,
                                             Collection<EncryptedDigest> values, long streamId, long from, long to) {

        Map<Integer, EncryptedMetadata> payload = new HashMap<>();

        for (EncryptedDigest value : values) {
            for (StreamMetaData requestedMetaData : requestedMetadata) {
                if (payload.containsKey(requestedMetaData.getId())) {
                    payload.put(requestedMetaData.getId(), MetaDataFactory.mergeEncyptedMetadata(payload.get(
                            requestedMetaData.getId()), value.getPayload().get(requestedMetaData.getId())));
                } else {
                    payload.put(requestedMetaData.getId(), value.getPayload().get(requestedMetaData.getId()));
                }
            }
        }

        // what kind of meta data
        return new EncryptedDigest(streamId, from, to, new ArrayList<>(payload.values()));
    }

    /**
     * A mock implementation for a server-side stream representation.
     */
    private static class MockStream {
        private final long streamId;
        private final SortedMap<Long, EncryptedChunk> chunks;
        private final SortedMap<Long, EncryptedDigest> digests;
        private final List<StreamMetaData> metaData;

        @JsonCreator
        public MockStream(long streamId, SortedMap<Long, EncryptedChunk> chunks, SortedMap<Long, EncryptedDigest> digests,
                          List<StreamMetaData> metaData) {
            this.streamId = streamId;
            this.chunks = chunks;
            this.digests = digests;
            this.metaData = metaData;
        }

        private MockStream(long streamId, List<StreamMetaData> metaData) {
            this.streamId = streamId;
            this.metaData = metaData;
            this.chunks = new TreeMap<>();
            this.digests = new TreeMap<>();
        }

        public long getStreamId() {
            return streamId;
        }

        private long addChunk(EncryptedChunk chunk, EncryptedDigest digest) throws CouldNotStoreException {
            long chunkId = chunks.size();
            if (chunkId == chunk.getChunkId()) {
                chunks.put(chunkId, chunk);
            } else {
                throw new CouldNotStoreException("Chunk ID did not match the expected next chunk");
            }

            if (digest.getChunkIdFrom() == digest.getChunkIdTo() - 1 && digest.getChunkIdFrom() == chunkId) {
                digests.put(chunkId, digest);
            } else {
                throw new CouldNotStoreException("Digest range did not match the expected range. Digest range is " +
                        "from Chunk ID" + digest.getChunkIdFrom() + " to Chunk ID " + digest.getChunkIdTo() +
                        " expected it to be exactly from Chunk ID" + chunkId + " to Chunk ID " + chunkId);
            }

            return chunkId;
        }

        public SortedMap<Long, EncryptedChunk> getChunks() {
            return chunks;
        }

        public SortedMap<Long, EncryptedDigest> getDigests() {
            return digests;
        }

        public List<StreamMetaData> getMetaData() {
            return metaData;
        }
    }
}
