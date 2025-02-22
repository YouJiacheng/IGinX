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
package cn.edu.tsinghua.iginx.engine.physical.memory;

import cn.edu.tsinghua.iginx.conf.ConfigDescriptor;
import cn.edu.tsinghua.iginx.engine.physical.exception.PhysicalException;
import cn.edu.tsinghua.iginx.engine.physical.memory.queue.MemoryPhysicalTaskQueue;
import cn.edu.tsinghua.iginx.engine.physical.memory.queue.MemoryPhysicalTaskQueueImpl;
import cn.edu.tsinghua.iginx.engine.physical.task.MemoryPhysicalTask;
import cn.edu.tsinghua.iginx.engine.physical.task.TaskExecuteResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryPhysicalTaskDispatcher {

    private static final Logger logger =
            LoggerFactory.getLogger(MemoryPhysicalTaskDispatcher.class);

    private static final MemoryPhysicalTaskDispatcher INSTANCE = new MemoryPhysicalTaskDispatcher();

    private final MemoryPhysicalTaskQueue taskQueue;

    private final ExecutorService taskDispatcher;

    private final ExecutorService taskExecuteThreadPool;

    private MemoryPhysicalTaskDispatcher() {
        taskQueue = new MemoryPhysicalTaskQueueImpl();
        taskExecuteThreadPool =
                Executors.newFixedThreadPool(
                        ConfigDescriptor.getInstance().getConfig().getMemoryTaskThreadPoolSize());
        taskDispatcher = Executors.newSingleThreadExecutor();
    }

    public static MemoryPhysicalTaskDispatcher getInstance() {
        return INSTANCE;
    }

    public boolean addMemoryTask(MemoryPhysicalTask task) {
        return taskQueue.addTask(task);
    }

    public void startDispatcher() {
        taskDispatcher.submit(
                () -> {
                    try {
                        while (true) {
                            final MemoryPhysicalTask task = taskQueue.getTask();
                            taskExecuteThreadPool.submit(
                                    () -> {
                                        MemoryPhysicalTask currentTask = task;
                                        while (currentTask != null) {
                                            TaskExecuteResult result;
                                            long startTime = System.currentTimeMillis();
                                            try {
                                                result = currentTask.execute();
                                            } catch (Exception e) {
                                                logger.error("execute memory task failure: ", e);
                                                result =
                                                        new TaskExecuteResult(
                                                                new PhysicalException(e));
                                            }
                                            long span = System.currentTimeMillis() - startTime;
                                            currentTask.setSpan(span);
                                            currentTask.setResult(result);
                                            if (currentTask.getFollowerTask()
                                                    != null) { // 链式执行可以被执行的任务
                                                MemoryPhysicalTask followerTask =
                                                        (MemoryPhysicalTask)
                                                                currentTask.getFollowerTask();
                                                if (followerTask.notifyParentReady()) {
                                                    currentTask = followerTask;
                                                } else {
                                                    currentTask = null;
                                                }
                                            } else {
                                                currentTask = null;
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
    }

    public void stopDispatcher() {
        taskDispatcher.shutdown();
    }
}
