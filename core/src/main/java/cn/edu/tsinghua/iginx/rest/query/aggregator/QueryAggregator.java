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
package cn.edu.tsinghua.iginx.rest.query.aggregator;

import cn.edu.tsinghua.iginx.rest.RestSession;
import cn.edu.tsinghua.iginx.rest.bean.QueryResultDataset;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.AggregateType;
import cn.edu.tsinghua.iginx.thrift.TimePrecision;
import cn.edu.tsinghua.iginx.utils.TimeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class QueryAggregator {
    private Double divisor;
    private Long dur;
    private double percentile;
    private long unit;
    private String metric_name;
    private Filter filter;
    private QueryAggregatorType type;
    private AggregateType aggregateType;

    protected QueryAggregator(QueryAggregatorType type) {
        this(type, null);
    }

    protected QueryAggregator(QueryAggregatorType type, AggregateType aggregateType) {
        this.type = type;
        this.aggregateType = aggregateType;
    }

    public Double getDivisor() {
        return divisor;
    }

    public void setDivisor(Double divisor) {
        this.divisor = divisor;
    }

    public Long getDur() {
        return dur;
    }

    public void setDur(Long dur) {
        this.dur = dur;
    }

    public double getPercentile() {
        return percentile;
    }

    public void setPercentile(double percentile) {
        this.percentile = percentile;
    }

    public long getUnit() {
        return unit;
    }

    public void setUnit(long unit) {
        this.unit = unit;
    }

    public String getMetric_name() {
        return metric_name;
    }

    public void setMetric_name(String metric_name) {
        this.metric_name = metric_name;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public QueryAggregatorType getType() {
        return type;
    }

    public void setType(QueryAggregatorType type) {
        this.type = type;
    }

    public QueryResultDataset doAggregate(
            RestSession session,
            List<String> paths,
            Map<String, List<String>> tagList,
            long startTimestamp,
            long endTimestamp) {
        return doAggregate(
                session,
                paths,
                tagList,
                startTimestamp,
                endTimestamp,
                TimeUtils.DEFAULT_TIMESTAMP_PRECISION);
    }

    public QueryResultDataset doAggregate(
            RestSession session,
            List<String> paths,
            Map<String, List<String>> tagList,
            long startTimestamp,
            long endTimestamp,
            TimePrecision timePrecision) {
        SessionQueryDataSet sessionQueryDataSet = null;
        try {
            if (type == QueryAggregatorType.NONE) {
                sessionQueryDataSet =
                        session.queryData(
                                paths, startTimestamp, endTimestamp, tagList, timePrecision);
            } else if (aggregateType != null) {
                sessionQueryDataSet =
                        session.downsampleQuery(
                                paths,
                                tagList,
                                startTimestamp,
                                endTimestamp,
                                aggregateType,
                                getDur(),
                                timePrecision);
            }
        } catch (Exception e) {
            // TODO: more precise exception catch
            e.printStackTrace();
            return new QueryResultDataset();
        }
        if (sessionQueryDataSet == null) {
            // type != QueryAggregatorType.NONE) and aggregateType == null
            throw new RuntimeException("Unsupported Query");
        }
        QueryResultDataset queryResultDataset = new QueryResultDataset();
        queryResultDataset.setPaths(getPathsFromSessionQueryDataSet(sessionQueryDataSet));
        int n = sessionQueryDataSet.getKeys().length;
        int m = sessionQueryDataSet.getPaths().size();
        int datapoints = 0;
        for (int j = 0; j < m; j++) {
            List<Object> value = new ArrayList<>();
            List<Long> time = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (sessionQueryDataSet.getValues().get(i).get(j) != null) {
                    value.add(sessionQueryDataSet.getValues().get(i).get(j));
                    long timeRes = sessionQueryDataSet.getKeys()[i];
                    time.add(timeRes);
                    queryResultDataset.add(timeRes, sessionQueryDataSet.getValues().get(i).get(j));
                    datapoints += 1;
                }
            }
            queryResultDataset.addValueLists(value);
            queryResultDataset.addTimeLists(time);
        }
        queryResultDataset.setSampleSize(datapoints);
        return queryResultDataset;
    }

    public List<String> getPathsFromSessionQueryDataSet(SessionQueryDataSet sessionQueryDataSet) {
        List<String> ret = new ArrayList<>();
        List<Boolean> notNull = new ArrayList<>();
        int n = sessionQueryDataSet.getKeys().length;
        int m = sessionQueryDataSet.getPaths().size();
        for (int i = 0; i < m; i++) {
            notNull.add(false);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (sessionQueryDataSet.getValues().get(i).get(j) != null) {
                    notNull.set(j, true);
                }
            }
        }
        for (int i = 0; i < m; i++) {
            if (notNull.get(i)) {
                ret.add(sessionQueryDataSet.getPaths().get(i));
            }
        }
        return ret;
    }

    public List<String> getPathsFromShowTimeSeries(SessionQueryDataSet sessionQueryDataSet) {
        List<String> ret = new ArrayList<>();
        List<Boolean> notNull = new ArrayList<>();
        int m = sessionQueryDataSet.getPaths().size();
        for (int i = 0; i < m; i++) {
            notNull.add(false);
        }
        for (int i = 0; i < m; i++) {
            ret.add(sessionQueryDataSet.getPaths().get(i));
        }
        return ret;
    }
}
