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
package cn.edu.tsinghua.iginx.metadata.storage.etcd;

import static cn.edu.tsinghua.iginx.metadata.utils.ReshardStatus.*;

import cn.edu.tsinghua.iginx.conf.ConfigDescriptor;
import cn.edu.tsinghua.iginx.exceptions.MetaStorageException;
import cn.edu.tsinghua.iginx.metadata.cache.IMetaCache;
import cn.edu.tsinghua.iginx.metadata.entity.*;
import cn.edu.tsinghua.iginx.metadata.hook.*;
import cn.edu.tsinghua.iginx.metadata.storage.IMetaStorage;
import cn.edu.tsinghua.iginx.metadata.utils.ReshardStatus;
import cn.edu.tsinghua.iginx.utils.JsonUtils;
import cn.edu.tsinghua.iginx.utils.Pair;
import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ETCDMetaStorage implements IMetaStorage {

    private static final Logger logger = LoggerFactory.getLogger(ETCDMetaStorage.class);

    private static final String IGINX_ID = "/id/iginx/";

    private static final String STORAGE_ID = "/id/storage/";

    private static final String STORAGE_UNIT_ID = "/id/storage_unit/";

    private static final String STORAGE_LOCK = "/lock/storage/";

    private static final String STORAGE_UNIT_LOCK = "/lock/storage_unit/";

    private static final String FRAGMENT_LOCK = "/lock/fragment/";

    private static final String USER_LOCK = "/lock/user/";

    private static final String TRANSFORM_LOCK = "/lock/transform/";

    private static final String RESHARD_STATUS_LOCK_NODE = "/lock/status/reshard";

    private static final String RESHARD_COUNTER_LOCK_NODE = "/lock/counter/reshard";

    private static final String ACTIVE_END_TIME_COUNTER_LOCK_NODE =
            "/lock/counter/end/time/active/max";

    private static final String LATENCY_COUNTER_LOCK_NODE = "/lock/counter/latency";

    private static final String FRAGMENT_HEAT_COUNTER_LOCK_NODE = "/lock/counter/fragment/heat";

    private static final String FRAGMENT_REQUESTS_COUNTER_LOCK_NODE =
            "/lock/counter/fragment/requests";

    private static final String TIMESERIES_HEAT_COUNTER_LOCK_NODE = "/lock/counter/timeseries/heat";

    private static final String SCHEMA_MAPPING_PREFIX = "/schema/";

    private static final String IGINX_PREFIX = "/iginx/";

    private static final String STORAGE_PREFIX = "/storage/";

    private static final String STORAGE_UNIT_PREFIX = "/storage_unit/";

    private static final String FRAGMENT_PREFIX = "/fragment/";

    private static final String USER_PREFIX = "/user/";

    private static final String STATISTICS_FRAGMENT_POINTS_PREFIX = "/statistics/fragment/points";

    private static final String STATISTICS_FRAGMENT_REQUESTS_PREFIX_WRITE =
            "/statistics/fragment/requests/write";

    private static final String STATISTICS_FRAGMENT_REQUESTS_PREFIX_READ =
            "/statistics/fragment/requests/read";

    private static final String STATISTICS_FRAGMENT_REQUESTS_COUNTER_PREFIX =
            "/statistics/fragment/requests/counter";

    private static final String STATISTICS_FRAGMENT_HEAT_PREFIX_WRITE =
            "/statistics/fragment/heat/write";

    private static final String STATISTICS_FRAGMENT_HEAT_PREFIX_READ =
            "/statistics/fragment/heat/read";

    private static final String STATISTICS_FRAGMENT_HEAT_COUNTER_PREFIX =
            "/statistics/fragment/heat/counter";

    private static final String STATISTICS_TIMESERIES_HEAT_PREFIX = "/statistics/timeseries/heat";

    private static final String STATISTICS_TIMESERIES_HEAT_COUNTER_PREFIX =
            "/statistics/timeseries/heat/counter";

    private static final String MAX_ACTIVE_END_TIME_STATISTICS_NODE =
            "/statistics/end/time/active/max/node";

    private static final String MAX_ACTIVE_END_TIME_STATISTICS_NODE_PREFIX =
            "/statistics/end/time/active/max";

    private static final String RESHARD_STATUS_NODE_PREFIX = "/status/reshard";

    private static final String RESHARD_COUNTER_NODE_PREFIX = "/counter/reshard";

    private static final String TIMESERIES_NODE_PREFIX = "/timeseries";

    private static final String TRANSFORM_PREFIX = "/transform/";

    private static final long MAX_LOCK_TIME = 30; // 最长锁住 30 秒

    private static final long HEART_BEAT_INTERVAL = 5; // 和 etcd 之间的心跳包的时间间隔

    private static ETCDMetaStorage INSTANCE = null;

    private final Lock storageLeaseLock = new ReentrantLock();
    private final Lock storageUnitLeaseLock = new ReentrantLock();
    private final Lock fragmentLeaseLock = new ReentrantLock();
    private final Lock userLeaseLock = new ReentrantLock();
    private final Lock transformLeaseLock = new ReentrantLock();
    private final Lock fragmentRequestsCounterLeaseLock = new ReentrantLock();
    private final Lock fragmentHeatCounterLeaseLock = new ReentrantLock();
    private final Lock timeseriesHeatCounterLeaseLock = new ReentrantLock();
    private final Lock reshardStatusLeaseLock = new ReentrantLock();
    private final Lock reshardCounterLeaseLock = new ReentrantLock();
    private final Lock maxActiveEndTimeStatisticsLeaseLock = new ReentrantLock();

    private Client client;

    private Watch.Watcher schemaMappingWatcher;
    private SchemaMappingChangeHook schemaMappingChangeHook = null;
    private Watch.Watcher iginxWatcher;
    private IginxChangeHook iginxChangeHook = null;
    private Watch.Watcher storageWatcher;
    private StorageChangeHook storageChangeHook = null;
    private long storageLease = -1L;
    private Watch.Watcher storageUnitWatcher;
    private StorageUnitChangeHook storageUnitChangeHook = null;
    private long storageUnitLease = -1L;
    private Watch.Watcher fragmentWatcher;
    private FragmentChangeHook fragmentChangeHook = null;
    private long fragmentLease = -1L;
    private Watch.Watcher userWatcher;
    private UserChangeHook userChangeHook = null;
    private long userLease = -1L;
    private Watch.Watcher transformWatcher;
    private TransformChangeHook transformChangeHook = null;
    private long transformLease = -1L;

    private long fragmentRequestsCounterLease = -1L;

    private long fragmentHeatCounterLease = -1L;

    private long timeseriesHeatCounterLease = -1L;

    private Watch.Watcher reshardStatusWatcher;
    private ReshardStatusChangeHook reshardStatusChangeHook = null;
    private long reshardStatusLease = -1L;
    private Watch.Watcher reshardCounterWatcher;
    private ReshardCounterChangeHook reshardCounterChangeHook = null;
    private long reshardCounterLease = -1L;
    private Watch.Watcher maxActiveEndTimeStatisticsWatcher;
    private MaxActiveEndTimeStatisticsChangeHook maxActiveEndTimeStatisticsChangeHook = null;
    private long maxActiveEndTimeStatisticsLease = -1L;

    private final int IGINX_NODE_LENGTH = 7;

    private final int STORAGE_ENGINE_NODE_LENGTH = 6;

    private String generateID(String prefix, long idLength, long val) {
        return String.format(prefix + "%0" + idLength + "d", (int) val);
    }

    public ETCDMetaStorage() {
        client =
                Client.builder()
                        .endpoints(
                                ConfigDescriptor.getInstance()
                                        .getConfig()
                                        .getEtcdEndpoints()
                                        .split(","))
                        .build();

        // 注册 schema mapping 的监听
        this.schemaMappingWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(SCHEMA_MAPPING_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(
                                                ByteSequence.from(SCHEMA_MAPPING_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.schemaMappingChangeHook == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            String schema =
                                                    event.getKeyValue()
                                                            .getKey()
                                                            .toString(StandardCharsets.UTF_8)
                                                            .substring(
                                                                    SCHEMA_MAPPING_PREFIX.length());
                                            Map<String, Integer> schemaMapping = null;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    schemaMapping =
                                                            JsonUtils.transform(
                                                                    new String(
                                                                            event.getKeyValue()
                                                                                    .getValue()
                                                                                    .getBytes()));
                                                    break;
                                                case DELETE:
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                            ETCDMetaStorage.this.schemaMappingChangeHook.onChange(
                                                    schema, schemaMapping);
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 iginx 的监听
        this.iginxWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(IGINX_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(ByteSequence.from(IGINX_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.iginxChangeHook == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            IginxMeta iginx;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    iginx =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    IginxMeta.class);
                                                    logger.info(
                                                            "new iginx comes to cluster: id = "
                                                                    + iginx.getId()
                                                                    + " ,ip = "
                                                                    + iginx.getIp()
                                                                    + " , port = "
                                                                    + iginx.getPort());
                                                    ETCDMetaStorage.this.iginxChangeHook.onChange(
                                                            iginx.getId(), iginx);
                                                    break;
                                                case DELETE:
                                                    iginx =
                                                            JsonUtils.fromJson(
                                                                    event.getPrevKV()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    IginxMeta.class);
                                                    logger.info(
                                                            "iginx leave from cluster: id = "
                                                                    + iginx.getId()
                                                                    + " ,ip = "
                                                                    + iginx.getIp()
                                                                    + " , port = "
                                                                    + iginx.getPort());
                                                    iginxChangeHook.onChange(iginx.getId(), null);
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 storage 的监听
        this.storageWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(STORAGE_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(ByteSequence.from(STORAGE_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.storageWatcher == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            StorageEngineMeta storageEngine;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    storageEngine =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    StorageEngineMeta.class);
                                                    storageChangeHook.onChange(
                                                            storageEngine.getId(), storageEngine);
                                                    break;
                                                case DELETE:
                                                    storageEngine =
                                                            JsonUtils.fromJson(
                                                                    event.getPrevKV()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    StorageEngineMeta.class);
                                                    storageChangeHook.onChange(
                                                            storageEngine.getId(), null);
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 storage unit 的监听
        this.storageUnitWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(STORAGE_UNIT_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(
                                                ByteSequence.from(STORAGE_UNIT_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.storageUnitWatcher == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            StorageUnitMeta storageUnit;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    storageUnit =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    StorageUnitMeta.class);
                                                    storageUnitChangeHook.onChange(
                                                            storageUnit.getId(), storageUnit);
                                                    break;
                                                case DELETE:
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 fragment 的监听
        this.fragmentWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(FRAGMENT_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(ByteSequence.from(FRAGMENT_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.fragmentChangeHook == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            FragmentMeta fragment;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    fragment =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    FragmentMeta.class);
                                                    boolean isCreate =
                                                            event.getPrevKV().getVersion()
                                                                    == 0; // 上一次如果是 0，意味着就是创建
                                                    fragmentChangeHook.onChange(isCreate, fragment);
                                                    break;
                                                case DELETE:
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 user 的监听
        this.userWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(USER_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(ByteSequence.from(USER_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.userChangeHook == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            UserMeta userMeta;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    userMeta =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    UserMeta.class);
                                                    userChangeHook.onChange(
                                                            userMeta.getUsername(), userMeta);
                                                    break;
                                                case DELETE:
                                                    userMeta =
                                                            JsonUtils.fromJson(
                                                                    event.getPrevKV()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    UserMeta.class);
                                                    userChangeHook.onChange(
                                                            userMeta.getUsername(), null);
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 transform 的监听
        this.transformWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(TRANSFORM_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(ByteSequence.from(TRANSFORM_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.transformChangeHook == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            TransformTaskMeta taskMeta;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    taskMeta =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    TransformTaskMeta.class);
                                                    transformChangeHook.onChange(
                                                            taskMeta.getName(), taskMeta);
                                                    break;
                                                case DELETE:
                                                    taskMeta =
                                                            JsonUtils.fromJson(
                                                                    event.getPrevKV()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    TransformTaskMeta.class);
                                                    transformChangeHook.onChange(
                                                            taskMeta.getName(), null);
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 reshardStatus 的监听
        this.reshardStatusWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(RESHARD_STATUS_NODE_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(
                                                ByteSequence.from(
                                                        RESHARD_STATUS_NODE_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.reshardStatusChangeHook == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            ReshardStatus status;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    status =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    ReshardStatus.class);
                                                    reshardStatusChangeHook.onChange(status);
                                                    break;
                                                case DELETE:
                                                    status =
                                                            JsonUtils.fromJson(
                                                                    event.getPrevKV()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    ReshardStatus.class);
                                                    reshardStatusChangeHook.onChange(status);
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 reshardCounter 的监听
        this.reshardCounterWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(RESHARD_COUNTER_NODE_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(
                                                ByteSequence.from(
                                                        RESHARD_COUNTER_NODE_PREFIX.getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this.reshardCounterChangeHook == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            int counter;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    counter =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    Integer.class);
                                                    reshardCounterChangeHook.onChange(counter);
                                                    break;
                                                case DELETE:
                                                    counter =
                                                            JsonUtils.fromJson(
                                                                    event.getPrevKV()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    Integer.class);
                                                    reshardCounterChangeHook.onChange(counter);
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });

        // 注册 maxActiveEndTimeStatistics 的监听
        this.maxActiveEndTimeStatisticsWatcher =
                client.getWatchClient()
                        .watch(
                                ByteSequence.from(
                                        MAX_ACTIVE_END_TIME_STATISTICS_NODE_PREFIX.getBytes()),
                                WatchOption.newBuilder()
                                        .withPrefix(
                                                ByteSequence.from(
                                                        MAX_ACTIVE_END_TIME_STATISTICS_NODE_PREFIX
                                                                .getBytes()))
                                        .withPrevKV(true)
                                        .build(),
                                new Watch.Listener() {
                                    @Override
                                    public void onNext(WatchResponse watchResponse) {
                                        if (ETCDMetaStorage.this
                                                        .maxActiveEndTimeStatisticsChangeHook
                                                == null) {
                                            return;
                                        }
                                        for (WatchEvent event : watchResponse.getEvents()) {
                                            long endTime;
                                            switch (event.getEventType()) {
                                                case PUT:
                                                    endTime =
                                                            JsonUtils.fromJson(
                                                                    event.getKeyValue()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    Long.class);
                                                    maxActiveEndTimeStatisticsChangeHook.onChange(
                                                            endTime);
                                                    break;
                                                case DELETE:
                                                    endTime =
                                                            JsonUtils.fromJson(
                                                                    event.getPrevKV()
                                                                            .getValue()
                                                                            .getBytes(),
                                                                    Long.class);
                                                    maxActiveEndTimeStatisticsChangeHook.onChange(
                                                            endTime);
                                                    break;
                                                default:
                                                    logger.error(
                                                            "unexpected watchEvent: "
                                                                    + event.getEventType());
                                                    break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {}

                                    @Override
                                    public void onCompleted() {}
                                });
    }

    public static ETCDMetaStorage getInstance() {
        if (INSTANCE == null) {
            synchronized (ETCDMetaStorage.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ETCDMetaStorage();
                }
            }
        }
        return INSTANCE;
    }

    private long nextId(String category) throws InterruptedException, ExecutionException {
        return client.getKVClient()
                .put(
                        ByteSequence.from(category.getBytes()),
                        ByteSequence.EMPTY,
                        PutOption.newBuilder().withPrevKV().build())
                .get()
                .getPrevKv()
                .getVersion();
    }

    @Override
    public Map<String, Map<String, Integer>> loadSchemaMapping() throws MetaStorageException {
        try {
            Map<String, Map<String, Integer>> schemaMappings = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(SCHEMA_MAPPING_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(
                                                            SCHEMA_MAPPING_PREFIX.getBytes()))
                                            .build())
                            .get();
            response.getKvs()
                    .forEach(
                            e -> {
                                String schema =
                                        e.getKey()
                                                .toString(StandardCharsets.UTF_8)
                                                .substring(SCHEMA_MAPPING_PREFIX.length());
                                Map<String, Integer> schemaMapping =
                                        JsonUtils.transform(
                                                e.getValue().toString(StandardCharsets.UTF_8));
                                schemaMappings.put(schema, schemaMapping);
                            });
            return schemaMappings;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when load schema mapping: ", e);
            throw new MetaStorageException(e);
        }
    }

    @Override
    public void registerSchemaMappingChangeHook(SchemaMappingChangeHook hook) {
        this.schemaMappingChangeHook = hook;
    }

    @Override
    public void updateSchemaMapping(String schema, Map<String, Integer> schemaMapping)
            throws MetaStorageException {
        try {
            if (schemaMapping == null) {
                this.client
                        .getKVClient()
                        .delete(ByteSequence.from((SCHEMA_MAPPING_PREFIX + schema).getBytes()))
                        .get();
            } else {
                this.client
                        .getKVClient()
                        .put(
                                ByteSequence.from((SCHEMA_MAPPING_PREFIX + schema).getBytes()),
                                ByteSequence.from(JsonUtils.toJson(schemaMapping)))
                        .get();
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when update schema mapping: ", e);
            throw new MetaStorageException(e);
        }
    }

    @Override
    public Map<Long, IginxMeta> loadIginx() throws MetaStorageException {
        try {
            Map<Long, IginxMeta> iginxMap = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(IGINX_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(ByteSequence.from(IGINX_PREFIX.getBytes()))
                                            .build())
                            .get();
            response.getKvs()
                    .forEach(
                            e -> {
                                String info = new String(e.getValue().getBytes());
                                System.out.println(info);
                                IginxMeta iginx =
                                        JsonUtils.fromJson(
                                                e.getValue().getBytes(), IginxMeta.class);
                                iginxMap.put(iginx.getId(), iginx);
                            });
            return iginxMap;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when load schema mapping: ", e);
            throw new MetaStorageException(e);
        }
    }

    @Override
    public long registerIginx(IginxMeta iginx) throws MetaStorageException {
        try {
            // 申请一个 id
            long id = nextId(IGINX_ID);
            Lease lease = this.client.getLeaseClient();
            long iginxLeaseId = lease.grant(HEART_BEAT_INTERVAL).get().getID();
            lease.keepAlive(
                    iginxLeaseId,
                    new StreamObserver<LeaseKeepAliveResponse>() {
                        @Override
                        public void onNext(LeaseKeepAliveResponse leaseKeepAliveResponse) {
                            logger.info("send heart beat to etcd succeed.");
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            logger.error("got error when send heart beat to etcd: ", throwable);
                        }

                        @Override
                        public void onCompleted() {}
                    });
            iginx = new IginxMeta(id, iginx.getIp(), iginx.getPort(), iginx.getExtraParams());
            this.client
                    .getKVClient()
                    .put(
                            ByteSequence.from(
                                    generateID(IGINX_PREFIX, IGINX_NODE_LENGTH, id).getBytes()),
                            ByteSequence.from(JsonUtils.toJson(iginx)))
                    .get();
            return id;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when register iginx meta: ", e);
            throw new MetaStorageException(e);
        }
    }

    @Override
    public void registerIginxChangeHook(IginxChangeHook hook) {
        this.iginxChangeHook = hook;
    }

    private void lockStorage() throws MetaStorageException {
        try {
            storageLeaseLock.lock();
            storageLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient().lock(ByteSequence.from(STORAGE_LOCK.getBytes()), storageLease);
        } catch (Exception e) {
            storageLeaseLock.unlock();
            throw new MetaStorageException("acquire storage mutex error: ", e);
        }
    }

    private void releaseStorage() throws MetaStorageException {
        try {
            client.getLockClient().unlock(ByteSequence.from(STORAGE_LOCK.getBytes())).get();
            client.getLeaseClient().revoke(storageLease).get();
            storageLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release storage error: ", e);
        } finally {
            storageLeaseLock.unlock();
        }
    }

    @Override
    public Map<Long, StorageEngineMeta> loadStorageEngine(
            List<StorageEngineMeta> localStorageEngines) throws MetaStorageException {
        try {
            lockStorage();
            Map<Long, StorageEngineMeta> storageEngines = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(STORAGE_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(STORAGE_PREFIX.getBytes()))
                                            .build())
                            .get();
            if (response.getCount() != 0L) { // 服务器上已经有了，本地的不作数
                response.getKvs()
                        .forEach(
                                e -> {
                                    StorageEngineMeta storageEngine =
                                            JsonUtils.fromJson(
                                                    e.getValue().getBytes(),
                                                    StorageEngineMeta.class);
                                    storageEngines.put(storageEngine.getId(), storageEngine);
                                });
            } else { // 服务器上还没有，将本地的注册到服务器上
                for (StorageEngineMeta storageEngine : localStorageEngines) {
                    long id = nextId(STORAGE_ID); // 给每个数据后端分配 id
                    storageEngine.setId(id);
                    storageEngines.put(storageEngine.getId(), storageEngine);
                    this.client
                            .getKVClient()
                            .put(
                                    ByteSequence.from(
                                            generateID(
                                                            STORAGE_PREFIX,
                                                            STORAGE_ENGINE_NODE_LENGTH,
                                                            id)
                                                    .getBytes()),
                                    ByteSequence.from(JsonUtils.toJson(storageEngine)))
                            .get();
                }
            }
            return storageEngines;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when load storage: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (storageLease != -1) {
                releaseStorage();
            }
        }
    }

    @Override
    public long addStorageEngine(StorageEngineMeta storageEngine) throws MetaStorageException {
        try {
            lockStorage();
            long id = nextId(STORAGE_ID);
            storageEngine.setId(id);
            this.client
                    .getKVClient()
                    .put(
                            ByteSequence.from(
                                    generateID(STORAGE_PREFIX, STORAGE_ENGINE_NODE_LENGTH, id)
                                            .getBytes()),
                            ByteSequence.from(JsonUtils.toJson(storageEngine)))
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when add storage: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (storageLease != -1) {
                releaseStorage();
            }
        }
        return 0L;
    }

    @Override
    public boolean updateStorageEngine(long storageID, StorageEngineMeta storageEngine)
            throws MetaStorageException {
        try {
            lockStorage();
            this.client
                    .getKVClient()
                    .put(
                            ByteSequence.from(
                                    generateID(
                                                    STORAGE_PREFIX,
                                                    STORAGE_ENGINE_NODE_LENGTH,
                                                    storageID)
                                            .getBytes()),
                            ByteSequence.from(JsonUtils.toJson(storageEngine)))
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when add storage: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (storageLease != -1) {
                releaseStorage();
            }
        }
        return true;
    }

    @Override
    public void registerStorageChangeHook(StorageChangeHook hook) {
        this.storageChangeHook = hook;
    }

    @Override
    public Map<String, StorageUnitMeta> loadStorageUnit() throws MetaStorageException {
        try {
            Map<String, StorageUnitMeta> storageUnitMap = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(STORAGE_UNIT_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(
                                                            STORAGE_UNIT_PREFIX.getBytes()))
                                            .build())
                            .get();
            List<KeyValue> kvs = response.getKvs();
            kvs.sort(Comparator.comparing(e -> e.getKey().toString(StandardCharsets.UTF_8)));
            for (KeyValue kv : kvs) {
                StorageUnitMeta storageUnit =
                        JsonUtils.fromJson(kv.getValue().getBytes(), StorageUnitMeta.class);
                if (!storageUnit.isMaster()) { // 需要加入到主节点的子节点列表中
                    StorageUnitMeta masterStorageUnit =
                            storageUnitMap.get(storageUnit.getMasterId());
                    if (masterStorageUnit == null) { // 子节点先于主节点加入系统中，不应该发生，报错
                        logger.error(
                                "unexpected storage unit "
                                        + new String(kv.getValue().getBytes())
                                        + ", because it does not has a master storage unit");
                    } else {
                        masterStorageUnit.addReplica(storageUnit);
                    }
                }
                storageUnitMap.put(storageUnit.getId(), storageUnit);
            }
            return storageUnitMap;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when load storage unit: ", e);
            throw new MetaStorageException(e);
        }
    }

    @Override
    public void lockStorageUnit() throws MetaStorageException {
        try {
            storageUnitLeaseLock.lock();
            storageUnitLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(ByteSequence.from(STORAGE_UNIT_LOCK.getBytes()), storageUnitLease)
                    .get()
                    .getKey();
        } catch (Exception e) {
            storageUnitLeaseLock.unlock();
            throw new MetaStorageException("acquire storage unit mutex error: ", e);
        }
    }

    @Override
    public String addStorageUnit() throws MetaStorageException {
        try {
            return generateID("unit", STORAGE_ENGINE_NODE_LENGTH, nextId(STORAGE_UNIT_ID));
        } catch (InterruptedException | ExecutionException e) {
            throw new MetaStorageException("add storage unit error: ", e);
        }
    }

    @Override
    public void updateStorageUnit(StorageUnitMeta storageUnitMeta) throws MetaStorageException {
        try {
            client.getKVClient()
                    .put(
                            ByteSequence.from(
                                    (STORAGE_UNIT_PREFIX + storageUnitMeta.getId()).getBytes()),
                            ByteSequence.from(JsonUtils.toJson(storageUnitMeta)))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new MetaStorageException("update storage unit error: ", e);
        }
    }

    @Override
    public void releaseStorageUnit() throws MetaStorageException {
        try {
            client.getLockClient().unlock(ByteSequence.from(STORAGE_UNIT_LOCK.getBytes())).get();
            client.getLeaseClient().revoke(storageUnitLease).get();
            storageUnitLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release storage mutex error: ", e);
        } finally {
            storageUnitLeaseLock.unlock();
        }
    }

    @Override
    public void registerStorageUnitChangeHook(StorageUnitChangeHook hook) {
        this.storageUnitChangeHook = hook;
    }

    @Override
    public Map<TimeSeriesRange, List<FragmentMeta>> loadFragment() throws MetaStorageException {
        try {
            Map<TimeSeriesRange, List<FragmentMeta>> fragmentsMap = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(FRAGMENT_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(FRAGMENT_PREFIX.getBytes()))
                                            .build())
                            .get();
            for (KeyValue kv : response.getKvs()) {
                FragmentMeta fragment =
                        JsonUtils.fromJson(kv.getValue().getBytes(), FragmentMeta.class);
                fragmentsMap
                        .computeIfAbsent(fragment.getTsInterval(), e -> new ArrayList<>())
                        .add(fragment);
            }
            return fragmentsMap;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when load fragments: ", e);
            throw new MetaStorageException(e);
        }
    }

    @Override
    public void lockFragment() throws MetaStorageException {
        try {
            fragmentLeaseLock.lock();
            fragmentLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient().lock(ByteSequence.from(FRAGMENT_LOCK.getBytes()), fragmentLease);
        } catch (Exception e) {
            fragmentLeaseLock.unlock();
            throw new MetaStorageException("acquire fragment mutex error: ", e);
        }
    }

    @Override
    public List<FragmentMeta> getFragmentListByTimeSeriesNameAndTimeInterval(
            String tsName, TimeInterval timeInterval) {
        try {
            List<FragmentMeta> fragments = new ArrayList<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(FRAGMENT_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(FRAGMENT_PREFIX.getBytes()))
                                            .build())
                            .get();
            for (KeyValue kv : response.getKvs()) {
                FragmentMeta fragment =
                        JsonUtils.fromJson(kv.getValue().getBytes(), FragmentMeta.class);
                if (fragment.getTimeInterval().isIntersect(timeInterval)
                        && fragment.getTsInterval().isContain(tsName)) {
                    fragments.add(fragment);
                }
            }
            fragments.sort(
                    (o1, o2) -> {
                        long s1 = o1.getTimeInterval().getStartTime();
                        long s2 = o2.getTimeInterval().getStartTime();
                        return Long.compare(s2, s1);
                    });
            return fragments;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when get fragments by tsName and timeInterval: ", e);
        }
        return new ArrayList<>();
    }

    @Override
    public Map<TimeSeriesRange, List<FragmentMeta>>
            getFragmentMapByTimeSeriesIntervalAndTimeInterval(
                    TimeSeriesRange tsInterval, TimeInterval timeInterval) {
        try {
            Map<TimeSeriesRange, List<FragmentMeta>> fragmentsMap = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(FRAGMENT_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(FRAGMENT_PREFIX.getBytes()))
                                            .build())
                            .get();
            for (KeyValue kv : response.getKvs()) {
                FragmentMeta fragment =
                        JsonUtils.fromJson(kv.getValue().getBytes(), FragmentMeta.class);
                if (fragment.getTimeInterval().isIntersect(timeInterval)
                        && fragment.getTsInterval().isIntersect(tsInterval)) {
                    fragmentsMap
                            .computeIfAbsent(fragment.getTsInterval(), e -> new ArrayList<>())
                            .add(fragment);
                }
            }
            fragmentsMap
                    .values()
                    .forEach(
                            e ->
                                    e.sort(
                                            (o1, o2) -> {
                                                long s1 = o1.getTimeInterval().getStartTime();
                                                long s2 = o2.getTimeInterval().getStartTime();
                                                return Long.compare(s1, s2);
                                            }));
            return fragmentsMap;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when get fragments by tsName and timeInterval: ", e);
        }
        return new HashMap<>();
    }

    @Override
    public void updateFragment(FragmentMeta fragmentMeta) throws MetaStorageException {
        try {
            client.getKVClient()
                    .put(
                            ByteSequence.from(
                                    (FRAGMENT_PREFIX
                                                    + fragmentMeta.getTsInterval().toString()
                                                    + "/"
                                                    + fragmentMeta.getTimeInterval().toString())
                                            .getBytes()),
                            ByteSequence.from(JsonUtils.toJson(fragmentMeta)))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new MetaStorageException("update storage unit error: ", e);
        }
    }

    @Override
    public void updateFragmentByTsInterval(TimeSeriesRange tsInterval, FragmentMeta fragmentMeta)
            throws MetaStorageException {
        try {
            client.getKVClient()
                    .delete(
                            ByteSequence.from(
                                    (FRAGMENT_PREFIX
                                                    + tsInterval.toString()
                                                    + "/"
                                                    + fragmentMeta.getTimeInterval().toString())
                                            .getBytes()));
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(
                                            (FRAGMENT_PREFIX + tsInterval.toString()).getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(
                                                            (FRAGMENT_PREFIX
                                                                            + tsInterval.toString())
                                                                    .getBytes()))
                                            .build())
                            .get();
            if (response.getKvs().isEmpty()) {
                client.getKVClient()
                        .delete(
                                ByteSequence.from(
                                        (FRAGMENT_PREFIX + tsInterval.toString()).getBytes()));
            }
            client.getKVClient()
                    .put(
                            ByteSequence.from(
                                    (FRAGMENT_PREFIX
                                                    + fragmentMeta.getTsInterval().toString()
                                                    + "/"
                                                    + fragmentMeta.getTimeInterval().toString())
                                            .getBytes()),
                            ByteSequence.from(JsonUtils.toJson(fragmentMeta)))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new MetaStorageException("update storage unit error: ", e);
        }
    }

    @Override
    public void removeFragment(FragmentMeta fragmentMeta) throws MetaStorageException {
        try {
            client.getKVClient()
                    .delete(
                            ByteSequence.from(
                                    (FRAGMENT_PREFIX
                                                    + fragmentMeta.getTsInterval().toString()
                                                    + "/"
                                                    + fragmentMeta.getTimeInterval().toString())
                                            .getBytes()));
            // 删除不需要的统计数据
            client.getKVClient()
                    .delete(
                            ByteSequence.from(
                                    (STATISTICS_FRAGMENT_REQUESTS_PREFIX_WRITE
                                                    + "/"
                                                    + fragmentMeta.getTsInterval().toString()
                                                    + "/"
                                                    + fragmentMeta.getTimeInterval().toString())
                                            .getBytes()));
            client.getKVClient()
                    .delete(
                            ByteSequence.from(
                                    (STATISTICS_FRAGMENT_REQUESTS_PREFIX_READ
                                                    + "/"
                                                    + fragmentMeta.getTsInterval().toString()
                                                    + "/"
                                                    + fragmentMeta.getTimeInterval().toString())
                                            .getBytes()));
            client.getKVClient()
                    .delete(
                            ByteSequence.from(
                                    (STATISTICS_FRAGMENT_POINTS_PREFIX
                                                    + "/"
                                                    + fragmentMeta.getTsInterval().toString()
                                                    + "/"
                                                    + fragmentMeta.getTimeInterval().toString())
                                            .getBytes()));
        } catch (Exception e) {
            throw new MetaStorageException("get error when remove fragment", e);
        }
    }

    @Override
    public void addFragment(FragmentMeta fragmentMeta) throws MetaStorageException {
        updateFragment(fragmentMeta);
    }

    @Override
    public void releaseFragment() throws MetaStorageException {
        try {
            client.getLockClient().unlock(ByteSequence.from(FRAGMENT_LOCK.getBytes())).get();
            client.getLeaseClient().revoke(fragmentLease).get();
            fragmentLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release fragment mutex error: ", e);
        } finally {
            fragmentLeaseLock.unlock();
        }
    }

    @Override
    public void registerFragmentChangeHook(FragmentChangeHook hook) {
        this.fragmentChangeHook = hook;
    }

    private void lockUser() throws MetaStorageException {
        try {
            userLeaseLock.lock();
            userLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient().lock(ByteSequence.from(USER_LOCK.getBytes()), userLease);
        } catch (Exception e) {
            userLeaseLock.unlock();
            throw new MetaStorageException("acquire user mutex error: ", e);
        }
    }

    private void releaseUser() throws MetaStorageException {
        try {
            client.getLockClient().unlock(ByteSequence.from(USER_LOCK.getBytes())).get();
            client.getLeaseClient().revoke(userLease).get();
            userLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release user mutex error: ", e);
        } finally {
            userLeaseLock.unlock();
        }
    }

    @Override
    public List<UserMeta> loadUser(UserMeta userMeta) throws MetaStorageException {
        try {
            lockUser();
            Map<String, UserMeta> users = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(USER_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(ByteSequence.from(USER_PREFIX.getBytes()))
                                            .build())
                            .get();
            if (response.getCount() != 0L) { // 服务器上已经有了，本地的不作数
                response.getKvs()
                        .forEach(
                                e -> {
                                    UserMeta user =
                                            JsonUtils.fromJson(
                                                    e.getValue().getBytes(), UserMeta.class);
                                    users.put(user.getUsername(), user);
                                });
            } else {
                addUser(userMeta);
                users.put(userMeta.getUsername(), userMeta);
            }
            return new ArrayList<>(users.values());
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when load user: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (userLease != -1) {
                releaseUser();
            }
        }
    }

    @Override
    public void registerUserChangeHook(UserChangeHook hook) {
        userChangeHook = hook;
    }

    @Override
    public void addUser(UserMeta userMeta) throws MetaStorageException {
        updateUser(userMeta);
    }

    @Override
    public void updateUser(UserMeta userMeta) throws MetaStorageException {
        try {
            lockUser();
            this.client
                    .getKVClient()
                    .put(
                            ByteSequence.from((USER_PREFIX + userMeta.getUsername()).getBytes()),
                            ByteSequence.from(JsonUtils.toJson(userMeta)))
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when add/update user: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (userLease != -1) {
                releaseUser();
            }
        }
        if (userChangeHook != null) {
            userChangeHook.onChange(userMeta.getUsername(), userMeta);
        }
    }

    @Override
    public void removeUser(String username) throws MetaStorageException {
        try {
            lockUser();
            this.client
                    .getKVClient()
                    .delete(ByteSequence.from((USER_PREFIX + username).getBytes()))
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when remove user: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (userLease != -1) {
                releaseUser();
            }
        }
        if (userChangeHook != null) {
            userChangeHook.onChange(username, null);
        }
    }

    @Override
    public void registerTimeseriesChangeHook(TimeSeriesChangeHook hook) {}

    @Override
    public void registerVersionChangeHook(VersionChangeHook hook) {}

    @Override
    public boolean election() {
        return false;
    }

    @Override
    public void updateTimeseriesData(Map<String, Double> timeseriesData, long iginxid, long version)
            throws Exception {}

    @Override
    public Map<String, Double> getTimeseriesData() {
        return null;
    }

    @Override
    public void registerPolicy(long iginxId, int num) throws Exception {}

    @Override
    public int updateVersion() {
        return 0;
    }

    @Override
    public void updateTimeseriesLoad(Map<String, Long> timeseriesLoadMap) throws Exception {
        for (Map.Entry<String, Long> timeseriesLoadEntry : timeseriesLoadMap.entrySet()) {
            String path = STATISTICS_TIMESERIES_HEAT_PREFIX + "/" + timeseriesLoadEntry.getKey();
            if (this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(path.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(ByteSequence.from(path.getBytes()))
                                            .build())
                            .get()
                    == null) {
                this.client
                        .getKVClient()
                        .put(
                                ByteSequence.from(path.getBytes()),
                                ByteSequence.from(JsonUtils.toJson(timeseriesLoadEntry.getValue())))
                        .get();
            }
        }
        Map<String, Long> currentTimeseriesLoadMap = loadTimeseriesHeat();
        for (Map.Entry<String, Long> timeseriesLoadEntry : timeseriesLoadMap.entrySet()) {
            String path = STATISTICS_TIMESERIES_HEAT_PREFIX + "/" + timeseriesLoadEntry.getKey();
            this.client
                    .getKVClient()
                    .put(
                            ByteSequence.from(path.getBytes()),
                            ByteSequence.from(
                                    JsonUtils.toJson(
                                            timeseriesLoadEntry.getValue()
                                                    + currentTimeseriesLoadMap.getOrDefault(
                                                            timeseriesLoadEntry.getKey(), 0L))))
                    .get();
        }
    }

    @Override
    public Map<String, Long> loadTimeseriesHeat() throws MetaStorageException, Exception {
        Map<String, Long> timeseriesHeatMap = new HashMap<>();
        GetResponse response =
                this.client
                        .getKVClient()
                        .get(
                                ByteSequence.from(STATISTICS_TIMESERIES_HEAT_PREFIX.getBytes()),
                                GetOption.newBuilder()
                                        .withPrefix(
                                                ByteSequence.from(
                                                        STATISTICS_TIMESERIES_HEAT_PREFIX
                                                                .getBytes()))
                                        .build())
                        .get();
        for (KeyValue kv : response.getKvs()) {
            byte[] data = JsonUtils.fromJson(kv.getValue().getBytes(), byte[].class);
            long heat = JsonUtils.fromJson(data, Long.class);
            timeseriesHeatMap.put(kv.getKey().toString(), heat);
        }
        return timeseriesHeatMap;
    }

    @Override
    public void removeTimeseriesHeat() throws MetaStorageException {
        try {
            client.getKVClient()
                    .delete(ByteSequence.from(STATISTICS_TIMESERIES_HEAT_PREFIX.getBytes()));
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when removing timeseries heat: ", e);
        }
    }

    @Override
    public void lockTimeseriesHeatCounter() throws MetaStorageException {
        try {
            timeseriesHeatCounterLeaseLock.lock();
            timeseriesHeatCounterLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(
                            ByteSequence.from(TIMESERIES_HEAT_COUNTER_LOCK_NODE.getBytes()),
                            timeseriesHeatCounterLease);
        } catch (Exception e) {
            timeseriesHeatCounterLeaseLock.unlock();
            throw new MetaStorageException("acquire fragment mutex error: ", e);
        }
    }

    @Override
    public void incrementTimeseriesHeatCounter() throws MetaStorageException {
        try {
            if (client.getKVClient()
                            .get(
                                    ByteSequence.from(
                                            STATISTICS_TIMESERIES_HEAT_COUNTER_PREFIX.getBytes()))
                            .get()
                    == null) {
                client.getKVClient()
                        .put(
                                ByteSequence.from(
                                        STATISTICS_TIMESERIES_HEAT_COUNTER_PREFIX.getBytes()),
                                ByteSequence.from(JsonUtils.toJson(1)))
                        .get();
            } else {
                client.getKVClient()
                        .put(
                                ByteSequence.from(
                                        STATISTICS_TIMESERIES_HEAT_COUNTER_PREFIX.getBytes()),
                                ByteSequence.from(JsonUtils.toJson(1 + getTimeseriesHeatCounter())))
                        .get();
            }
        } catch (Exception e) {
            throw new MetaStorageException(
                    "encounter error when updating timeseries heat counter: ", e);
        }
    }

    @Override
    public void resetTimeseriesHeatCounter() throws MetaStorageException {
        try {
            client.getKVClient()
                    .put(
                            ByteSequence.from(STATISTICS_TIMESERIES_HEAT_COUNTER_PREFIX.getBytes()),
                            ByteSequence.from(JsonUtils.toJson(0)))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException(
                    "encounter error when resetting timeseries heat counter: ", e);
        }
    }

    @Override
    public void releaseTimeseriesHeatCounter() throws MetaStorageException {
        try {
            client.getLockClient()
                    .unlock(ByteSequence.from(TIMESERIES_HEAT_COUNTER_LOCK_NODE.getBytes()))
                    .get();
            client.getLeaseClient().revoke(timeseriesHeatCounterLease).get();
            timeseriesHeatCounterLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release fragment mutex error: ", e);
        } finally {
            timeseriesHeatCounterLeaseLock.unlock();
        }
    }

    @Override
    public int getTimeseriesHeatCounter() throws MetaStorageException {
        try {
            String[] tuples = STATISTICS_TIMESERIES_HEAT_COUNTER_PREFIX.split("/");
            String lastTuple = tuples[tuples.length - 1];
            StringBuilder newPrefix = new StringBuilder();
            for (int i = 0; i < tuples.length - 1; i++) {
                newPrefix.append(tuples[i]);
            }
            GetResponse response =
                    client.getKVClient()
                            .get(ByteSequence.from(newPrefix.toString().getBytes()))
                            .get();
            if (!response.getKvs().isEmpty()) {
                for (KeyValue kv : response.getKvs()) {
                    if (kv.getKey().toString().equals(lastTuple)) {
                        byte[] data = JsonUtils.fromJson(kv.getValue().getBytes(), byte[].class);
                        return JsonUtils.fromJson(data, Integer.class);
                    }
                }
            }
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when get timeseries heat counter: ", e);
        }
        return 0;
    }

    @Override
    public void updateFragmentRequests(
            Map<FragmentMeta, Long> writeRequestsMap, Map<FragmentMeta, Long> readRequestsMap)
            throws Exception {
        for (Map.Entry<FragmentMeta, Long> writeRequestsEntry : writeRequestsMap.entrySet()) {
            if (writeRequestsEntry.getValue() > 0) {
                String requestsPath =
                        STATISTICS_FRAGMENT_REQUESTS_PREFIX_WRITE
                                + "/"
                                + writeRequestsEntry.getKey().getTsInterval().toString()
                                + "/"
                                + writeRequestsEntry.getKey().getTimeInterval().toString();
                String pointsPath =
                        STATISTICS_FRAGMENT_POINTS_PREFIX
                                + "/"
                                + writeRequestsEntry.getKey().getTsInterval().toString()
                                + "/"
                                + writeRequestsEntry.getKey().getTimeInterval().toString();
                GetResponse response =
                        client.getKVClient().get(ByteSequence.from(requestsPath.getBytes())).get();
                if (response == null || response.getCount() <= 0) {
                    client.getKVClient()
                            .put(
                                    ByteSequence.from(requestsPath.getBytes()),
                                    ByteSequence.from(
                                            JsonUtils.toJson(writeRequestsEntry.getValue())));
                } else {
                    long requests =
                            JsonUtils.fromJson(
                                    response.getKvs().get(0).getValue().getBytes(), Long.class);
                    client.getKVClient()
                            .put(
                                    ByteSequence.from(requestsPath.getBytes()),
                                    ByteSequence.from(
                                            JsonUtils.toJson(
                                                    requests + writeRequestsEntry.getValue())));
                }

                response = client.getKVClient().get(ByteSequence.from(pointsPath.getBytes())).get();
                if (response == null || response.getCount() <= 0) {
                    client.getKVClient()
                            .put(
                                    ByteSequence.from(pointsPath.getBytes()),
                                    ByteSequence.from(
                                            JsonUtils.toJson(writeRequestsEntry.getValue())));
                } else {
                    long points =
                            JsonUtils.fromJson(
                                    response.getKvs().get(0).getValue().getBytes(), Long.class);
                    client.getKVClient()
                            .put(
                                    ByteSequence.from(pointsPath.getBytes()),
                                    ByteSequence.from(
                                            JsonUtils.toJson(
                                                    points + writeRequestsEntry.getValue())));
                }
            }
        }
        for (Map.Entry<FragmentMeta, Long> readRequestsEntry : readRequestsMap.entrySet()) {
            String path =
                    STATISTICS_FRAGMENT_REQUESTS_PREFIX_READ
                            + "/"
                            + readRequestsEntry.getKey().getTsInterval().toString()
                            + "/"
                            + readRequestsEntry.getKey().getTimeInterval().toString();
            GetResponse response =
                    client.getKVClient().get(ByteSequence.from(path.getBytes())).get();
            if (response == null || response.getCount() <= 0) {
                client.getKVClient()
                        .put(
                                ByteSequence.from(path.getBytes()),
                                ByteSequence.from(JsonUtils.toJson(readRequestsEntry.getValue())));
            } else {
                long requests =
                        JsonUtils.fromJson(
                                response.getKvs().get(0).getValue().getBytes(), Long.class);
                client.getKVClient()
                        .put(
                                ByteSequence.from(path.getBytes()),
                                ByteSequence.from(
                                        JsonUtils.toJson(requests + readRequestsEntry.getValue())));
            }
        }
    }

    @Override
    public void removeFragmentRequests() throws MetaStorageException {
        try {
            client.getKVClient()
                    .delete(ByteSequence.from(STATISTICS_FRAGMENT_REQUESTS_PREFIX_WRITE.getBytes()))
                    .get();
            client.getKVClient()
                    .delete(ByteSequence.from(STATISTICS_FRAGMENT_REQUESTS_PREFIX_READ.getBytes()))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when removing fragment requests: ", e);
        }
    }

    @Override
    public void lockFragmentRequestsCounter() throws MetaStorageException {
        try {
            fragmentRequestsCounterLeaseLock.lock();
            fragmentRequestsCounterLease =
                    client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(
                            ByteSequence.from(TIMESERIES_HEAT_COUNTER_LOCK_NODE.getBytes()),
                            fragmentRequestsCounterLease);
        } catch (Exception e) {
            fragmentRequestsCounterLeaseLock.unlock();
            throw new MetaStorageException(
                    "encounter error when acquiring fragment requests counter mutex: ", e);
        }
    }

    @Override
    public void incrementFragmentRequestsCounter() throws MetaStorageException {
        try {
            client.getKVClient()
                    .put(
                            ByteSequence.from(
                                    STATISTICS_FRAGMENT_REQUESTS_COUNTER_PREFIX.getBytes()),
                            ByteSequence.from(JsonUtils.toJson(1 + getFragmentRequestsCounter())))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException(
                    "encounter error when updating fragment requests counter: ", e);
        }
    }

    @Override
    public void resetFragmentRequestsCounter() throws MetaStorageException {
        try {
            client.getKVClient()
                    .put(
                            ByteSequence.from(
                                    STATISTICS_FRAGMENT_REQUESTS_COUNTER_PREFIX.getBytes()),
                            ByteSequence.from(JsonUtils.toJson(0)));
        } catch (Exception e) {
            throw new MetaStorageException(
                    "encounter error when resetting fragment requests counter: ", e);
        }
    }

    @Override
    public void releaseFragmentRequestsCounter() throws MetaStorageException {
        try {
            client.getLockClient()
                    .unlock(ByteSequence.from(FRAGMENT_REQUESTS_COUNTER_LOCK_NODE.getBytes()))
                    .get();
            client.getLeaseClient().revoke(timeseriesHeatCounterLease).get();
            fragmentRequestsCounterLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException(
                    "encounter error when resetting fragment requests counter: ", e);
        } finally {
            fragmentRequestsCounterLeaseLock.unlock();
        }
    }

    @Override
    public int getFragmentRequestsCounter() throws MetaStorageException {
        try {
            String[] tuples = STATISTICS_FRAGMENT_REQUESTS_COUNTER_PREFIX.split("/");
            String lastTuple = tuples[tuples.length - 1];
            StringBuilder newPrefix = new StringBuilder();
            for (int i = 0; i < tuples.length - 1; i++) {
                newPrefix.append(tuples[i]);
            }
            GetResponse response =
                    client.getKVClient()
                            .get(ByteSequence.from(newPrefix.toString().getBytes()))
                            .get();
            if (!response.getKvs().isEmpty()) {
                for (KeyValue kv : response.getKvs()) {
                    if (kv.getKey().toString().equals(lastTuple)) {
                        return JsonUtils.fromJson(kv.getValue().getBytes(), Integer.class);
                    }
                }
            }
        } catch (Exception e) {
            throw new MetaStorageException(
                    "encounter error when get fragment requests counter: ", e);
        }
        return 0;
    }

    @Override
    public Map<FragmentMeta, Long> loadFragmentPoints(IMetaCache cache) throws Exception {
        Map<FragmentMeta, Long> writePointsMap = new HashMap<>();
        GetResponse response =
                client.getKVClient()
                        .get(ByteSequence.from(STATISTICS_FRAGMENT_POINTS_PREFIX.getBytes()))
                        .get();
        Map<String, List<KeyValue>> timeSeriesRangeListMap = new HashMap<>();
        for (KeyValue kv : response.getKvs()) {
            String[] tuples = kv.getKey().toString().split("/");
            String timeSeriesRangeStr = tuples[tuples.length - 2];
            List<KeyValue> keyValues =
                    timeSeriesRangeListMap.computeIfAbsent(
                            timeSeriesRangeStr, k -> new ArrayList<>());
            keyValues.add(kv);
        }
        for (Map.Entry<String, List<KeyValue>> entry : timeSeriesRangeListMap.entrySet()) {
            TimeSeriesRange timeSeriesRange = TimeSeriesInterval.fromString(entry.getKey());
            List<FragmentMeta> fragmentMetas =
                    cache.getFragmentMapByExactTimeSeriesInterval(timeSeriesRange);
            for (KeyValue kv : entry.getValue()) {
                String[] tuples = kv.getKey().toString().split("/");
                long startTime = Long.parseLong(tuples[tuples.length - 1]);
                for (FragmentMeta fragmentMeta : fragmentMetas) {
                    if (fragmentMeta.getTimeInterval().getStartTime() == startTime) {
                        long points = JsonUtils.fromJson(kv.getValue().getBytes(), Long.class);
                        writePointsMap.put(fragmentMeta, points);
                    }
                }
            }
        }
        return writePointsMap;
    }

    @Override
    public void deleteFragmentPoints(TimeSeriesInterval tsInterval, TimeInterval timeInterval)
            throws Exception {
        try {
            client.getKVClient()
                    .delete(
                            ByteSequence.from(
                                    (STATISTICS_FRAGMENT_POINTS_PREFIX
                                                    + "/"
                                                    + tsInterval.toString()
                                                    + "/"
                                                    + timeInterval.toString())
                                            .getBytes()))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when removing fragment points: ", e);
        }
    }

    @Override
    public void updateFragmentPoints(FragmentMeta fragmentMeta, long points) throws Exception {
        String path =
                STATISTICS_FRAGMENT_POINTS_PREFIX
                        + "/"
                        + fragmentMeta.getTsInterval().toString()
                        + "/"
                        + fragmentMeta.getTimeInterval().toString();
        client.getKVClient()
                .put(
                        ByteSequence.from(path.getBytes()),
                        ByteSequence.from(JsonUtils.toJson(points)))
                .get();
    }

    @Override
    public void updateFragmentHeat(
            Map<FragmentMeta, Long> writeHotspotMap, Map<FragmentMeta, Long> readHotspotMap)
            throws Exception {
        for (Map.Entry<FragmentMeta, Long> writeHotspotEntry : writeHotspotMap.entrySet()) {
            String path =
                    STATISTICS_FRAGMENT_HEAT_PREFIX_WRITE
                            + "/"
                            + writeHotspotEntry.getKey().getTsInterval().toString()
                            + "/"
                            + writeHotspotEntry.getKey().getTimeInterval().toString();
            GetResponse response =
                    client.getKVClient().get(ByteSequence.from(path.getBytes())).get();
            if (response == null || response.getCount() <= 0) {
                client.getKVClient()
                        .put(
                                ByteSequence.from(path.getBytes()),
                                ByteSequence.from(JsonUtils.toJson(writeHotspotEntry.getValue())));
            } else {
                long heat =
                        JsonUtils.fromJson(
                                response.getKvs().get(0).getValue().getBytes(), Long.class);
                client.getKVClient()
                        .put(
                                ByteSequence.from(path.getBytes()),
                                ByteSequence.from(
                                        JsonUtils.toJson(heat + writeHotspotEntry.getValue())));
            }
        }
        for (Map.Entry<FragmentMeta, Long> readHotspotEntry : readHotspotMap.entrySet()) {
            String path =
                    STATISTICS_FRAGMENT_HEAT_PREFIX_READ
                            + "/"
                            + readHotspotEntry.getKey().getTsInterval().toString()
                            + "/"
                            + readHotspotEntry.getKey().getTimeInterval().toString();
            GetResponse response =
                    client.getKVClient().get(ByteSequence.from(path.getBytes())).get();
            if (response == null || response.getCount() <= 0) {
                client.getKVClient()
                        .put(
                                ByteSequence.from(path.getBytes()),
                                ByteSequence.from(JsonUtils.toJson(readHotspotEntry.getValue())));
            } else {
                long heat =
                        JsonUtils.fromJson(
                                response.getKvs().get(0).getValue().getBytes(), Long.class);
                client.getKVClient()
                        .put(
                                ByteSequence.from(path.getBytes()),
                                ByteSequence.from(
                                        JsonUtils.toJson(heat + readHotspotEntry.getValue())));
            }
        }
    }

    @Override
    public Pair<Map<FragmentMeta, Long>, Map<FragmentMeta, Long>> loadFragmentHeat(IMetaCache cache)
            throws Exception {
        Map<FragmentMeta, Long> writeHotspotMap = new HashMap<>();
        Map<FragmentMeta, Long> readHotspotMap = new HashMap<>();
        GetResponse writeResponse =
                client.getKVClient()
                        .get(ByteSequence.from(STATISTICS_FRAGMENT_HEAT_PREFIX_WRITE.getBytes()))
                        .get();
        GetResponse readResponse =
                client.getKVClient()
                        .get(ByteSequence.from(STATISTICS_FRAGMENT_HEAT_PREFIX_READ.getBytes()))
                        .get();
        Map<String, List<KeyValue>> timeSeriesWriteRangeListMap = new HashMap<>();
        Map<String, List<KeyValue>> timeSeriesReadRangeListMap = new HashMap<>();
        if (writeResponse != null) {
            for (KeyValue kv : writeResponse.getKvs()) {
                String[] tuples = kv.getKey().toString().split("/");
                List<KeyValue> keyValues =
                        timeSeriesWriteRangeListMap.computeIfAbsent(
                                tuples[tuples.length - 2], k -> new ArrayList<>());
                keyValues.add(kv);
            }
        }
        if (readResponse != null) {
            for (KeyValue kv : readResponse.getKvs()) {
                String[] tuples = kv.getKey().toString().split("/");
                List<KeyValue> keyValues =
                        timeSeriesReadRangeListMap.computeIfAbsent(
                                tuples[tuples.length - 2], k -> new ArrayList<>());
                keyValues.add(kv);
            }
        }
        for (Map.Entry<String, List<KeyValue>> entry : timeSeriesWriteRangeListMap.entrySet()) {
            TimeSeriesRange timeSeriesRange = TimeSeriesInterval.fromString(entry.getKey());
            Map<TimeSeriesRange, List<FragmentMeta>> fragmentMapOfTimeSeriesInterval =
                    cache.getFragmentMapByTimeSeriesInterval(timeSeriesRange);
            List<FragmentMeta> fragmentMetas = fragmentMapOfTimeSeriesInterval.get(timeSeriesRange);

            if (fragmentMetas != null) {
                for (KeyValue kv : entry.getValue()) {
                    String[] tuples = kv.getKey().toString().split("/");
                    long startTime = Long.parseLong(tuples[tuples.length - 1]);
                    for (FragmentMeta fragmentMeta : fragmentMetas) {
                        if (fragmentMeta.getTimeInterval().getStartTime() == startTime) {
                            long heat = JsonUtils.fromJson(kv.getValue().getBytes(), Long.class);
                            writeHotspotMap.put(fragmentMeta, heat);
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, List<KeyValue>> entry : timeSeriesReadRangeListMap.entrySet()) {
            TimeSeriesRange timeSeriesRange = TimeSeriesInterval.fromString(entry.getKey());
            Map<TimeSeriesRange, List<FragmentMeta>> fragmentMapOfTimeSeriesInterval =
                    cache.getFragmentMapByTimeSeriesInterval(timeSeriesRange);
            List<FragmentMeta> fragmentMetas = fragmentMapOfTimeSeriesInterval.get(timeSeriesRange);

            if (fragmentMetas != null) {
                for (KeyValue kv : entry.getValue()) {
                    String[] tuples = kv.getKey().toString().split("/");
                    long startTime = Long.parseLong(tuples[tuples.length - 1]);
                    for (FragmentMeta fragmentMeta : fragmentMetas) {
                        if (fragmentMeta.getTimeInterval().getStartTime() == startTime) {
                            long heat = JsonUtils.fromJson(kv.getValue().getBytes(), Long.class);
                            readHotspotMap.put(fragmentMeta, heat);
                        }
                    }
                }
            }
        }
        return new Pair<>(writeHotspotMap, readHotspotMap);
    }

    @Override
    public void removeFragmentHeat() throws MetaStorageException {
        try {
            client.getKVClient()
                    .delete(ByteSequence.from(STATISTICS_FRAGMENT_HEAT_PREFIX_WRITE.getBytes()))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when removing fragment heat: ", e);
        }
    }

    @Override
    public void lockFragmentHeatCounter() throws MetaStorageException {
        try {
            fragmentHeatCounterLeaseLock.lock();
            fragmentHeatCounterLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(
                            ByteSequence.from(FRAGMENT_HEAT_COUNTER_LOCK_NODE.getBytes()),
                            fragmentHeatCounterLease);
        } catch (Exception e) {
            fragmentHeatCounterLeaseLock.unlock();
            throw new MetaStorageException("acquire fragment heat counter mutex error: ", e);
        }
    }

    @Override
    public void incrementFragmentHeatCounter() throws MetaStorageException {
        client.getKVClient()
                .put(
                        ByteSequence.from(STATISTICS_FRAGMENT_HEAT_COUNTER_PREFIX.getBytes()),
                        ByteSequence.from(JsonUtils.toJson(getFragmentHeatCounter() + 1)));
    }

    @Override
    public void resetFragmentHeatCounter() throws MetaStorageException {
        client.getKVClient()
                .put(
                        ByteSequence.from(STATISTICS_FRAGMENT_HEAT_COUNTER_PREFIX.getBytes()),
                        ByteSequence.from(JsonUtils.toJson(0)));
    }

    @Override
    public void releaseFragmentHeatCounter() throws MetaStorageException {
        try {
            client.getLockClient()
                    .unlock(ByteSequence.from(FRAGMENT_HEAT_COUNTER_LOCK_NODE.getBytes()))
                    .get();
            client.getLeaseClient().revoke(fragmentHeatCounterLease).get();
            fragmentHeatCounterLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release fragment heat counter mutex error: ", e);
        } finally {
            fragmentHeatCounterLeaseLock.unlock();
        }
    }

    @Override
    public int getFragmentHeatCounter() throws MetaStorageException {
        try {
            String[] tuples = STATISTICS_FRAGMENT_HEAT_COUNTER_PREFIX.split("/");
            String lastTuple = tuples[tuples.length - 1];
            StringBuilder newPrefix = new StringBuilder();
            for (int i = 0; i < tuples.length - 1; i++) {
                newPrefix.append(tuples[i]);
            }
            GetResponse response =
                    client.getKVClient()
                            .get(ByteSequence.from(newPrefix.toString().getBytes()))
                            .get();
            if (!response.getKvs().isEmpty()) {
                for (KeyValue kv : response.getKvs()) {
                    if (kv.getKey().toString().equals(lastTuple)) {
                        return JsonUtils.fromJson(kv.getValue().getBytes(), Integer.class);
                    }
                }
            }
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when get fragment heat counter: ", e);
        }
        return 0;
    }

    @Override
    public boolean proposeToReshard() throws MetaStorageException {
        ReshardStatus currStatus = getReshardStatus();
        if (currStatus == null
                || (currStatus.equals(NON_RESHARDING) || currStatus.equals(JUDGING))) {
            updateReshardStatus(EXECUTING);
            return true;
        }
        return false;
    }

    private ReshardStatus getReshardStatus() throws MetaStorageException {
        try {
            String[] tuples = RESHARD_STATUS_NODE_PREFIX.split("/");
            String lastTuple = tuples[tuples.length - 1];
            StringBuilder newPrefix = new StringBuilder();
            for (int i = 0; i < tuples.length - 1; i++) {
                newPrefix.append(tuples[i]);
            }
            GetResponse response =
                    client.getKVClient()
                            .get(ByteSequence.from(newPrefix.toString().getBytes()))
                            .get();
            if (!response.getKvs().isEmpty()) {
                for (KeyValue kv : response.getKvs()) {
                    if (kv.getKey().toString().equals(lastTuple)) {
                        return JsonUtils.fromJson(kv.getValue().getBytes(), ReshardStatus.class);
                    }
                }
            }
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when get reshard status: ", e);
        }
        return null;
    }

    @Override
    public void lockReshardStatus() throws MetaStorageException {
        try {
            reshardStatusLeaseLock.lock();
            reshardStatusLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(
                            ByteSequence.from(RESHARD_STATUS_LOCK_NODE.getBytes()),
                            reshardStatusLease);
        } catch (Exception e) {
            reshardStatusLeaseLock.unlock();
            throw new MetaStorageException("acquire reshard status mutex error: ", e);
        }
    }

    @Override
    public void updateReshardStatus(ReshardStatus status) throws MetaStorageException {
        try {
            client.getKVClient()
                    .put(
                            ByteSequence.from(RESHARD_STATUS_NODE_PREFIX.getBytes()),
                            ByteSequence.from(JsonUtils.toJson(status)))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException("update reshard status mutex error: ", e);
        }
    }

    @Override
    public void releaseReshardStatus() throws MetaStorageException {
        try {
            client.getLockClient()
                    .unlock(ByteSequence.from(RESHARD_STATUS_LOCK_NODE.getBytes()))
                    .get();
            client.getLeaseClient().revoke(reshardStatusLease).get();
            reshardStatusLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release reshard status mutex error: ", e);
        } finally {
            reshardStatusLeaseLock.unlock();
        }
    }

    @Override
    public void removeReshardStatus() throws MetaStorageException {
        try {
            client.getKVClient()
                    .delete(ByteSequence.from(RESHARD_STATUS_NODE_PREFIX.getBytes()))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException("remove reshard status mutex error: ", e);
        }
    }

    @Override
    public void registerReshardStatusHook(ReshardStatusChangeHook hook) {
        this.reshardStatusChangeHook = hook;
    }

    @Override
    public void lockReshardCounter() throws MetaStorageException {
        try {
            reshardCounterLeaseLock.lock();
            reshardCounterLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(
                            ByteSequence.from(RESHARD_COUNTER_LOCK_NODE.getBytes()),
                            reshardCounterLease);
        } catch (Exception e) {
            reshardCounterLeaseLock.unlock();
            throw new MetaStorageException("acquire reshard counter mutex error: ", e);
        }
    }

    @Override
    public void incrementReshardCounter() throws MetaStorageException {
        client.getKVClient()
                .put(
                        ByteSequence.from(RESHARD_COUNTER_NODE_PREFIX.getBytes()),
                        ByteSequence.from(JsonUtils.toJson(getReshardCounter() + 1)));
    }

    @Override
    public void resetReshardCounter() throws MetaStorageException {
        client.getKVClient()
                .put(
                        ByteSequence.from(RESHARD_COUNTER_NODE_PREFIX.getBytes()),
                        ByteSequence.from(JsonUtils.toJson(0)));
    }

    private int getReshardCounter() throws MetaStorageException {
        try {
            String[] tuples = RESHARD_COUNTER_NODE_PREFIX.split("/");
            String lastTuple = tuples[tuples.length - 1];
            StringBuilder newPrefix = new StringBuilder();
            for (int i = 0; i < tuples.length - 1; i++) {
                newPrefix.append(tuples[i]);
            }
            GetResponse response =
                    client.getKVClient()
                            .get(ByteSequence.from(newPrefix.toString().getBytes()))
                            .get();
            if (!response.getKvs().isEmpty()) {
                for (KeyValue kv : response.getKvs()) {
                    if (kv.getKey().toString().equals(lastTuple)) {
                        return JsonUtils.fromJson(kv.getValue().getBytes(), Integer.class);
                    }
                }
            }
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when get reshard counter: ", e);
        }
        return 0;
    }

    @Override
    public void releaseReshardCounter() throws MetaStorageException {
        try {
            client.getLockClient()
                    .unlock(ByteSequence.from(RESHARD_COUNTER_LOCK_NODE.getBytes()))
                    .get();
            client.getLeaseClient().revoke(reshardCounterLease).get();
            reshardCounterLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release reshard counter mutex error: ", e);
        } finally {
            reshardCounterLeaseLock.unlock();
        }
    }

    @Override
    public void removeReshardCounter() throws MetaStorageException {
        try {
            client.getKVClient()
                    .delete(ByteSequence.from(RESHARD_COUNTER_NODE_PREFIX.getBytes()))
                    .get();
        } catch (Exception e) {
            throw new MetaStorageException("remove reshard counter mutex error: ", e);
        }
    }

    @Override
    public void registerReshardCounterChangeHook(ReshardCounterChangeHook hook) {
        this.reshardCounterChangeHook = hook;
    }

    private void lockTransform() throws MetaStorageException {
        try {
            transformLeaseLock.lock();
            transformLease = client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(ByteSequence.from(TRANSFORM_LOCK.getBytes()), transformLease);
        } catch (Exception e) {
            transformLeaseLock.unlock();
            throw new MetaStorageException("acquire transform mutex error: ", e);
        }
    }

    private void releaseTransform() throws MetaStorageException {
        try {
            client.getLockClient().unlock(ByteSequence.from(TRANSFORM_LOCK.getBytes())).get();
            client.getLeaseClient().revoke(transformLease).get();
            transformLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release user mutex error: ", e);
        } finally {
            transformLeaseLock.unlock();
        }
    }

    @Override
    public void registerTransformChangeHook(TransformChangeHook hook) {
        transformChangeHook = hook;
    }

    @Override
    public List<TransformTaskMeta> loadTransformTask() throws MetaStorageException {
        try {
            lockTransform();
            Map<String, TransformTaskMeta> taskMetaMap = new HashMap<>();
            GetResponse response =
                    this.client
                            .getKVClient()
                            .get(
                                    ByteSequence.from(TRANSFORM_PREFIX.getBytes()),
                                    GetOption.newBuilder()
                                            .withPrefix(
                                                    ByteSequence.from(TRANSFORM_PREFIX.getBytes()))
                                            .build())
                            .get();
            if (response.getCount() != 0L) {
                response.getKvs()
                        .forEach(
                                e -> {
                                    TransformTaskMeta taskMeta =
                                            JsonUtils.fromJson(
                                                    e.getValue().getBytes(),
                                                    TransformTaskMeta.class);
                                    taskMetaMap.put(taskMeta.getName(), taskMeta);
                                });
            }
            return new ArrayList<>(taskMetaMap.values());
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when load transform: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (transformLease != -1) {
                releaseTransform();
            }
        }
    }

    @Override
    public void addTransformTask(TransformTaskMeta transformTask) throws MetaStorageException {
        updateTransformTask(transformTask);
    }

    @Override
    public void updateTransformTask(TransformTaskMeta transformTask) throws MetaStorageException {
        try {
            lockTransform();
            this.client
                    .getKVClient()
                    .put(
                            ByteSequence.from(
                                    (TRANSFORM_PREFIX + transformTask.getName()).getBytes()),
                            ByteSequence.from(JsonUtils.toJson(transformTask)))
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when add/update transform: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (transformLease != -1) {
                releaseTransform();
            }
        }
        if (transformChangeHook != null) {
            transformChangeHook.onChange(transformTask.getName(), transformTask);
        }
    }

    @Override
    public void dropTransformTask(String name) throws MetaStorageException {
        try {
            lockTransform();
            this.client
                    .getKVClient()
                    .delete(ByteSequence.from((TRANSFORM_PREFIX + name).getBytes()))
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            logger.error("got error when remove transform: ", e);
            throw new MetaStorageException(e);
        } finally {
            if (transformLease != -1) {
                releaseTransform();
            }
        }
        if (transformChangeHook != null) {
            transformChangeHook.onChange(name, null);
        }
    }

    @Override
    public void lockMaxActiveEndTimeStatistics() throws MetaStorageException {
        try {
            maxActiveEndTimeStatisticsLeaseLock.lock();
            maxActiveEndTimeStatisticsLease =
                    client.getLeaseClient().grant(MAX_LOCK_TIME).get().getID();
            client.getLockClient()
                    .lock(
                            ByteSequence.from(ACTIVE_END_TIME_COUNTER_LOCK_NODE.getBytes()),
                            maxActiveEndTimeStatisticsLease);
        } catch (Exception e) {
            maxActiveEndTimeStatisticsLeaseLock.unlock();
            throw new MetaStorageException("acquire max active end time mutex error: ", e);
        }
    }

    @Override
    public void addOrUpdateMaxActiveEndTimeStatistics(long endTime) throws MetaStorageException {
        try {
            client.getKVClient()
                    .put(
                            ByteSequence.from(MAX_ACTIVE_END_TIME_STATISTICS_NODE.getBytes()),
                            ByteSequence.from(JsonUtils.toJson(endTime)));
        } catch (Exception e) {
            throw new MetaStorageException(
                    "encounter error when adding or updating max active end time statistics: ", e);
        }
    }

    @Override
    public long getMaxActiveEndTimeStatistics() throws MetaStorageException {
        try {
            String[] tuples = MAX_ACTIVE_END_TIME_STATISTICS_NODE.split("/");
            String lastTuple = tuples[tuples.length - 1];
            StringBuilder newPrefix = new StringBuilder();
            for (int i = 0; i < tuples.length - 1; i++) {
                newPrefix.append(tuples[i]);
            }
            GetResponse response =
                    client.getKVClient()
                            .get(ByteSequence.from(newPrefix.toString().getBytes()))
                            .get();
            if (!response.getKvs().isEmpty()) {
                for (KeyValue kv : response.getKvs()) {
                    if (kv.getKey().toString().equals(lastTuple)) {
                        return JsonUtils.fromJson(kv.getValue().getBytes(), Integer.class);
                    }
                }
            }
        } catch (Exception e) {
            throw new MetaStorageException("encounter error when get max active end time: ", e);
        }
        return 0;
    }

    @Override
    public void releaseMaxActiveEndTimeStatistics() throws MetaStorageException {
        try {
            client.getLockClient()
                    .unlock(ByteSequence.from(ACTIVE_END_TIME_COUNTER_LOCK_NODE.getBytes()))
                    .get();
            client.getLeaseClient().revoke(maxActiveEndTimeStatisticsLease).get();
            maxActiveEndTimeStatisticsLease = -1L;
        } catch (Exception e) {
            throw new MetaStorageException("release user mutex error: ", e);
        } finally {
            maxActiveEndTimeStatisticsLeaseLock.unlock();
        }
    }

    @Override
    public void registerMaxActiveEndTimeStatisticsChangeHook(
            MaxActiveEndTimeStatisticsChangeHook hook) throws MetaStorageException {
        this.maxActiveEndTimeStatisticsChangeHook = hook;
    }

    public void close() throws MetaStorageException {
        this.schemaMappingWatcher.close();
        this.schemaMappingWatcher = null;

        this.iginxWatcher.close();
        this.iginxWatcher = null;

        this.storageWatcher.close();
        this.storageWatcher = null;

        this.storageUnitWatcher.close();
        this.storageUnitWatcher = null;

        this.fragmentWatcher.close();
        this.fragmentWatcher = null;

        this.userWatcher.close();
        this.userWatcher = null;

        this.transformWatcher.close();
        this.transformWatcher = null;

        this.client.close();
        this.client = null;
    }
}
