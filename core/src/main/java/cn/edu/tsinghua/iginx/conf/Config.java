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
package cn.edu.tsinghua.iginx.conf;

import cn.edu.tsinghua.iginx.thrift.TimePrecision;
import cn.edu.tsinghua.iginx.utils.TagKVUtils;
import java.util.ArrayList;
import java.util.List;

public class Config {

    private String ip = "0.0.0.0";

    private int port = 6888;

    private String username = "root";

    private String password = "root";

    private String metaStorage = "zookeeper";

    private String zookeeperConnectionString = "127.0.0.1:2181";

    private String storageEngineList =
            "127.0.0.1#6667#iotdb12#username=root#password=root#sessionPoolSize=20#dataDir=/path/to/your/data/";

    private int maxAsyncRetryTimes = 2;

    private int syncExecuteThreadPool = 60;

    private int asyncExecuteThreadPool = 20;

    private int replicaNum = 1;

    private TimePrecision timePrecision = TimePrecision.NS;

    private String databaseClassNames =
            "iotdb=cn.edu.tsinghua.iginx.iotdb.IoTDBPlanExecutor,influxdb=cn.edu.tsinghua.iginx.influxdb.InfluxDBPlanExecutor,parquet=cn.edu.tsinghua.iginx.parquet.parquetStorage";
    // ,opentsdb=cn.edu.tsinghua.iginx.opentsdb.OpenTSDBStorage,timescaledb=cn.edu.tsinghua.iginx.timescaledb.TimescaleDBStorage,postgresql=cn.edu.tsinghua.iginx.postgresql.PostgreSQLStorage

    private String policyClassName = "cn.edu.tsinghua.iginx.policy.naive.NativePolicy";

    private boolean enableMonitor = true;

    private int loadBalanceCheckInterval = 3;

    private boolean enableFragmentCompaction = false;

    private boolean enableInstantCompaction = false; // 启动即时分片合并，仅用于测试

    private long fragmentCompactionWriteThreshold = 1000;

    private long fragmentCompactionReadThreshold = 1000;

    private double fragmentCompactionReadRatioThreshold = 0.1;

    private long reshardFragmentTimeMargin = 60;

    private String migrationPolicyClassName =
            "cn.edu.tsinghua.iginx.migration.GreedyMigrationPolicy";

    private long migrationBatchSize = 100;

    private int maxReshardFragmentsNum = 3;

    private double maxTimeseriesLoadBalanceThreshold = 2;

    private String statisticsCollectorClassName = "";

    private int statisticsLogInterval = 5000;

    private boolean enableEnvParameter = false;

    private String restIp = "127.0.0.1";

    private int restPort = 6666;

    private int maxTimeseriesLength = 10;

    private long disorderMargin = 10;

    private int asyncRestThreadPool = 100;

    private boolean enableRestService = true;

    private String etcdEndpoints = "http://localhost:2379";

    private boolean enableMQTT = false;

    private String mqttHost = "0.0.0.0";

    private int mqttPort = 1883;

    private int mqttHandlerPoolSize = 1;

    private String mqttPayloadFormatter = "cn.edu.tsinghua.iginx.mqtt.JsonPayloadFormatter";

    private int mqttMaxMessageSize = 1048576;

    private String clients = "";

    private int instancesNumPerClient = 0;

    private String queryOptimizer = "";

    private String constraintChecker = "naive";

    private String physicalOptimizer = "naive";

    private int memoryTaskThreadPoolSize = 200;

    private int physicalTaskThreadPoolSizePerStorage = 100;

    private int maxCachedPhysicalTaskPerStorage = 500;

    private double cachedTimeseriesProb = 0.01;

    private int retryCount = 10;

    private int retryWait = 5000;

    private int reAllocatePeriod = 30000;

    private int fragmentPerEngine = 10;

    private boolean enableStorageGroupValueLimit = true;

    private double storageGroupValueLimit = 200.0;

    private boolean enablePushDown = true;

    private boolean useStreamExecutor = true;

    private boolean enableMemoryControl = true;

    private String systemResourceMetrics = "default";

    private double heapMemoryThreshold = 0.9;

    private double systemMemoryThreshold = 0.9;

    private double systemCpuThreshold = 0.9;

    private boolean enableMetaCacheControl = false;

