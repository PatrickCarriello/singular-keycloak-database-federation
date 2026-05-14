package org.opensingular.dbuserprovider.persistence;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ENUM's of supported RDBMS.
 *
 * References to Hibernate Dialects were removed because, starting from
 * Hibernate 6 (used by Keycloak 26), the versioned Dialects
 * (PostgreSQL10Dialect, MySQL57Dialect, Oracle12cDialect, SQLServer2012Dialect)
 * were removed. To avoid coupling with Hibernate's internal API, pagination
 * is now handled manually in {@link org.opensingular.dbuserprovider.util.PagingUtil}.
 */
public enum RDBMS {
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", "SELECT 1"),
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "SELECT 1"),
    ORACLE("Oracle", "oracle.jdbc.OracleDriver", "SELECT 1 FROM DUAL"),
    IBMDB2("IBM DB2", "com.ibm.db2.jcc.DB2Driver", "select * from sysibm.sysdummy1"),
    SQL_SERVER_JTDS("MS SQL Server (jtds)", "net.sourceforge.jtds.jdbc.Driver", "SELECT 1"),
    SQL_SERVER("MS SQL Server (microsoft)", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "SELECT 1");

    private final String desc;
    private final String driver;
    private final String testString;

    RDBMS(String desc, String driver, String testString) {
        this.desc = desc;
        this.driver = driver;
        this.testString = testString;
    }

    public static RDBMS getByDescription(String desc) {
        for (RDBMS value : values()) {
            if (value.desc.equals(desc)) {
                return value;
            }
        }
        return null;
    }

    public static List<String> getAllDescriptions() {
        return Arrays.stream(values()).map(RDBMS::getDesc).collect(Collectors.toList());
    }

    public String getDesc() {
        return desc;
    }

    public String getDriver() {
        return driver;
    }

    public String getTestString() {
        return testString;
    }
}
