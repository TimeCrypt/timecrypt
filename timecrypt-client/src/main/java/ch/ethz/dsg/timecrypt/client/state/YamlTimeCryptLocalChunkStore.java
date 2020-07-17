/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.state;

import ch.ethz.dsg.timecrypt.client.exceptions.ChunkOutOfOrderException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the TimeCrypt local chunk store as a YAML file. The local chunk store should always only store one
 * value during normal operation (because it only stores the value while it is being send to the server.
 */
public class YamlTimeCryptLocalChunkStore implements TimeCryptLocalChunkStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(YamlTimeCryptLocalChunkStore.class);

    @JsonIgnore
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final SortedMap<Long, Pair<EncryptedChunk, EncryptedDigest>> unwrittenChunks;
    @JsonIgnore
    private String path;
    private long lastWrittenChunkId;

    private Object mutex = new Object();

    /**
     * The constructor for a new TimeCrypt local chunk store. WATCH OUT: You should only construct this once per stream
     * and from then on load the chunk store from disk!
     * <p>
     * It is possible to pass null as a constructor in order to crate a local chunk store that lives only in memory.
     *
     * @param path The path for storing this chunk store.
     * @throws IOException Exception if the chunk store could not be saved to disk.
     */
    public YamlTimeCryptLocalChunkStore(String path) throws IOException {
        this.path = path;
        this.lastWrittenChunkId = -1;
        this.unwrittenChunks = new TreeMap<>();
        if (path != null) {
            synchronized (mutex) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(path), this);
            }
        }
    }

    /**
     * The constructor for de-serializing the chunk store from Disk.
     *
     * @param unwrittenChunks    The storage for unwritten chunks.
     * @param lastWrittenChunkId The ID of the last written chunk.
     */
    @JsonCreator
    public YamlTimeCryptLocalChunkStore(SortedMap<Long, Pair<EncryptedChunk, EncryptedDigest>> unwrittenChunks,
                                        long lastWrittenChunkId) {
        this.unwrittenChunks = unwrittenChunks;
        this.lastWrittenChunkId = lastWrittenChunkId;
    }

    /**
     * Try to load the chunk store from a file.
     *
     * @param path The path to the file that should be a local TimeCrypt chunk store.
     * @return The chunk store if it could be loaded.
     * @throws IOException Could not find the chunk store or could not open it.
     */
    public static YamlTimeCryptLocalChunkStore loadYamlLocalChunkStore(String path) throws IOException {
        File chunkPath = new File(path);

        if (!chunkPath.exists()) {
            throw new FileNotFoundException();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
        mapper.registerModule(new Jdk8Module());

        YamlTimeCryptLocalChunkStore chunkStore = mapper.readValue(chunkPath, YamlTimeCryptLocalChunkStore.class);
        chunkStore.setPath(chunkPath.getAbsolutePath());
        return chunkStore;
    }

    /**
     * Sets the path of this profile. This is not in the constructor since it is not stored inside the serialized
     * profile file to allow easy movement of the file.
     *
     * @param absolutePath The path to the local chunk store file on disk.
     */
    public void setPath(String absolutePath) {
        this.path = absolutePath;
    }

    @Override
    public long getLastWrittenChunkId() {
        return lastWrittenChunkId;
    }

    @Override
    public void setLastWrittenChunkId(long id) {
        synchronized (mutex) {
            this.lastWrittenChunkId = id;
        }
    }

    @Override
    public void addUnwrittenChunk(EncryptedChunk chunk, EncryptedDigest digest) throws CouldNotStoreException,
            ChunkOutOfOrderException {
        long newChunkId = chunk.getChunkId();
        synchronized (mutex) {
            if (newChunkId <= lastWrittenChunkId) {
                // chunk was already written
                LOGGER.error("Can not store chunk for writing - its ID (" + newChunkId +
                        ") is smaller or equal to the last written chunk ID (" + lastWrittenChunkId + ").");
                throw new ChunkOutOfOrderException(newChunkId, lastWrittenChunkId);
            } else if (newChunkId == (this.lastWrittenChunkId + 1)) {
                unwrittenChunks.put(chunk.getChunkId(), new ImmutablePair<>(chunk, digest));
            } else {
                // chunk is not the next value to write
                LOGGER.error("Can not store chunk for writing - its ID is not the next value to write.");
                throw new ChunkOutOfOrderException(newChunkId, lastWrittenChunkId);
            }
        }
        writeChunkStore();
    }

    @Override
    public SortedMap<Long, Pair<EncryptedChunk, EncryptedDigest>> getUnwrittenChunks() {
        return unwrittenChunks;
    }

    @Override
    public void markChunkAsWritten(long chunkId) throws ChunkOutOfOrderException, CouldNotStoreException {
        synchronized (mutex) {
            if (chunkId == (this.lastWrittenChunkId + 1)) {
                this.unwrittenChunks.remove(chunkId);
                this.lastWrittenChunkId++;
            } else {
                throw new ChunkOutOfOrderException(chunkId, lastWrittenChunkId);
            }
        }
        writeChunkStore();
    }

    private void writeChunkStore() throws CouldNotStoreException {
        if (path != null) {
            synchronized (mutex) {
                try {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(path), this);
                } catch (IOException e) {
                    LOGGER.error("Error occurred during the storage of the local chunkstore", e);
                    throw new CouldNotStoreException("Error occurred during storing of the local chunkstore: "
                            + e.getMessage());
                }
            }
        }
    }

    @Override
    public void deleteChunkStore() {
        if (path != null) {
            File file = new File(path);
            file.delete();
        }
    }
}
