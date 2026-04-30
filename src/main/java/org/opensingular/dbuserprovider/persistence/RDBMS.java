package org.opensingular.dbuserprovider.persistence;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enumeração dos RDBMS suportados.
 *
 * As referências aos Dialects do Hibernate foram removidas porque, a partir do
 * Hibernate 6 (utilizado pelo Keycloak 26), os Dialects "versionados"
 * (PostgreSQL10Dialect, MySQL57Dialect, Oracle12cDialect, SQLServer2012Dialect)
 * foram removidos. Para evitar acoplamento com a API interna do Hibernate, a
 * paginação agora é feita manualmente em {@link org.opensingular.dbuserprovider.util.PagingUtil}.
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
