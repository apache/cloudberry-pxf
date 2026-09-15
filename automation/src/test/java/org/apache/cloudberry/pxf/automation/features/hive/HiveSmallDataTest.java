package org.apache.cloudberry.pxf.automation.features.hive;

import annotations.WorksWithFDW;
import org.apache.cloudberry.pxf.automation.components.hive.Hive;
import org.apache.cloudberry.pxf.automation.features.BaseFeature;
import org.apache.cloudberry.pxf.automation.structures.tables.basic.Table;
import org.apache.cloudberry.pxf.automation.structures.tables.hive.HiveTable;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.testng.annotations.Test;

import java.math.BigDecimal;

/** Basic Hive reads, ported from regression/sql/FDW_HiveSmokeTest.sql. */
@WorksWithFDW
public class HiveSmallDataTest extends BaseFeature {
    private Hive hive;
    private HiveTable hiveTable;

    @Test(groups = {"hive", "features"})
    public void readHiveTable() throws Exception {
        hive = (Hive) systemManager.getSystemObject("hive");
        hiveTable = TableFactory.getHiveByRowCommaTable("pxf_hive_small_data_types", new String[]{
                "name string", "num int", "dub double", "longNum bigint", "bool boolean"
        });
        hive.createTableAndVerify(hiveTable);

        Table data = getSmallData();
        for (int i = 1; i <= 100; i++) {
            data.getData().get(i - 1).set(2, BigDecimal.valueOf(i, 4).toPlainString());
        }
        String dataPath = hdfs.getWorkingDirectory() + "/hive_small_data_types.csv";
        hdfs.writeTableToFile(dataPath, data, ",");
        hive.loadData(hiveTable, dataPath, false);

        exTable = TableFactory.getPxfHiveReadableTable("pxf_hive_small_data_types", new String[]{
                "name text", "num integer", "dub double precision", "longNum bigint", "bool boolean"
        }, hiveTable, true);
        createTable(exTable);

        runSqlTest("features/hive/small_data_types");
    }

    @Override
    protected void afterClass() throws Exception {
        try {
            if (exTable != null) {
                gpdb.dropTable(exTable, true);
            }
            if (hiveTable != null) {
                hive.dropTable(hiveTable, false);
            }
        } finally {
            if (hive != null) {
                hive.close();
            }
        }
    }
}
