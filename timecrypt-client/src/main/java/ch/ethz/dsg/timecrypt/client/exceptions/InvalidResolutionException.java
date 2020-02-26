/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

import ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil;

import java.util.List;

/**
 * Gets raised when an invalid or unknown resolution is requested for a stream.
 */
public class InvalidResolutionException extends Exception {

    private final TimeUtil.Precision requestedResolution;
    private final List<TimeUtil.Precision> availableResolutions;

    /**
     * Creates a new invalid resolution exception to raise in context where stream resolutions that are not available
     * get requested.
     *
     * @param requestedResolution  The resolution that was requested.
     * @param availableResolutions The list of resolutions that were available in this context.
     */
    public InvalidResolutionException(TimeUtil.Precision requestedResolution, List<TimeUtil.Precision> availableResolutions) {
        super("Requested Resolution " + requestedResolution + " but only the following resolutions where available: "
                + availableResolutions);
        this.requestedResolution = requestedResolution;
        this.availableResolutions = availableResolutions;
    }

    /**
     * Returns the requested Resolution that lead to raising this exception
     *
     * @return The resolution
     */
    public TimeUtil.Precision getRequestedResolution() {
        return requestedResolution;
    }

    /**
     * Returns the available resolutions in this context.
     *
     * @return A list of available resolutions.
     */
    public List<TimeUtil.Precision> getAvailableResolutions() {
        return availableResolutions;
    }

}
