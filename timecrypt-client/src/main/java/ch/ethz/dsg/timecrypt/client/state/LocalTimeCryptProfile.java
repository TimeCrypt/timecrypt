/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.state;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LocalTimeCryptProfile implements TimeCryptProfile {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalTimeCryptProfile.class);

    @JsonIgnore
    private String path;
    @JsonIgnore
    private boolean dirty;
    private String serverAddress;
    private int serverPort;
    private String userName;
    private String profileName;
    private Map<Long, Stream> streams = new HashMap<>();

    public LocalTimeCryptProfile() {
        this.dirty = false;
    }

    public LocalTimeCryptProfile(String path, String userName, String profileName, String serverAddress, int serverPort,
                                 Map<Long, Stream> streams) {
        this.path = path;
        this.dirty = false;
        this.userName = userName;
        this.profileName = profileName;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.streams = streams;
    }

    /**
     * Create a new local TimeCrypt profile associated to a path but does not sync it yet.
     *
     * @param path          The path that corresponds to the profile. If it is null the profile will only be created
     *                      in memory.
     * @param userName      The name of the user of this profile on the (remote) server that will be used.
     * @param profileName   The name of this profile - should help users identify the right profile.
     * @param serverAddress The address of the (remote) TimeCrypt server to use.
     * @param serverPort    The port of the (remote) TimeCrypt server to use.
     */
    public LocalTimeCryptProfile(String path, String userName, String profileName, String serverAddress, int serverPort) {
        this.path = path;
        this.dirty = false;
        this.userName = userName;
        this.profileName = profileName;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    /**
     * Try to load the profile from a file.
     *
     * @param path The path to the file that should be a local TimeCrypt profile.
     * @return The profile if it could be loaded.
     * @throws IOException Could not find the profile or could not open it.
     */
    public static LocalTimeCryptProfile localProfileFromFile(String path) throws IOException {
        File profilePath = new File(path);

        if (!profilePath.exists()) {
            throw new FileNotFoundException();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
        mapper.registerModule(new Jdk8Module());

        LocalTimeCryptProfile profile = mapper.readValue(profilePath, LocalTimeCryptProfile.class);
        profile.setPath(profilePath.getAbsolutePath());
        return profile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalTimeCryptProfile that = (LocalTimeCryptProfile) o;
        return serverPort == that.serverPort &&
                serverAddress.equals(that.serverAddress) &&
                userName.equals(that.userName) &&
                streams.equals(that.streams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverAddress, serverPort, userName, streams);
    }

    private void setPath(String profilePath) {
        this.path = profilePath;
    }

    @Override
    public Map<Long, Stream> getStreams() {
        return streams;
    }

    @Override
    public String getServerAddress() {
        return this.serverAddress;
    }

    @Override
    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    @Override
    public int getServerPort() {
        return this.serverPort;
    }

    @Override
    public void setServerPort(int port) {
        this.serverPort = port;
    }

    @Override
    public String getProfileName() {
        return this.profileName;
    }

    @Override
    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    @Override
    public String getUserName() {
        return this.userName;
    }

    @Override
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public void addStream(Stream stream) {
        streams.put(stream.getId(), stream);
    }

    @Override
    public Stream getStream(long id) {
        return streams.get(id);
    }

    @Override
    public boolean syncProfile(boolean force) throws CouldNotStoreException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        if (path != null) {
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(path), this);
            } catch (IOException e) {
                LOGGER.error("Error occurred during the storage of the profile", e);
                throw new CouldNotStoreException("Error occurred during storing of the profile: " + e.getMessage());
            }
        }

        // TODO: Actually check if changes occurred since the last write.
        return true;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void deleteStream(long id) {
        streams.remove(id);
    }
}