    private long fragmentCacheThreshold = 1024 * 128;

    private int batchSize = 50;

    private String pythonCMD = "python3";

    private int transformTaskThreadPoolSize = 10;

    private int transformMaxRetryTimes = 3;

    private boolean needInitBasicUDFFunctions = true;

    private List<String> udfList = new ArrayList<>();

    private String historicalPrefixList = "";

    private int expectedStorageUnitNum = 0;

    private int minThriftWorkerThreadNum = 20;

    private int maxThriftWrokerThreadNum = 2147483647;

    //////////////

    public static final String tagNameAnnotation = TagKVUtils.tagNameAnnotation;

    public static final String tagPrefix = TagKVUtils.tagPrefix;

    public static final String tagSuffix = TagKVUtils.tagSuffix;

    /////////////

    private boolean isLocalParquetStorage = true;

    public int getMaxTimeseriesLength() {
        return maxTimeseriesLength;
    }

    public void setMaxTimeseriesLength(int maxTimeseriesLength) {
        this.maxTimeseriesLength = maxTimeseriesLength;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getZookeeperConnectionString() {
        return zookeeperConnectionString;
    }

    public void setZookeeperConnectionString(String zookeeperConnectionString) {
        this.zookeeperConnectionString = zookeeperConnectionString;
    }

    public String getStorageEngineList() {
        return storageEngineList;
    }

    public void setStorageEngineList(String storageEngineList) {
        this.storageEngineList = storageEngineList;
    }

    public int getMaxAsyncRetryTimes() {
        return maxAsyncRetryTimes;
    }

    public void setMaxAsyncRetryTimes(int maxAsyncRetryTimes) {
        this.maxAsyncRetryTimes = maxAsyncRetryTimes;
    }

    public int getSyncExecuteThreadPool() {
        return syncExecuteThreadPool;
    }

    public void setSyncExecuteThreadPool(int syncExecuteThreadPool) {
        this.syncExecuteThreadPool = syncExecuteThreadPool;
    }

    public int getAsyncExecuteThreadPool() {
        return asyncExecuteThreadPool;
    }

    public void setAsyncExecuteThreadPool(int asyncExecuteThreadPool) {
        this.asyncExecuteThreadPool = asyncExecuteThreadPool;
    }

    public int getReplicaNum() {
        return replicaNum;
    }

    public void setReplicaNum(int replicaNum) {
        this.replicaNum = replicaNum;
    }

    public TimePrecision getTimePrecision() {
        return timePrecision;
    }

    public String getDatabaseClassNames() {
        return databaseClassNames;
    }

    public void setDatabaseClassNames(String databaseClassNames) {
        this.databaseClassNames = databaseClassNames;
    }

    public long getReshardFragmentTimeMargin() {
        return reshardFragmentTimeMargin;
    }

    public void setReshardFragmentTimeMargin(long reshardFragmentTimeMargin) {
        this.reshardFragmentTimeMargin = reshardFragmentTimeMargin;
    }

    public String getMigrationPolicyClassName() {
        return migrationPolicyClassName;
    }

    public void setMigrationPolicyClassName(String migrationPolicyClassName) {
        this.migrationPolicyClassName = migrationPolicyClassName;
    }

    public String getPolicyClassName() {
        return policyClassName;
    }

    public void setPolicyClassName(String policyClassName) {
        this.policyClassName = policyClassName;
    }

    public boolean isEnableMonitor() {
        return enableMonitor;
    }

    public void setEnableMonitor(boolean enableMonitor) {
        this.enableMonitor = enableMonitor;
    }

    public int getLoadBalanceCheckInterval() {
        return loadBalanceCheckInterval;
    }

    public void setLoadBalanceCheckInterval(int loadBalanceCheckInterval) {
        this.loadBalanceCheckInterval = loadBalanceCheckInterval;
    }

    public boolean isEnableFragmentCompaction() {
        return enableFragmentCompaction;
    }

    public void setEnableFragmentCompaction(boolean enableFragmentCompaction) {
        this.enableFragmentCompaction = enableFragmentCompaction;
    }

    public boolean isEnableInstantCompaction() {
        return enableInstantCompaction;
    }

    public void setEnableInstantCompaction(boolean enableInstantCompaction) {
        this.enableInstantCompaction = enableInstantCompaction;
    }

    public long getFragmentCompactionWriteThreshold() {
        return fragmentCompactionWriteThreshold;
    }

    public void setFragmentCompactionWriteThreshold(long fragmentCompactionWriteThreshold) {
        this.fragmentCompactionWriteThreshold = fragmentCompactionWriteThreshold;
    }

    public long getFragmentCompactionReadThreshold() {
        return fragmentCompactionReadThreshold;
    }

    public void setFragmentCompactionReadThreshold(long fragmentCompactionReadThreshold) {
        this.fragmentCompactionReadThreshold = fragmentCompactionReadThreshold;
    }

    public double getFragmentCompactionReadRatioThreshold() {
        return fragmentCompactionReadRatioThreshold;
    }

    public void setFragmentCompactionReadRatioThreshold(
            double fragmentCompactionReadRatioThreshold) {
        this.fragmentCompactionReadRatioThreshold = fragmentCompactionReadRatioThreshold;
    }

    public long getMigrationBatchSize() {
        return migrationBatchSize;
    }

    public void setMigrationBatchSize(long migrationBatchSize) {
        this.migrationBatchSize = migrationBatchSize;
    }

    public int getMaxReshardFragmentsNum() {
        return maxReshardFragmentsNum;
    }

    public void setMaxReshardFragmentsNum(int maxReshardFragmentsNum) {
        this.maxReshardFragmentsNum = maxReshardFragmentsNum;
    }

    public double getMaxTimeseriesLoadBalanceThreshold() {
        return maxTimeseriesLoadBalanceThreshold;
    }

    public void setMaxTimeseriesLoadBalanceThreshold(double maxTimeseriesLoadBalanceThreshold) {
        this.maxTimeseriesLoadBalanceThreshold = maxTimeseriesLoadBalanceThreshold;
    }

    public String getStatisticsCollectorClassName() {
        return statisticsCollectorClassName;
    }

    public void setStatisticsCollectorClassName(String statisticsCollectorClassName) {
        this.statisticsCollectorClassName = statisticsCollectorClassName;
    }

    public int getStatisticsLogInterval() {
        return statisticsLogInterval;
    }

    public void setStatisticsLogInterval(int statisticsLogInterval) {
        this.statisticsLogInterval = statisticsLogInterval;
    }

    public boolean isEnableEnvParameter() {
        return enableEnvParameter;
    }

    public void setEnableEnvParameter(boolean enableEnvParameter) {
        this.enableEnvParameter = enableEnvParameter;
    }

    public String getRestIp() {
        return restIp;
    }

    public void setRestIp(String restIp) {
        this.restIp = restIp;
    }

    public int getRestPort() {
        return restPort;
    }

    public void setRestPort(int restPort) {
        this.restPort = restPort;
    }

    public int getAsyncRestThreadPool() {
        return asyncRestThreadPool;
    }

    public void setAsyncRestThreadPool(int asyncRestThreadPool) {
        this.asyncRestThreadPool = asyncRestThreadPool;
    }

    public boolean isEnableRestService() {
        return enableRestService;
    }

    public void setEnableRestService(boolean enableRestService) {
        this.enableRestService = enableRestService;
    }

    public String getMetaStorage() {
        return metaStorage;
    }

    public void setMetaStorage(String metaStorage) {
        this.metaStorage = metaStorage;
    }

    public long getDisorderMargin() {
        return disorderMargin;
    }

    public void setDisorderMargin(long disorderMargin) {
        this.disorderMargin = disorderMargin;
    }

    public String getEtcdEndpoints() {
        return etcdEndpoints;
    }

    public void setEtcdEndpoints(String etcdEndpoints) {
        this.etcdEndpoints = etcdEndpoints;
    }

    public boolean isEnableMQTT() {
        return enableMQTT;
    }

    public void setEnableMQTT(boolean enableMQTT) {
        this.enableMQTT = enableMQTT;
    }

    public String getMqttHost() {
        return mqttHost;
    }

    public void setMqttHost(String mqttHost) {
        this.mqttHost = mqttHost;
    }

    public int getMqttPort() {
        return mqttPort;
    }

    public void setMqttPort(int mqttPort) {
        this.mqttPort = mqttPort;
    }

    public int getMqttHandlerPoolSize() {
        return mqttHandlerPoolSize;
    }

    public void setMqttHandlerPoolSize(int mqttHandlerPoolSize) {
        this.mqttHandlerPoolSize = mqttHandlerPoolSize;
    }

    public String getMqttPayloadFormatter() {
        return mqttPayloadFormatter;
    }

    public void setMqttPayloadFormatter(String mqttPayloadFormatter) {
        this.mqttPayloadFormatter = mqttPayloadFormatter;
    }

    public int getMqttMaxMessageSize() {
        return mqttMaxMessageSize;
    }

    public void setMqttMaxMessageSize(int mqttMaxMessageSize) {
        this.mqttMaxMessageSize = mqttMaxMessageSize;
    }

    public String getClients() {
        return clients;
    }

    public void setClients(String clients) {
        this.clients = clients;
    }

    public int getInstancesNumPerClient() {
        return instancesNumPerClient;
    }

    public void setInstancesNumPerClient(int instancesNumPerClient) {
        this.instancesNumPerClient = instancesNumPerClient;
    }

    public String getQueryOptimizer() {
        return queryOptimizer;
    }

    public void setQueryOptimizer(String queryOptimizer) {
        this.queryOptimizer = queryOptimizer;
    }

    public String getConstraintChecker() {
        return constraintChecker;
    }

    public void setConstraintChecker(String constraintChecker) {
        this.constraintChecker = constraintChecker;
    }

    public String getPhysicalOptimizer() {
        return physicalOptimizer;
    }

    public void setPhysicalOptimizer(String physicalOptimizer) {
        this.physicalOptimizer = physicalOptimizer;
    }

    public int getMemoryTaskThreadPoolSize() {
        return memoryTaskThreadPoolSize;
    }

    public void setMemoryTaskThreadPoolSize(int memoryTaskThreadPoolSize) {
        this.memoryTaskThreadPoolSize = memoryTaskThreadPoolSize;
    }

    public int getPhysicalTaskThreadPoolSizePerStorage() {
        return physicalTaskThreadPoolSizePerStorage;
    }

    public void setPhysicalTaskThreadPoolSizePerStorage(int physicalTaskThreadPoolSizePerStorage) {
        this.physicalTaskThreadPoolSizePerStorage = physicalTaskThreadPoolSizePerStorage;
    }

    public int getMaxCachedPhysicalTaskPerStorage() {
        return maxCachedPhysicalTaskPerStorage;
    }

    public void setMaxCachedPhysicalTaskPerStorage(int maxCachedPhysicalTaskPerStorage) {
        this.maxCachedPhysicalTaskPerStorage = maxCachedPhysicalTaskPerStorage;
    }

    public double getCachedTimeseriesProb() {
        return cachedTimeseriesProb;
    }

    public void setCachedTimeseriesProb(double cachedTimeseriesProb) {
        this.cachedTimeseriesProb = cachedTimeseriesProb;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getRetryWait() {
        return retryWait;
    }

    public void setRetryWait(int retryWait) {
        this.retryWait = retryWait;
    }

    public int getReAllocatePeriod() {
        return reAllocatePeriod;
    }

    public void setReAllocatePeriod(int reAllocatePeriod) {
        this.reAllocatePeriod = reAllocatePeriod;
    }

    public int getFragmentPerEngine() {
        return fragmentPerEngine;
    }

    public void setFragmentPerEngine(int fragmentPerEngine) {
        this.fragmentPerEngine = fragmentPerEngine;
    }

    public boolean isEnableStorageGroupValueLimit() {
        return enableStorageGroupValueLimit;
    }

    public void setEnableStorageGroupValueLimit(boolean enableStorageGroupValueLimit) {
        this.enableStorageGroupValueLimit = enableStorageGroupValueLimit;
    }

    public double getStorageGroupValueLimit() {
        return storageGroupValueLimit;
    }

    public void setStorageGroupValueLimit(double storageGroupValueLimit) {
        this.storageGroupValueLimit = storageGroupValueLimit;
    }

    public boolean isEnablePushDown() {
        return enablePushDown;
    }

    public void setEnablePushDown(boolean enablePushDown) {
        this.enablePushDown = enablePushDown;
    }

    public boolean isUseStreamExecutor() {
        return useStreamExecutor;
    }

    public void setUseStreamExecutor(boolean useStreamExecutor) {
        this.useStreamExecutor = useStreamExecutor;
    }

    public boolean isEnableMemoryControl() {
        return enableMemoryControl;
    }

    public void setEnableMemoryControl(boolean enableMemoryControl) {
        this.enableMemoryControl = enableMemoryControl;
    }

    public String getSystemResourceMetrics() {
        return systemResourceMetrics;
    }

    public void setSystemResourceMetrics(String systemResourceMetrics) {
        this.systemResourceMetrics = systemResourceMetrics;
    }

    public double getHeapMemoryThreshold() {
        return heapMemoryThreshold;
    }

    public void setHeapMemoryThreshold(double heapMemoryThreshold) {
        this.heapMemoryThreshold = heapMemoryThreshold;
    }

    public double getSystemMemoryThreshold() {
        return systemMemoryThreshold;
    }

    public void setSystemMemoryThreshold(double systemMemoryThreshold) {
        this.systemMemoryThreshold = systemMemoryThreshold;
    }

    public double getSystemCpuThreshold() {
        return systemCpuThreshold;
    }

    public void setSystemCpuThreshold(double systemCpuThreshold) {
        this.systemCpuThreshold = systemCpuThreshold;
    }

    public boolean isEnableMetaCacheControl() {
        return enableMetaCacheControl;
    }

    public void setEnableMetaCacheControl(boolean enableMetaCacheControl) {
        this.enableMetaCacheControl = enableMetaCacheControl;
    }

    public long getFragmentCacheThreshold() {
        return fragmentCacheThreshold;
    }

    public void setFragmentCacheThreshold(long fragmentCacheThreshold) {
        this.fragmentCacheThreshold = fragmentCacheThreshold;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getPythonCMD() {
        return pythonCMD;
    }

    public void setPythonCMD(String pythonCMD) {
        this.pythonCMD = pythonCMD;
    }

    public int getTransformTaskThreadPoolSize() {
        return transformTaskThreadPoolSize;
    }

    public void setTransformTaskThreadPoolSize(int transformTaskThreadPoolSize) {
        this.transformTaskThreadPoolSize = transformTaskThreadPoolSize;
    }

    public int getTransformMaxRetryTimes() {
        return transformMaxRetryTimes;
    }

    public void setTransformMaxRetryTimes(int transformMaxRetryTimes) {
        this.transformMaxRetryTimes = transformMaxRetryTimes;
    }

    public boolean isNeedInitBasicUDFFunctions() {
        return needInitBasicUDFFunctions;
    }

    public void setNeedInitBasicUDFFunctions(boolean needInitBasicUDFFunctions) {
        this.needInitBasicUDFFunctions = needInitBasicUDFFunctions;
    }

    public List<String> getUdfList() {
        return udfList;
    }

    public void setUdfList(List<String> udfList) {
        this.udfList = udfList;
    }

    public String getHistoricalPrefixList() {
        return historicalPrefixList;
    }

    public void setHistoricalPrefixList(String historicalPrefixList) {
        this.historicalPrefixList = historicalPrefixList;
    }

    public int getExpectedStorageUnitNum() {
        return expectedStorageUnitNum;
    }

    public void setExpectedStorageUnitNum(int expectedStorageUnitNum) {
        this.expectedStorageUnitNum = expectedStorageUnitNum;
    }

    public int getMinThriftWorkerThreadNum() {
        return minThriftWorkerThreadNum;
    }

    public void setMinThriftWorkerThreadNum(int minThriftWorkerThreadNum) {
        this.minThriftWorkerThreadNum = minThriftWorkerThreadNum;
    }

    public int getMaxThriftWrokerThreadNum() {
        return maxThriftWrokerThreadNum;
    }

    public void setMaxThriftWrokerThreadNum(int maxThriftWrokerThreadNum) {
        this.maxThriftWrokerThreadNum = maxThriftWrokerThreadNum;
    }

    public boolean isLocalParquetStorage() {
        return isLocalParquetStorage;
    }

    public void setLocalParquetStorage(boolean localParquetStorage) {
        isLocalParquetStorage = localParquetStorage;
    }
}
