/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package cn.edu.tsinghua.iginx.metadata.entity;

import static cn.edu.tsinghua.iginx.utils.StringUtils.isContainSpecialChar;

import com.alibaba.fastjson2.annotation.JSONType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JSONType(
        seeAlso = {TimeSeriesInterval.class, TimeSeriesPrefixRange.class},
        typeKey = "type")
public interface TimeSeriesRange extends Comparable<TimeSeriesRange> {

    public static Logger logger = LoggerFactory.getLogger(TimeSeriesRange.class);

    public static enum TYPE {
        PREFIX,
        NORMAL
    }

    public TYPE getType();

    public default boolean isNormal() {
        return getType() == TYPE.NORMAL;
    }

    public default boolean isPrefix() {
        return getType() == TYPE.PREFIX;
    }

    public default void setTimeSeries(String timeSeries) {
        if (getType() == TYPE.NORMAL) {
            logger.error("TimeSeriesInterval Normal can't not use the setTimeSeries func");
            System.exit(0);
        }
    }

    public default String getTimeSeries() {
        logger.warn("TimeSeriesInterval Normal can't not use the getTimeSeries func");
        return null;
    }

    public default String getStartTimeSeries() {
        if (getType() == TYPE.PREFIX) {
            logger.error("TimeSeriesInterval PREFIX can't not use the getStartTimeSeries func");
            return null;
        }
        return null;
    }

    public default void setStartTimeSeries(String startTimeSeries) {
        if (getType() == TYPE.PREFIX) {
            logger.error("TimeSeriesInterval PREFIX can't not use the setStartTimeSeries func");
            System.exit(0);
        }
    }

    public default String getEndTimeSeries() {
        if (getType() == TYPE.PREFIX) {
            logger.error("TimeSeriesInterval PREFIX can't not use the getEndTimeSeries func");
            return null;
        }
        return null;
    }

    public default void setEndTimeSeries(String endTimeSeries) {
        if (getType() == TYPE.PREFIX) {
            logger.error("TimeSeriesInterval PREFIX can't not use the setEndTimeSeries func");
            System.exit(0);
        }
    }

    public String getSchemaPrefix();

    public void setSchemaPrefix(String schemaPrefix);

    public default boolean isCompletelyAfter(TimeSeriesRange tsInterval) {
        return false;
    }

    public default boolean isAfter(String tsName) {
        return false;
    }

    public boolean isClosed();

    public void setClosed(boolean closed);

    // Strange function: it should not work on the implementation of TimeSeriesPrefixRange
    public static TimeSeriesRange fromString(String str) throws IllegalArgumentException {
        if (str.contains("-") && !isContainSpecialChar(str)) {
            String[] parts = str.split("-");
            if (parts.length != 2) {
                logger.error("Input string {} in invalid format of TimeSeriesInterval ", str);
                throw new IllegalArgumentException(
                        "Input invalid string format in TimeSeriesRange");
            }
            return new TimeSeriesInterval(
                    parts[0].equals("null") ? null : parts[0],
                    parts[1].equals("null") ? null : parts[1]);
        } else {
            if (str.contains(".*") && str.indexOf(".*") == str.length() - 2)
                str = str.substring(0, str.length() - 2);
            if (!isContainSpecialChar(str)) return new TimeSeriesPrefixRange(str);
            else {
                logger.error("Input string {} in invalid format of TimeSeriesPrefixRange ", str);
                throw new IllegalArgumentException(
                        "Input invalid string format in TimeSeriesRange");
            }
        }
    }

    public boolean isContain(String tsName);

    public boolean isIntersect(TimeSeriesRange tsInterval);

    public int compareTo(TimeSeriesRange o);
}
