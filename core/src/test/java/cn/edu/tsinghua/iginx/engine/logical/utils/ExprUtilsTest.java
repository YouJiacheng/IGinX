package cn.edu.tsinghua.iginx.engine.logical.utils;

import static org.junit.Assert.assertEquals;

import cn.edu.tsinghua.iginx.engine.shared.TimeRange;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.*;
import cn.edu.tsinghua.iginx.exceptions.SQLParserException;
import cn.edu.tsinghua.iginx.metadata.entity.TimeSeriesInterval;
import cn.edu.tsinghua.iginx.sql.TestUtils;
import cn.edu.tsinghua.iginx.sql.statement.DeleteStatement;
import cn.edu.tsinghua.iginx.sql.statement.SelectStatement;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.Test;

class InfluxDBFilterTransformer {

    public static String toString(Filter filter) {
        switch (filter.getType()) {
            case And:
                return toString((AndFilter) filter);
            case Or:
                return toString((OrFilter) filter);
            case Not:
                return toString((NotFilter) filter);
            case Value:
                return toString((ValueFilter) filter);
            case Key:
                return toString((KeyFilter) filter);
            default:
                return "";
        }
    }

    private static String toString(AndFilter filter) {
        return filter.getChildren()
                .stream()
                .map(InfluxDBFilterTransformer::toString)
                .collect(Collectors.joining(" and ", "(", ")"));
    }

    private static String toString(NotFilter filter) {
        return "not " + filter.toString();
    }

    private static String toString(KeyFilter filter) {
        return "time " + Op.op2Str(filter.getOp()) + " " + filter.getValue();
    }

    private static String toString(ValueFilter filter) {
        return filter.getPath()
                + " "
                + Op.op2Str(filter.getOp())
                + " "
                + filter.getValue().getValue();
    }

    private static String toString(OrFilter filter) {
        return filter.getChildren()
                .stream()
                .map(InfluxDBFilterTransformer::toString)
                .collect(Collectors.joining(" or ", "(", ")"));
    }
}

public class ExprUtilsTest {
    @Test
    public void testRemoveNot() {
        String select = "SELECT a FROM root WHERE !(a != 10);";
        SelectStatement statement = (SelectStatement) TestUtils.buildStatement(select);
        Filter filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.removeNot(filter).toString());

