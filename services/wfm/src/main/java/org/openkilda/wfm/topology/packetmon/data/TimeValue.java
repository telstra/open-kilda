/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.packetmon.data;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class TimeValue {
    private DescriptiveStatistics timeValues;
    private DescriptiveStatistics dataValues;

    /**
     * Represents a list of time and values to form a timeseries.
     *
     * @param windowSize int Maximum values that will be stored.
     */
    public TimeValue(int windowSize) {
        timeValues = new DescriptiveStatistics(windowSize);
        dataValues = new DescriptiveStatistics(windowSize);
    }

    /**
     * Minimum time value in the series.
     *
     * @return double
     */
    public double getMinTime() {
        return timeValues.getMin();
    }

    /**
     * Maximum time value in the series.
     *
     * @return double
     */
    public double getMaxTime() {
        return timeValues.getMax();
    }

    /**
     * Duration represented in the series.
     *
     * @return double
     */
    public double getDuration() {
        return getMaxTime() - getMinTime();
    }

    /**
     * Minimum value in the series.
     *
     * @return double
     */
    public double getMinValue() {
        return dataValues.getMin();
    }

    /**
     * Maximum value in the series.
     *
     * @return double
     */
    public double getMaxValue() {
        return dataValues.getMax();
    }

    /**
     * Difference between the max and min values.
     *
     * @return double
     */
    public double getDeltaValue() {
        return getMaxValue() - getMinValue();
    }

    /**
     * Calculates average rate based on delta in values / time duration.
     *
     * @return double
     */
    public double getOverallRate() {
        return getDeltaValue() / getDuration();
    }

    /**
     * Adds a new time/value pair to the series.
     *
     * @param time long Time as epoch
     * @param value long Value
     */
    public void add(long time, long value) {
        timeValues.addValue(time);
        dataValues.addValue(value);
    }

    /**
     * All times in the series.
     *
     * @return double[]
     */
    public double[] getTimeValues() {
        return timeValues.getValues();
    }

    /**
     * All values in the series.
     *
     * @return double[]
     */
    public double[] getDataValues() {
        return dataValues.getValues();
    }

    /**
     * Calculate rates for a series of doubles, where rate is t[i] - t[i-1].
     *
     * @param values double List of values
     * @return double[]
     */
    public double[] getRates(double[] values) {
        double[] result = new double[values.length - 1];
        for (int i = 1; i < values.length; i++) {
            result[i - 1] = values[i] - values[i - 1];
        }
        return result;
    }

    /**
     * Calculate speeds based on rate of values / time delta.
     *
     * @return double[]
     */
    public double[] getSpeeds() {
        double[] durations = getRates(timeValues.getValues());
        double[] deltaValues = getRates(dataValues.getValues());

        double[] speeds = new double[durations.length];
        for (int i = 0; i < durations.length; i++) {
            speeds[i] = deltaValues[i] / durations[i];
        }
        return speeds;
    }

    /**
     * Returns speeds as a Descriptive Statistics object.
     *
     * @return DescriptiveStatistics
     */
    public DescriptiveStatistics getStats() {
        return new DescriptiveStatistics(getSpeeds());
    }

    /**
     * Calculates the average speed based on the average of each speed.
     *
     * @return double
     */
    public double getAvgSpeed() {
        return getStats().getMean();
    }

    /**
     * Number of elements in the series.
     *
     * @return long
     */
    public long size() {
        return timeValues.getN();
    }

}
