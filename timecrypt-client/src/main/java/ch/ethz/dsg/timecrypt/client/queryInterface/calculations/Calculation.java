/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface.calculations;

import ch.ethz.dsg.timecrypt.client.exceptions.QueryFailedException;
import ch.ethz.dsg.timecrypt.client.exceptions.QueryNeedsChunkScanException;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Digest;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;

import java.util.List;

/**
 * A calculation represents an operation that can be performed on a TimeCrypt stream.
 */
public interface Calculation {

    Query configureQuery(Stream stream, boolean chunkScanAllowed, Query query) throws QueryNeedsChunkScanException;

    Double performOnDigests(List<Digest> digests) throws QueryFailedException;

    Double performOnDataPoints(List<DataPoint> dataPoints);

    Double performOnMixed(List<Digest> digests, List<DataPoint> dataPoints) throws QueryFailedException;
}
