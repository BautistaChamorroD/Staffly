package com.staffly.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseIndexTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void branchHasIndexOnCompanyId() throws Exception {
        assertThat(indexedColumns("BRANCH")).contains("COMPANY_ID");
    }

    @Test
    void appUserHasIndexOnCompanyId() throws Exception {
        assertThat(indexedColumns("APP_USER")).contains("COMPANY_ID");
    }

    private Set<String> indexedColumns(String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false)) {
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    if (column != null) {
                        columns.add(column.toUpperCase());
                    }
                }
            }
        }
        return columns;
    }
}
