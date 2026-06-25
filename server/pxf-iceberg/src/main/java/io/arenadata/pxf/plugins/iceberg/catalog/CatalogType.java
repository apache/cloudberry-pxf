package io.arenadata.pxf.plugins.iceberg.catalog;

import org.apache.commons.lang3.StringUtils;

public enum CatalogType {
    HIVE_METASTORE,
    REST,
    JDBC,
    HADOOP,
    ;


    public static CatalogType findByName(String name){
        if(StringUtils.isEmpty(name)){
            throw new IllegalArgumentException("Catalog type can't be empty");
        }
        return valueOf(name.toUpperCase());
    }
}
