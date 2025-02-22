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
package cn.edu.tsinghua.iginx.engine.physical.optimizer;

import cn.edu.tsinghua.iginx.engine.physical.optimizer.naive.NaivePhysicalOptimizer;
import cn.edu.tsinghua.iginx.engine.physical.optimizer.rule.Rule;
import java.util.Collection;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhysicalOptimizerManager {

    private static final Logger logger = LoggerFactory.getLogger(PhysicalOptimizerManager.class);

    private static final String NAIVE = "naive";

    private static final PhysicalOptimizerManager INSTANCE = new PhysicalOptimizerManager();

    private PhysicalOptimizerManager() {}

    public static PhysicalOptimizerManager getInstance() {
        return INSTANCE;
    }

    public PhysicalOptimizer getOptimizer(String name) {
        if (name == null) {
            return null;
        }
        PhysicalOptimizer optimizer = null;
        switch (name) {
            case NAIVE:
                logger.info("use {} as physical optimizer.", name);
                optimizer = NaivePhysicalOptimizer.getInstance();
                break;
            default:
                logger.error("unknown physical optimizer {}, use {} as default.", name, NAIVE);
                optimizer = NaivePhysicalOptimizer.getInstance();
        }
        optimizer.setRules(getRules());
        return optimizer;
    }

    private Collection<Rule> getRules() {
        // TODO: get rule from conf
        return Collections.emptyList();
    }
}
