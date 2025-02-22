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
package cn.edu.tsinghua.iginx.engine.physical.storage.execute;

import cn.edu.tsinghua.iginx.conf.ConfigDescriptor;
import cn.edu.tsinghua.iginx.engine.physical.exception.PhysicalException;
import cn.edu.tsinghua.iginx.engine.physical.exception.TooManyPhysicalTasksException;
import cn.edu.tsinghua.iginx.engine.physical.exception.UnexpectedOperatorException;
import cn.edu.tsinghua.iginx.engine.physical.memory.MemoryPhysicalTaskDispatcher;
import cn.edu.tsinghua.iginx.engine.physical.optimizer.ReplicaDispatcher;
import cn.edu.tsinghua.iginx.engine.physical.storage.IStorage;
import cn.edu.tsinghua.iginx.engine.physical.storage.StorageManager;
import cn.edu.tsinghua.iginx.engine.physical.storage.domain.Timeseries;
import cn.edu.tsinghua.iginx.engine.physical.storage.queue.StoragePhysicalTaskQueue;
import cn.edu.tsinghua.iginx.engine.physical.storage.utils.TagKVUtils;
import cn.edu.tsinghua.iginx.engine.physical.task.GlobalPhysicalTask;
import cn.edu.tsinghua.iginx.engine.physical.task.MemoryPhysicalTask;
import cn.edu.tsinghua.iginx.engine.physical.task.StoragePhysicalTask;
import cn.edu.tsinghua.iginx.engine.physical.task.TaskExecuteResult;
import cn.edu.tsinghua.iginx.engine.shared.operator.ShowTimeSeries;
import cn.edu.tsinghua.iginx.engine.shared.operator.tag.TagFilter;
import cn.edu.tsinghua.iginx.metadata.DefaultMetaManager;
import cn.edu.tsinghua.iginx.metadata.IMetaManager;
import cn.edu.tsinghua.iginx.metadata.entity.StorageEngineMeta;
import cn.edu.tsinghua.iginx.metadata.entity.StorageUnitMeta;
import cn.edu.tsinghua.iginx.metadata.hook.StorageEngineChangeHook;
import cn.edu.tsinghua.iginx.metadata.hook.StorageUnitHook;
import cn.edu.tsinghua.iginx.monitor.HotSpotMonitor;
import cn.edu.tsinghua.iginx.monitor.RequestsMonitor;
import cn.edu.tsinghua.iginx.utils.Pair;
import cn.edu.tsinghua.iginx.utils.StringUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoragePhysicalTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(StoragePhysicalTaskExecutor.class);

    private static final StoragePhysicalTaskExecutor INSTANCE = new StoragePhysicalTaskExecutor();

    private final IMetaManager metaManager = DefaultMetaManager.getInstance();

    private final StorageManager storageManager =
            new StorageManager(metaManager.getStorageEngineList());

    private final Map<String, StoragePhysicalTaskQueue> storageTaskQueues =
            new ConcurrentHashMap<>();

    private final Map<String, ExecutorService> dispatchers = new ConcurrentHashMap<>();

    private ReplicaDispatcher replicaDispatcher;

    private MemoryPhysicalTaskDispatcher memoryTaskExecutor;

    private final int maxCachedPhysicalTaskPerStorage =
            ConfigDescriptor.getInstance().getConfig().getMaxCachedPhysicalTaskPerStorage();

    private StoragePhysicalTaskExecutor() {
        StorageUnitHook storageUnitHook =
                (before, after) -> {
                    if (before == null && after != null) { // 新增加 du，处理这种事件，其他事件暂时不处理
                        logger.info("new storage unit " + after.getId() + " come!");
                        String id = after.getId();
                        boolean isDummy = after.isDummy();
                        if (storageTaskQueues.containsKey(id)) {
                            return;
                        }
                        storageTaskQueues.put(id, new StoragePhysicalTaskQueue());
                        // 为拥有该分片的存储创建一个调度线程，用于调度任务执行
                        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
                        long storageId = after.getStorageEngineId();
                        dispatchers.put(id, dispatcher);
                        dispatcher.submit(
                                () -> {
                                    try {
                                        StoragePhysicalTaskQueue taskQueue =
                                                storageTaskQueues.get(id);
                                        Pair<IStorage, ThreadPoolExecutor> p =
                                                storageManager.getStorage(storageId);
                                        while (p == null) {
                                            p = storageManager.getStorage(storageId);
                                            logger.info("spinning for IStorage!");
                                            try {
                                                Thread.sleep(5);
                                            } catch (InterruptedException e) {
                                                logger.error("encounter error when spinning: ", e);
                                            }
                                        }
                                        Pair<IStorage, ThreadPoolExecutor> pair = p;
                                        while (true) {
                                            StoragePhysicalTask task = taskQueue.getTask();
                                            task.setStorageUnit(id);
                                            task.setDummyStorageUnit(isDummy);
                                            if (pair.v.getQueue().size()
                                                    > maxCachedPhysicalTaskPerStorage) {
                                                task.setResult(
                                                        new TaskExecuteResult(
                                                                new TooManyPhysicalTasksException(
                                                                        storageId)));
                                                continue;
                                            }
                                            pair.v.submit(
                                                    () -> {
                                                        TaskExecuteResult result = null;
                                                        long taskId = System.nanoTime();
                                                        long startTime = System.currentTimeMillis();
                                                        try {
                                                            result = pair.k.execute(task);
                                                        } catch (Exception e) {
                                                            logger.error(
                                                                    "execute task error: " + e);
                                                            result =
                                                                    new TaskExecuteResult(
                                                                            new PhysicalException(
                                                                                    e));
                                                        }
                                                        try {
                                                            HotSpotMonitor.getInstance()
                                                                    .recordAfter(
                                                                            taskId,
                                                                            task
                                                                                    .getTargetFragment(),
                                                                            task.getOperators()
                                                                                    .get(0)
                                                                                    .getType());
                                                            RequestsMonitor.getInstance()
                                                                    .record(
                                                                            task
                                                                                    .getTargetFragment(),
                                                                            task.getOperators()
                                                                                    .get(0));
                                                        } catch (Exception e) {
                                                            logger.error("Monitor catch error:", e);
                                                        }
                                                        long span =
                                                                System.currentTimeMillis()
                                                                        - startTime;
                                                        task.setSpan(span);
                                                        task.setResult(result);
                                                        if (task.getFollowerTask() != null
                                                                && task
                                                                        .isSync()) { // 只有同步任务才会影响后续任务的执行
                                                            MemoryPhysicalTask followerTask =
                                                                    (MemoryPhysicalTask)
                                                                            task.getFollowerTask();
                                                            boolean isFollowerTaskReady =
                                                                    followerTask
                                                                            .notifyParentReady();
                                                            if (isFollowerTaskReady) {
                                                                memoryTaskExecutor.addMemoryTask(
                                                                        followerTask);
                                                            }
                                                        }
                                                        if (task.isNeedBroadcasting()) { // 需要传播
                                                            if (result.getException() != null) {
                                                                logger.error(
                                                                        "task "
                                                                                + task
                                                                                + " will not broadcasting to replicas for the sake of exception: "
                                                                                + result
                                                                                        .getException());
                                                                task.setResult(
                                                                        new TaskExecuteResult(
                                                                                result
                                                                                        .getException()));
                                                            } else {
                                                                StorageUnitMeta masterStorageUnit =
                                                                        task.getTargetFragment()
                                                                                .getMasterStorageUnit();
                                                                List<String> replicaIds =
                                                                        masterStorageUnit
                                                                                .getReplicas()
                                                                                .stream()
                                                                                .map(
                                                                                        StorageUnitMeta
                                                                                                ::getId)
                                                                                .collect(
                                                                                        Collectors
                                                                                                .toList());
                                                                replicaIds.add(
                                                                        masterStorageUnit.getId());
                                                                for (String replicaId :
                                                                        replicaIds) {
                                                                    if (replicaId.equals(
                                                                            task
                                                                                    .getStorageUnit())) {
                                                                        continue;
                                                                    }
                                                                    StoragePhysicalTask
                                                                            replicaTask =
                                                                                    new StoragePhysicalTask(
                                                                                            task
                                                                                                    .getOperators(),
                                                                                            false,
                                                                                            false);
                                                                    storageTaskQueues
                                                                            .get(replicaId)
                                                                            .addTask(replicaTask);
                                                                    logger.info(
                                                                            "broadcasting task "
                                                                                    + task
                                                                                    + " to "
                                                                                    + replicaId);
                                                                }
                                                            }
                                                        }
                                                    });
                                        }
                                    } catch (Exception e) {
                                        logger.error(
                                                "unexpected exception during dispatcher memory task, please contact developer to check: ",
                                                e);
                                    }
                                });
                        logger.info("process for new storage unit finished!");
                    }
                };
        StorageEngineChangeHook storageEngineChangeHook =
                (before, after) -> {
                    if (before == null && after != null) { // 新增加存储，处理这种事件，其他事件暂时不处理
                        if (after.getCreatedBy() != metaManager.getIginxId()) {
                            storageManager.addStorage(after);
                        }
                    }
                };
        metaManager.registerStorageEngineChangeHook(storageEngineChangeHook);
        metaManager.registerStorageUnitHook(storageUnitHook);
        List<StorageEngineMeta> storages = metaManager.getStorageEngineList();
        for (StorageEngineMeta storage : storages) {
            if (storage.isHasData()) {
                storageUnitHook.onChange(null, storage.getDummyStorageUnit());
            }
        }
    }

    public static StoragePhysicalTaskExecutor getInstance() {
        return INSTANCE;
    }

    public void commit(StoragePhysicalTask task) {
        commit(Collections.singletonList(task));
    }

    public void commitWithTargetStorageUnitId(StoragePhysicalTask task, String storageUnitId) {
        storageTaskQueues.get(storageUnitId).addTask(task);
    }

    public TaskExecuteResult executeGlobalTask(GlobalPhysicalTask task) {
        List<StorageEngineMeta> storageList = metaManager.getStorageEngineList();
        switch (task.getOperator().getType()) {
            case ShowTimeSeries:
                Set<Timeseries> timeseriesSet = new HashSet<>();
                for (StorageEngineMeta storage : storageList) {
                    long id = storage.getId();
                    Pair<IStorage, ThreadPoolExecutor> pair = storageManager.getStorage(id);
                    if (pair == null) {
                        continue;
                    }
                    try {
                        List<Timeseries> timeseriesList = pair.k.getTimeSeries();
                        // fix the schemaPrefix
                        String schemaPrefix = storage.getSchemaPrefix();
                        if (schemaPrefix != null) {
                            for (Timeseries timeseries : timeseriesList) {
                                timeseries.setPath(schemaPrefix + "." + timeseries.getPath());
                            }
                        }
                        timeseriesSet.addAll(timeseriesList);
                    } catch (PhysicalException e) {
                        return new TaskExecuteResult(e);
                    }
                }

                ShowTimeSeries operator = (ShowTimeSeries) task.getOperator();
                Set<String> pathRegexSet = operator.getPathRegexSet();
                TagFilter tagFilter = operator.getTagFilter();

                TreeSet<Timeseries> tsSetAfterFilter =
                        new TreeSet<>(Comparator.comparing(Timeseries::getPhysicalPath));
                for (Timeseries timeseries : timeseriesSet) {
                    boolean isTarget = true;
                    if (!pathRegexSet.isEmpty()) {
                        isTarget = false;
                        for (String pathRegex : pathRegexSet) {
                            if (Pattern.matches(
                                    StringUtils.reformatPath(pathRegex), timeseries.getPath())) {
                                isTarget = true;
                                break;
                            }
                        }
                    }
                    if (tagFilter != null) {
                        if (!TagKVUtils.match(timeseries.getTags(), tagFilter)) {
                            isTarget = false;
                        }
                    }
                    if (isTarget) {
                        tsSetAfterFilter.add(timeseries);
                    }
                }

                int limit = operator.getLimit();
                int offset = operator.getOffset();
                if (limit == Integer.MAX_VALUE && offset == 0) {
                    return new TaskExecuteResult(Timeseries.toRowStream(tsSetAfterFilter));
                } else {
                    // only need part of data.
                    List<Timeseries> tsList = new ArrayList<>();
                    int cur = 0, size = tsSetAfterFilter.size();
                    for (Iterator<Timeseries> iter = tsSetAfterFilter.iterator();
                            iter.hasNext();
                            cur++) {
                        if (cur >= size || cur - offset >= limit) {
                            break;
                        }
                        Timeseries ts = iter.next();
                        if (cur >= offset) {
                            tsList.add(ts);
                        }
                    }
                    return new TaskExecuteResult(Timeseries.toRowStream(tsList));
                }
            default:
                return new TaskExecuteResult(
                        new UnexpectedOperatorException(
                                "unknown op: " + task.getOperator().getType()));
        }
    }

    public void commit(List<StoragePhysicalTask> tasks) {
        for (StoragePhysicalTask task : tasks) {
            if (replicaDispatcher == null) {
                storageTaskQueues
                        .get(task.getTargetFragment().getMasterStorageUnitId())
                        .addTask(task); // 默认情况下，异步写备，查询只查主
            } else {
                storageTaskQueues
                        .get(replicaDispatcher.chooseReplica(task))
                        .addTask(task); // 在优化策略提供了选择器的情况下，利用选择器提供的结果
            }
        }
    }

    public void init(
            MemoryPhysicalTaskDispatcher memoryTaskExecutor, ReplicaDispatcher replicaDispatcher) {
        this.memoryTaskExecutor = memoryTaskExecutor;
        this.replicaDispatcher = replicaDispatcher;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }
}
