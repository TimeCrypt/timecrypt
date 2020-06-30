/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface.calculations;

import ch.ethz.dsg.timecrypt.client.exceptions.QueryFailedException;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Digest;

import java.util.List;

public class StdCalculation extends VarianceCalculation {

    @Override
    public Double performOnDigests(List<Digest> digests) throws QueryFailedException {
        return Math.sqrt(super.performOnDigests(digests));
    }

    @Override
    public Double performOnDataPoints(List<DataPoint> dataPoints) {
        return Math.sqrt(super.performOnDataPoints(dataPoints));
    }

    @Override
    public Double performOnMixed(List<Digest> digests, List<DataPoint> dataPoints) throws QueryFailedException {
        return Math.sqrt(super.performOnMixed(digests, dataPoints));
    }
}