        select = "SELECT a FROM root WHERE !(!(a != 10));";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.removeNot(filter).toString());

        select = "SELECT a FROM root WHERE !(a > 5 AND b <= 10 AND c > 7 AND d == 8);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.removeNot(filter).toString());

        select = "SELECT a FROM root WHERE !(a > 5 AND b <= 10 or c > 7 AND d == 8);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.removeNot(filter).toString());
    }

    @Test
    public void testToDNF() {
        String select = "SELECT a FROM root WHERE a > 5 AND b <= 10 OR c > 7 AND d == 8;";
        SelectStatement statement = (SelectStatement) TestUtils.buildStatement(select);
        Filter filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.toDNF(filter).toString());

        select = "SELECT a FROM root WHERE (a > 5 OR b <= 10) AND (c > 7 OR d == 8);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.toDNF(filter).toString());

        select =
                "SELECT a FROM root WHERE (a > 5 OR b <= 10) AND (c > 7 OR d == 8) AND (e < 3 OR f != 2);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.toDNF(filter).toString());

        select = "SELECT a FROM root WHERE (a > 5 AND b <= 10) AND (c > 7 OR d == 8);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(ExprUtils.toDNF(filter).toString());
    }

    @Test
    public void testToCNF() {
        String select = "SELECT a FROM root WHERE a > 5 OR b <= 10 AND c > 7 OR d == 8;";
        SelectStatement statement = (SelectStatement) TestUtils.buildStatement(select);
        Filter filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(InfluxDBFilterTransformer.toString(filter));
        System.out.println(ExprUtils.toCNF(filter).toString());
        System.out.println(InfluxDBFilterTransformer.toString(ExprUtils.toCNF(filter)));

        select = "SELECT a FROM root WHERE (a > 5 AND b <= 10) OR (c > 7 AND d == 8);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(InfluxDBFilterTransformer.toString(filter));
        System.out.println(ExprUtils.toCNF(filter).toString());
        System.out.println(InfluxDBFilterTransformer.toString(ExprUtils.toCNF(filter)));

        select =
                "SELECT a FROM root WHERE (a > 5 AND b <= 10) OR (c > 7 OR d == 8) OR (e < 3 AND f != 2);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(InfluxDBFilterTransformer.toString(filter));
        System.out.println(ExprUtils.toCNF(filter).toString());
        System.out.println(InfluxDBFilterTransformer.toString(ExprUtils.toCNF(filter)));

        select = "SELECT a FROM root WHERE (a > 5 OR b <= 10) OR (c > 7 AND d == 8);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        System.out.println(filter.toString());
        System.out.println(InfluxDBFilterTransformer.toString(filter));
        System.out.println(ExprUtils.toCNF(filter).toString());
        System.out.println(InfluxDBFilterTransformer.toString(ExprUtils.toCNF(filter)));
    }

    @Test
    public void testTimeRange() {
        String delete =
                "DELETE FROM root.a WHERE (key > 5 AND key <= 10) OR (key > 12 AND key < 15);";
        DeleteStatement statement = (DeleteStatement) TestUtils.buildStatement(delete);
        assertEquals(
                Arrays.asList(new TimeRange(6, 11), new TimeRange(13, 15)),
                statement.getTimeRanges());

        delete =
                "DELETE FROM root.a WHERE (key > 1 AND key <= 8) OR (key >= 5 AND key < 11) OR key >= 66;";
        statement = (DeleteStatement) TestUtils.buildStatement(delete);
        assertEquals(
                Arrays.asList(new TimeRange(2, 11), new TimeRange(66, Long.MAX_VALUE)),
                statement.getTimeRanges());

        delete = "DELETE FROM root.a WHERE key >= 16 AND key < 61;";
        statement = (DeleteStatement) TestUtils.buildStatement(delete);
        assertEquals(Collections.singletonList(new TimeRange(16, 61)), statement.getTimeRanges());

        delete = "DELETE FROM root.a WHERE key >= 16;";
        statement = (DeleteStatement) TestUtils.buildStatement(delete);
        assertEquals(
                Collections.singletonList(new TimeRange(16, Long.MAX_VALUE)),
                statement.getTimeRanges());

        delete = "DELETE FROM root.a WHERE key < 61;";
        statement = (DeleteStatement) TestUtils.buildStatement(delete);
        assertEquals(Collections.singletonList(new TimeRange(0, 61)), statement.getTimeRanges());

        delete = "DELETE FROM root.a;";
        statement = (DeleteStatement) TestUtils.buildStatement(delete);
        assertEquals(
                Collections.singletonList(new TimeRange(0, Long.MAX_VALUE)),
                statement.getTimeRanges());
    }

    @Test(expected = SQLParserException.class)
    public void testErrDelete() {
        String delete = "DELETE FROM root.a WHERE key < 61 AND key > 616;";
        TestUtils.buildStatement(delete);
    }

    @Test
    public void testGetSubFilterFromFragment() {
        // sub1
        String select =
                "SELECT a FROM root WHERE (a > 5 OR d < 15) AND !(e < 27) AND (c < 10 OR b > 2) AND key > 10 AND key <= 100;";
        SelectStatement statement = (SelectStatement) TestUtils.buildStatement(select);
        Filter filter = statement.getFilter();
        assertEquals(
                "((root.a > 5 || root.d < 15) && !(root.e < 27) && (root.c < 10 || root.b > 2) && key > 10 && key <= 100)",
                filter.toString());
        assertEquals(
                "(key > 10 && key <= 100)",
                ExprUtils.getSubFilterFromFragment(
                                filter, new TimeSeriesInterval("root.a", "root.c"))
                        .toString());

        // sub2
        select =
                "SELECT a FROM root WHERE (a > 5 OR d < 15) AND !(e < 27) AND (c < 10 OR b > 2) AND key > 10 AND key <= 100;";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        assertEquals(
                "((root.a > 5 || root.d < 15) && !(root.e < 27) && (root.c < 10 || root.b > 2) && key > 10 && key <= 100)",
                filter.toString());
        assertEquals(
                "(root.e >= 27 && key > 10 && key <= 100)",
                ExprUtils.getSubFilterFromFragment(
                                filter, new TimeSeriesInterval("root.c", "root.z"))
                        .toString());

        // whole
        select = "SELECT a FROM root WHERE (a > 5 OR d < 15) AND !(e < 27) AND (c < 10 OR b > 2);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        assertEquals(
                "((root.a > 5 || root.d < 15) && !(root.e < 27) && (root.c < 10 || root.b > 2))",
                filter.toString());
        assertEquals(
                "((root.a > 5 || root.d < 15) && root.e >= 27 && (root.c < 10 || root.b > 2))",
                ExprUtils.getSubFilterFromFragment(
                                filter, new TimeSeriesInterval("root.a", "root.z"))
                        .toString());

        // empty
        select = "SELECT a FROM root WHERE (a > 5 OR d < 15) AND !(e < 27) AND (c < 10 OR b > 2);";
        statement = (SelectStatement) TestUtils.buildStatement(select);
        filter = statement.getFilter();
        assertEquals(
                "((root.a > 5 || root.d < 15) && !(root.e < 27) && (root.c < 10 || root.b > 2))",
                filter.toString());
        assertEquals(
                "True",
                ExprUtils.getSubFilterFromFragment(
                                filter, new TimeSeriesInterval("root.h", "root.z"))
                        .toString());
    }
}
