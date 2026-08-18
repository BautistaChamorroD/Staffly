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
        assertThat(indexNames("BRANCH")).contains("IDX_BRANCH_COMPANY");
    }

    @Test
    void appUserHasIndexOnCompanyId() throws Exception {
        assertThat(indexNames("APP_USER")).contains("IDX_APP_USER_COMPANY");
    }

    private Set<String> indexNames(String tableName) throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName != null) {
                        names.add(indexName.toUpperCase());
                    }
                }
            }
        }
        return names;
    }
}
