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
package cn.edu.tsinghua.iginx.integration.func.udf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import cn.edu.tsinghua.iginx.exceptions.ExecutionException;
import cn.edu.tsinghua.iginx.exceptions.SessionException;
import cn.edu.tsinghua.iginx.integration.controller.Controller;
import cn.edu.tsinghua.iginx.metadata.DefaultMetaManager;
import cn.edu.tsinghua.iginx.metadata.IMetaManager;
import cn.edu.tsinghua.iginx.metadata.entity.TransformTaskMeta;
import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import cn.edu.tsinghua.iginx.thrift.UDFType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UDFIT {

    private static final double delta = 0.01d;

    private static final Logger logger = LoggerFactory.getLogger(UDFIT.class);

    private static final IMetaManager metaManager = DefaultMetaManager.getInstance();

    private static Session session;

    @BeforeClass
    public static void setUp() throws SessionException {
        session = new Session("127.0.0.1", 6888, "root", "root");
        session.openSession();
    }

    @AfterClass
    public static void tearDown() throws SessionException {
        session.closeSession();
    }

    @Before
    public void insertData() throws ExecutionException, SessionException {
        String insertStrPrefix = "INSERT INTO us.d1 (key, s1, s2, s3, s4) values ";

        long startTimestamp = 0L;
        long endTimestamp = 15000L;

        StringBuilder builder = new StringBuilder(insertStrPrefix);

        int size = (int) (endTimestamp - startTimestamp);
        for (int i = 0; i < size; i++) {
            builder.append(", ");
            builder.append("(");
            builder.append(startTimestamp + i).append(", ");
            builder.append(i).append(", ");
            builder.append(i + 1).append(", ");
            builder.append("\"")
                    .append(new String(RandomStringUtils.randomAlphanumeric(10).getBytes()))
                    .append("\", ");
            builder.append((i + 0.1));
            builder.append(")");
        }
        builder.append(";");

        String insertStatement = builder.toString();

        SessionExecuteSqlResult res = session.executeSql(insertStatement);
        if (res.getParseErrorMsg() != null && !res.getParseErrorMsg().equals("")) {
            logger.error("Insert date execute fail. Caused by: {}.", res.getParseErrorMsg());
            fail();
        }
    }

    @After
    public void clearData() throws ExecutionException, SessionException {
        Controller.clearData(session);
    }

    private SessionExecuteSqlResult execute(String statement) {
        logger.info("Execute Statement: \"{}\"", statement);

        SessionExecuteSqlResult res = null;
        try {
            res = session.executeSql(statement);
        } catch (SessionException | ExecutionException e) {
            logger.error("Statement: \"{}\" execute fail. Caused by:", statement, e);
            fail();
        }

        if (res.getParseErrorMsg() != null && !res.getParseErrorMsg().equals("")) {
            logger.error(
                    "Statement: \"{}\" execute fail. Caused by: {}.",
                    statement,
                    res.getParseErrorMsg());
            fail();
        }

        return res;
    }

    @Test
    public void baseTests() {
        String udtfSQLFormat = "SELECT %s(s1) FROM us.d1 WHERE key < 200;";
        String udafSQLFormat = "SELECT %s(s1) FROM us.d1 OVER (RANGE 50 IN [0, 200));";
        String udsfSQLFormat = "SELECT %s(s1) FROM us.d1 WHERE key < 50;";

        List<TransformTaskMeta> taskMetas = metaManager.getTransformTasks();
        for (TransformTaskMeta taskMeta : taskMetas) {
            // execute udf
            if (taskMeta.getType().equals(UDFType.UDTF)) {
                execute(String.format(udtfSQLFormat, taskMeta.getName()));
            } else if (taskMeta.getType().equals(UDFType.UDAF)) {
                execute(String.format(udafSQLFormat, taskMeta.getName()));
            } else if (taskMeta.getType().equals(UDFType.UDSF)) {
                execute(String.format(udsfSQLFormat, taskMeta.getName()));
            }
        }
    }

    @Test
    public void testCOS() {
        String statement = "SELECT COS(s1) FROM us.d1 WHERE s1 < 10;";

        SessionExecuteSqlResult ret = execute(statement);
        assertEquals(Collections.singletonList("cos(us.d1.s1)"), ret.getPaths());
        assertArrayEquals(new long[] {0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L}, ret.getKeys());

        List<Double> expectedValues =
                Arrays.asList(
                        1.0,
                        0.5403023058681398,
                        -0.4161468365471424,
                        -0.9899924966004454,
                        -0.6536436208636119,
                        0.2836621854632263,
                        0.9601702866503661,
                        0.7539022543433046,
                        -0.14550003380861354,
                        -0.9111302618846769);
        for (int i = 0; i < ret.getValues().size(); i++) {
            assertEquals(1, ret.getValues().get(i).size());
            double expected = expectedValues.get(i);
            double actual = (double) ret.getValues().get(i).get(0);
            assertEquals(expected, actual, delta);
        }
    }

    @Test
    public void testConcurrentCos() {
        String insert =
                "INSERT INTO test(key, s1, s2) VALUES (1, 2, 3), (2, 3, 1), (3, 4, 3), (4, 9, 7), (5, 3, 6), (6, 6, 4);";
        execute(insert);

        String query =
                "SELECT * FROM (SELECT COS(s1) FROM test), (SELECT COS(s2) FROM test) LIMIT 10;";
        SessionExecuteSqlResult ret = execute(query);
        assertEquals(4, ret.getPaths().size());

        List<Double> cosS1ExpectedValues =
                Arrays.asList(
                        -0.4161468365471424,
                        -0.4161468365471424,
                        -0.4161468365471424,
                        -0.4161468365471424,
                        -0.4161468365471424,
                        -0.4161468365471424,
                        -0.9899924966004454,
                        -0.9899924966004454,
                        -0.9899924966004454,
                        -0.9899924966004454);
        List<Double> cosS2ExpectedValues =
                Arrays.asList(
                        -0.9899924966004454,
                        0.5403023058681398,
                        -0.9899924966004454,
                        0.7539022543433046,
                        0.9601702866503661,
                        -0.6536436208636119,
                        -0.9899924966004454,
                        0.5403023058681398,
                        -0.9899924966004454,
                        0.7539022543433046);

        for (int i = 0; i < ret.getValues().size(); i++) {
            assertEquals(4, ret.getValues().get(i).size());
            double expected = cosS1ExpectedValues.get(i);
            double actual = (double) ret.getValues().get(i).get(0);
            assertEquals(expected, actual, delta);

            expected = cosS2ExpectedValues.get(i);
            actual = (double) ret.getValues().get(i).get(1);
            assertEquals(expected, actual, delta);
        }
    }
}
