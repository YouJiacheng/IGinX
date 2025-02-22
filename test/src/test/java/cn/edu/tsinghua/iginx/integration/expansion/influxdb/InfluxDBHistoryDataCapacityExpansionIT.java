package cn.edu.tsinghua.iginx.integration.expansion.influxdb;

import cn.edu.tsinghua.iginx.integration.expansion.CapacityExpansionIT;
import cn.edu.tsinghua.iginx.integration.expansion.unit.SQLTestTools;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfluxDBHistoryDataCapacityExpansionIT extends CapacityExpansionIT {
    private static final Logger logger =
            LoggerFactory.getLogger(InfluxDBHistoryDataCapacityExpansionIT.class);

    public InfluxDBHistoryDataCapacityExpansionIT() {
        super("influxdb");
    }

    @Test
    public void testSchemaPrefix() throws Exception {
        session.executeSql(
                "ADD STORAGEENGINE (\"127.0.0.1\", 8060, \""
                        + ENGINE_TYPE
                        + "\", \"url:http://localhost:8086/ , username:user, password:12345678, sessionPoolSize:20, schema_prefix:expansion, has_data:true, is_read_only:true, token:testToken, organization:testOrg\");");

        String statement = "select * from expansion.data_center";
        String expect =
                "ResultSets:\n"
                        + "+-------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+\n"
                        + "|          key|expansion.data_center.cpu.temperature{host=1,rack=A,room=ROOMA}|expansion.data_center.cpu.temperature{host=2,rack=B,room=ROOMA}|expansion.data_center.cpu.temperature{host=4,rack=B,room=ROOMB}|expansion.data_center.cpu.usage{host=1,rack=A,room=ROOMA}|expansion.data_center.cpu.usage{host=2,rack=B,room=ROOMA}|expansion.data_center.cpu.usage{host=4,rack=B,room=ROOMB}|\n"
                        + "+-------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+\n"
                        + "|1000000000000|                                                           56.4|                                                           55.1|                                                           null|                                                     66.3|                                                     72.1|                                                     null|\n"
                        + "|1300000000000|                                                           56.2|                                                           null|                                                           99.8|                                                     67.1|                                                     null|                                                     22.1|\n"
                        + "+-------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+\n"
                        + "Total line number = 2\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }

    @Test
    public void testAddSameDataPrefixWithDiffSchemaPrefix() throws Exception {
        session.executeSql(
                "ADD STORAGEENGINE (\"127.0.0.1\", 8060, \""
                        + ENGINE_TYPE
                        + "\", \"url:http://localhost:8086/ , username:user, password:12345678, sessionPoolSize:20, schema_prefix:expansion, data_prefix:data_center, has_data:true, is_read_only:true, token:testToken, organization:testOrg\");");
        session.executeSql(
                "ADD STORAGEENGINE (\"127.0.0.1\", 8060, \""
                        + ENGINE_TYPE
                        + "\", \"url:http://localhost:8086/ , username:user, password:12345678, sessionPoolSize:20, schema_prefix:expansion2, data_prefix:data_center, has_data:true, is_read_only:true, token:testToken, organization:testOrg\");");

        String statement = "select * from expansion.data_center";
        String expect =
                "ResultSets:\n"
                        + "+-------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+\n"
                        + "|          key|expansion.data_center.cpu.temperature{host=1,rack=A,room=ROOMA}|expansion.data_center.cpu.temperature{host=2,rack=B,room=ROOMA}|expansion.data_center.cpu.temperature{host=4,rack=B,room=ROOMB}|expansion.data_center.cpu.usage{host=1,rack=A,room=ROOMA}|expansion.data_center.cpu.usage{host=2,rack=B,room=ROOMA}|expansion.data_center.cpu.usage{host=4,rack=B,room=ROOMB}|\n"
                        + "+-------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+\n"
                        + "|1000000000000|                                                           56.4|                                                           55.1|                                                           null|                                                     66.3|                                                     72.1|                                                     null|\n"
                        + "|1300000000000|                                                           56.2|                                                           null|                                                           99.8|                                                     67.1|                                                     null|                                                     22.1|\n"
                        + "+-------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+---------------------------------------------------------+\n"
                        + "Total line number = 2\n";
        SQLTestTools.executeAndCompare(session, statement, expect);

        statement = "select * from expansion2.data_center";
        expect =
                "ResultSets:\n"
                        + "+-------------+----------------------------------------------------------------+----------------------------------------------------------------+----------------------------------------------------------------+----------------------------------------------------------+----------------------------------------------------------+----------------------------------------------------------+\n"
                        + "|          key|expansion2.data_center.cpu.temperature{host=1,rack=A,room=ROOMA}|expansion2.data_center.cpu.temperature{host=2,rack=B,room=ROOMA}|expansion2.data_center.cpu.temperature{host=4,rack=B,room=ROOMB}|expansion2.data_center.cpu.usage{host=1,rack=A,room=ROOMA}|expansion2.data_center.cpu.usage{host=2,rack=B,room=ROOMA}|expansion2.data_center.cpu.usage{host=4,rack=B,room=ROOMB}|\n"
                        + "+-------------+----------------------------------------------------------------+----------------------------------------------------------------+----------------------------------------------------------------+----------------------------------------------------------+----------------------------------------------------------+----------------------------------------------------------+\n"
                        + "|1000000000000|                                                            56.4|                                                            55.1|                                                            null|                                                      66.3|                                                      72.1|                                                      null|\n"
                        + "|1300000000000|                                                            56.2|                                                            null|                                                            99.8|                                                      67.1|                                                      null|                                                      22.1|\n"
                        + "+-------------+----------------------------------------------------------------+----------------------------------------------------------------+----------------------------------------------------------------+----------------------------------------------------------+----------------------------------------------------------+----------------------------------------------------------+\n"
                        + "Total line number = 2\n";
        SQLTestTools.executeAndCompare(session, statement, expect);
    }
}
