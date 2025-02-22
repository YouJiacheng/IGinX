package cn.edu.tsinghua.iginx.integration.expansion;

import static org.junit.Assert.fail;

import cn.edu.tsinghua.iginx.exceptions.ExecutionException;
import cn.edu.tsinghua.iginx.exceptions.SessionException;
import cn.edu.tsinghua.iginx.integration.controller.Controller;
import cn.edu.tsinghua.iginx.integration.expansion.unit.SQLTestTools;
import cn.edu.tsinghua.iginx.pool.SessionPool;
import cn.edu.tsinghua.iginx.session.Session;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapacityExpansionIT implements BaseCapacityExpansionIT {
    private static final Logger logger = LoggerFactory.getLogger(CapacityExpansionIT.class);
    protected static Session session;
    protected static SessionPool sessionPool;
    protected String ENGINE_TYPE;

    public CapacityExpansionIT(String engineType) {
        this.ENGINE_TYPE = engineType;
    }

    @After
    public void clearData() throws ExecutionException, SessionException {
        Controller.clearData(session);
    }

    @BeforeClass
    public static void setUp() throws SessionException {
        session = new Session("127.0.0.1", 6888, "root", "root");
        sessionPool =
                new SessionPool.Builder()
                        .host("127.0.0.1")
                        .port(6888)
                        .user("root")
                        .password("root")
                        .maxSize(3)
                        .build();
        session.openSession();
    }

    @AfterClass
    public static void tearDown() throws SessionException {
        session.closeSession();
        sessionPool.close();
    }

    @Test
    public void oriHasDataExpHasData() throws Exception {
        testQueryHistoryDataFromInitialNode();
        testQueryAfterInsertNewData();
        testCapacityExpansion_oriHasDataExpHasData();
        testWriteAndQueryAfterCapacityExpansion_oriHasDataExpHasData();
    }

    @Test
    public void oriHasDataExpNoData() throws Exception {
        testQueryHistoryDataFromInitialNode();
        testQueryAfterInsertNewData();
        testCapacityExpansion_oriHasDataExpNoData();
        testWriteAndQueryAfterCapacityExpansion_oriHasDataExpNoData();
    }

    @Test
    public void oriNoDataExpHasData() throws Exception {
        testQueryHistoryDataFromNoInitialNode();
        testQueryAfterInsertNewDataFromNoInitialNode();
        testCapacityExpansion_oriNoDataExpHasData();
        testWriteAndQueryAfterCapacityExpansion_oriNoDataExpHasData();
    }

    @Test
    public void oriNoDataExpNoData() throws Exception {
        testQueryHistoryDataFromNoInitialNode();
        testQueryAfterInsertNewDataFromNoInitialNode();
        testCapacityExpansion_oriNoDataExpNoData();
        testWriteAndQueryAfterCapacityExpansion_oriNoDataExpNoData();
    }

    private void addStorageEngine(boolean hasData) throws SessionException, ExecutionException {
        if (ENGINE_TYPE.toLowerCase().contains("iotdb"))
            session.executeSql(
                    "ADD STORAGEENGINE (\"127.0.0.1\", 6668, \""
                            + ENGINE_TYPE
                            + "\", \"username:root, password:root, sessionPoolSize:20, has_data:"
                            + hasData
                            + ", is_read_only:true\");");
        else if (ENGINE_TYPE.toLowerCase().contains("influxdb"))
            session.executeSql(
                    "ADD STORAGEENGINE (\"127.0.0.1\", 8087, \""
                            + ENGINE_TYPE
                            + "\", \"url:http://localhost:8087/, username:user, password:12345678, sessionPoolSize:20, has_data:"
                            + hasData
                            + ", is_read_only:true, token:testToken, organization:testOrg\");");
        else if (ENGINE_TYPE.toLowerCase().contains("parquet"))
            session.executeSql(
                    "ADD STORAGEENGINE (\"127.0.0.1\", 6668, \""
                            + ENGINE_TYPE
                            + "\", \"username:root, password:root, sessionPoolSize:20, has_data:"
                            + hasData
                            + ", is_read_only:true\");");
        else {
            logger.error("not support the DB: {}", ENGINE_TYPE);
            fail();
        }
    }

    // @Test
    public void testQueryHistoryDataFromInitialNode() throws Exception {
        String statement = "select * from ln";
        String expect =
                "ResultSets:\n"
                        + "+---+-------------------+------------------------+\n"
                        + "|key|ln.wf01.wt01.status|ln.wf01.wt01.temperature|\n"
                        + "+---+-------------------+------------------------+\n"
                        + "|100|               true|                    null|\n"
                        + "|200|              false|                   20.71|\n"
                        + "+---+-------------------+------------------------+\n"
                        + "Total line number = 2\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select count(*) from ln.wf01";
        expect =
                "ResultSets:\n"
                        + "+--------------------------+-------------------------------+\n"
                        + "|count(ln.wf01.wt01.status)|count(ln.wf01.wt01.temperature)|\n"
                        + "+--------------------------+-------------------------------+\n"
                        + "|                         2|                              1|\n"
                        + "+--------------------------+-------------------------------+\n"
                        + "Total line number = 1\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testQueryHistoryDataFromNoInitialNode() throws Exception {
        String statement = "select * from ln";
        String expect =
                "ResultSets:\n" + "+---+\n" + "|key|\n" + "+---+\n" + "+---+\n" + "Empty set.\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    // @Test
    public void testQueryAfterInsertNewData() throws Exception {
        session.executeSql(
                "insert into ln.wf02 (key, status, version) values (100, true, \"v1\");");
        session.executeSql(
                "insert into ln.wf02 (key, status, version) values (400, false, \"v4\");");
        session.executeSql("insert into ln.wf02 (key, version) values (800, \"v8\");");

        String statement = "select * from ln";
        String expect =
                "ResultSets:\n"
                        + "+---+-------------------+------------------------+--------------+---------------+\n"
                        + "|key|ln.wf01.wt01.status|ln.wf01.wt01.temperature|ln.wf02.status|ln.wf02.version|\n"
                        + "+---+-------------------+------------------------+--------------+---------------+\n"
                        + "|100|               true|                    null|          true|             v1|\n"
                        + "|200|              false|                   20.71|          null|           null|\n"
                        + "|400|               null|                    null|         false|             v4|\n"
                        + "|800|               null|                    null|          null|             v8|\n"
                        + "+---+-------------------+------------------------+--------------+---------------+\n"
                        + "Total line number = 4\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select count(*) from ln.wf02";
        expect =
                "ResultSets:\n"
                        + "+---------------------+----------------------+\n"
                        + "|count(ln.wf02.status)|count(ln.wf02.version)|\n"
                        + "+---------------------+----------------------+\n"
                        + "|                    2|                     3|\n"
                        + "+---------------------+----------------------+\n"
                        + "Total line number = 1\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testQueryAfterInsertNewDataFromNoInitialNode() throws Exception {
        session.executeSql(
                "insert into ln.wf02 (key, status, version) values (100, true, \"v1\");");
        session.executeSql(
                "insert into ln.wf02 (key, status, version) values (400, false, \"v4\");");
        session.executeSql("insert into ln.wf02 (key, version) values (800, \"v8\");");

        String statement = "select * from ln";
        String expect =
                "ResultSets:\n"
                        + "+---+--------------+---------------+\n"
                        + "|key|ln.wf02.status|ln.wf02.version|\n"
                        + "+---+--------------+---------------+\n"
                        + "|100|          true|             v1|\n"
                        + "|400|         false|             v4|\n"
                        + "|800|          null|             v8|\n"
                        + "+---+--------------+---------------+\n"
                        + "Total line number = 3\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select count(*) from ln.wf02";
        expect =
                "ResultSets:\n"
                        + "+---------------------+----------------------+\n"
                        + "|count(ln.wf02.status)|count(ln.wf02.version)|\n"
                        + "+---------------------+----------------------+\n"
                        + "|                    2|                     3|\n"
                        + "+---------------------+----------------------+\n"
                        + "Total line number = 1\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    // @Test
    public void testCapacityExpansion_oriHasDataExpNoData() throws Exception {
        addStorageEngine(false);
        String statement = "select * from ln.wf03";
        String expect =
                "ResultSets:\n" + "+---+\n" + "|key|\n" + "+---+\n" + "+---+\n" + "Empty set.\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select * from ln";
        expect =
                "ResultSets:\n"
                        + "+---+-------------------+------------------------+--------------+---------------+\n"
                        + "|key|ln.wf01.wt01.status|ln.wf01.wt01.temperature|ln.wf02.status|ln.wf02.version|\n"
                        + "+---+-------------------+------------------------+--------------+---------------+\n"
                        + "|100|               true|                    null|          true|             v1|\n"
                        + "|200|              false|                   20.71|          null|           null|\n"
                        + "|400|               null|                    null|         false|             v4|\n"
                        + "|800|               null|                    null|          null|             v8|\n"
                        + "+---+-------------------+------------------------+--------------+---------------+\n"
                        + "Total line number = 4\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testCapacityExpansion_oriHasDataExpHasData() throws Exception {
        addStorageEngine(true);
        String statement = "select * from ln.wf03";
        String expect =
                "ResultSets:\n"
                        + "+---+-------------------+------------------------+\n"
                        + "|key|ln.wf03.wt01.status|ln.wf03.wt01.temperature|\n"
                        + "+---+-------------------+------------------------+\n"
                        + "| 77|               true|                    null|\n"
                        + "|200|              false|                   77.71|\n"
                        + "+---+-------------------+------------------------+\n"
                        + "Total line number = 2\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select * from ln";
        expect =
                "ResultSets:\n"
                        + "+---+-------------------+------------------------+--------------+---------------+-------------------+------------------------+\n"
                        + "|key|ln.wf01.wt01.status|ln.wf01.wt01.temperature|ln.wf02.status|ln.wf02.version|ln.wf03.wt01.status|ln.wf03.wt01.temperature|\n"
                        + "+---+-------------------+------------------------+--------------+---------------+-------------------+------------------------+\n"
                        + "| 77|               null|                    null|          null|           null|               true|                    null|\n"
                        + "|100|               true|                    null|          true|             v1|               null|                    null|\n"
                        + "|200|              false|                   20.71|          null|           null|              false|                   77.71|\n"
                        + "|400|               null|                    null|         false|             v4|               null|                    null|\n"
                        + "|800|               null|                    null|          null|             v8|               null|                    null|\n"
                        + "+---+-------------------+------------------------+--------------+---------------+-------------------+------------------------+\n"
                        + "Total line number = 5\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testCapacityExpansion_oriNoDataExpHasData() throws Exception {
        addStorageEngine(true);
        String statement = "select * from ln.wf03";
        String expect =
                "ResultSets:\n"
                        + "+---+-------------------+------------------------+\n"
                        + "|key|ln.wf03.wt01.status|ln.wf03.wt01.temperature|\n"
                        + "+---+-------------------+------------------------+\n"
                        + "| 77|               true|                    null|\n"
                        + "|200|              false|                   77.71|\n"
                        + "+---+-------------------+------------------------+\n"
                        + "Total line number = 2\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select * from ln";
        expect =
                "ResultSets:\n"
                        + "+---+--------------+---------------+-------------------+------------------------+\n"
                        + "|key|ln.wf02.status|ln.wf02.version|ln.wf03.wt01.status|ln.wf03.wt01.temperature|\n"
                        + "+---+--------------+---------------+-------------------+------------------------+\n"
                        + "| 77|          null|           null|               true|                    null|\n"
                        + "|100|          true|             v1|               null|                    null|\n"
                        + "|200|          null|           null|              false|                   77.71|\n"
                        + "|400|         false|             v4|               null|                    null|\n"
                        + "|800|          null|             v8|               null|                    null|\n"
                        + "+---+--------------+---------------+-------------------+------------------------+\n"
                        + "Total line number = 5\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testCapacityExpansion_oriNoDataExpNoData() throws Exception {
        addStorageEngine(false);
        String statement = "select * from ln.wf03";
        String expect =
                "ResultSets:\n" + "+---+\n" + "|key|\n" + "+---+\n" + "+---+\n" + "Empty set.\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select * from ln";
        expect =
                "ResultSets:\n"
                        + "+---+--------------+---------------+\n"
                        + "|key|ln.wf02.status|ln.wf02.version|\n"
                        + "+---+--------------+---------------+\n"
                        + "|100|          true|             v1|\n"
                        + "|400|         false|             v4|\n"
                        + "|800|          null|             v8|\n"
                        + "+---+--------------+---------------+\n"
                        + "Total line number = 3\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    // @Test
    public void testWriteAndQueryAfterCapacityExpansion_oriHasDataExpHasData() throws Exception {
        session.executeSql("insert into ln.wf02 (key, version) values (1600, \"v48\");");

        String statement = "select * from ln";
        String expect =
                "ResultSets:\n"
                        + "+----+-------------------+------------------------+--------------+---------------+-------------------+------------------------+\n"
                        + "| key|ln.wf01.wt01.status|ln.wf01.wt01.temperature|ln.wf02.status|ln.wf02.version|ln.wf03.wt01.status|ln.wf03.wt01.temperature|\n"
                        + "+----+-------------------+------------------------+--------------+---------------+-------------------+------------------------+\n"
                        + "|  77|               null|                    null|          null|           null|               true|                    null|\n"
                        + "| 100|               true|                    null|          true|             v1|               null|                    null|\n"
                        + "| 200|              false|                   20.71|          null|           null|              false|                   77.71|\n"
                        + "| 400|               null|                    null|         false|             v4|               null|                    null|\n"
                        + "| 800|               null|                    null|          null|             v8|               null|                    null|\n"
                        + "|1600|               null|                    null|          null|            v48|               null|                    null|\n"
                        + "+----+-------------------+------------------------+--------------+---------------+-------------------+------------------------+\n"
                        + "Total line number = 6\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select count(*) from ln.wf02";
        expect =
                "ResultSets:\n"
                        + "+---------------------+----------------------+\n"
                        + "|count(ln.wf02.status)|count(ln.wf02.version)|\n"
                        + "+---------------------+----------------------+\n"
                        + "|                    2|                     4|\n"
                        + "+---------------------+----------------------+\n"
                        + "Total line number = 1\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testWriteAndQueryAfterCapacityExpansion_oriNoDataExpHasData() throws Exception {
        session.executeSql("insert into ln.wf02 (key, version) values (1600, \"v48\");");

        String statement = "select * from ln";
        String expect =
                "ResultSets:\n"
                        + "+----+--------------+---------------+-------------------+------------------------+\n"
                        + "| key|ln.wf02.status|ln.wf02.version|ln.wf03.wt01.status|ln.wf03.wt01.temperature|\n"
                        + "+----+--------------+---------------+-------------------+------------------------+\n"
                        + "|  77|          null|           null|               true|                    null|\n"
                        + "| 100|          true|             v1|               null|                    null|\n"
                        + "| 200|          null|           null|              false|                   77.71|\n"
                        + "| 400|         false|             v4|               null|                    null|\n"
                        + "| 800|          null|             v8|               null|                    null|\n"
                        + "|1600|          null|            v48|               null|                    null|\n"
                        + "+----+--------------+---------------+-------------------+------------------------+\n"
                        + "Total line number = 6\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select count(*) from ln.wf02";
        expect =
                "ResultSets:\n"
                        + "+---------------------+----------------------+\n"
                        + "|count(ln.wf02.status)|count(ln.wf02.version)|\n"
                        + "+---------------------+----------------------+\n"
                        + "|                    2|                     4|\n"
                        + "+---------------------+----------------------+\n"
                        + "Total line number = 1\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testWriteAndQueryAfterCapacityExpansion_oriHasDataExpNoData() throws Exception {
        session.executeSql("insert into ln.wf02 (key, version) values (1600, \"v48\");");

        String statement = "select * from ln";
        String expect =
                "ResultSets:\n"
                        + "+----+-------------------+------------------------+--------------+---------------+\n"
                        + "| key|ln.wf01.wt01.status|ln.wf01.wt01.temperature|ln.wf02.status|ln.wf02.version|\n"
                        + "+----+-------------------+------------------------+--------------+---------------+\n"
                        + "| 100|               true|                    null|          true|             v1|\n"
                        + "| 200|              false|                   20.71|          null|           null|\n"
                        + "| 400|               null|                    null|         false|             v4|\n"
                        + "| 800|               null|                    null|          null|             v8|\n"
                        + "|1600|               null|                    null|          null|            v48|\n"
                        + "+----+-------------------+------------------------+--------------+---------------+\n"
                        + "Total line number = 5\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select count(*) from ln.wf02";
        expect =
                "ResultSets:\n"
                        + "+---------------------+----------------------+\n"
                        + "|count(ln.wf02.status)|count(ln.wf02.version)|\n"
                        + "+---------------------+----------------------+\n"
                        + "|                    2|                     4|\n"
                        + "+---------------------+----------------------+\n"
                        + "Total line number = 1\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    public void testWriteAndQueryAfterCapacityExpansion_oriNoDataExpNoData() throws Exception {
        session.executeSql("insert into ln.wf02 (key, version) values (1600, \"v48\");");

        String statement = "select * from ln";
        String expect =
                "ResultSets:\n"
                        + "+----+--------------+---------------+\n"
                        + "| key|ln.wf02.status|ln.wf02.version|\n"
                        + "+----+--------------+---------------+\n"
                        + "| 100|          true|             v1|\n"
                        + "| 400|         false|             v4|\n"
                        + "| 800|          null|             v8|\n"
                        + "|1600|          null|            v48|\n"
                        + "+----+--------------+---------------+\n"
                        + "Total line number = 4\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select count(*) from ln.wf02";
        expect =
                "ResultSets:\n"
                        + "+---------------------+----------------------+\n"
                        + "|count(ln.wf02.status)|count(ln.wf02.version)|\n"
                        + "+---------------------+----------------------+\n"
                        + "|                    2|                     4|\n"
                        + "+---------------------+----------------------+\n"
                        + "Total line number = 1\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }
}
