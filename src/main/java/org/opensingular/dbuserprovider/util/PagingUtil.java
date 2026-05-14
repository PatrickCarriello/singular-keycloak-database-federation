package org.opensingular.dbuserprovider.util;

import org.opensingular.dbuserprovider.persistence.RDBMS;

/**
 * Pagination utility implemented without Hibernate dependency.
 *
 * Previous versions of this project used {@code org.hibernate.dialect.Dialect}
 * and {@code org.hibernate.engine.spi.RowSelection} to generate the pagination
 * portion of queries, but those APIs were removed/changed in Hibernate 6
 * (used by Keycloak 26). Pagination clauses are now generated manually per RDBMS.
 */
public class PagingUtil {

    public static class Pageable {
        private final int firstResult;
        private final int maxResults;

        public Pageable(int firstResult, int maxResults) {
            this.firstResult = firstResult;
            this.maxResults = maxResults;
        }

        public int getFirstResult() {
            return firstResult;
        }

        public int getMaxResults() {
            return maxResults;
        }
    }

    /**
     * Appends the appropriate RDBMS pagination clause to the end of the query.
     */
    public static String formatScriptWithPageable(String query, Pageable pageable, RDBMS rdbms) {
        if (pageable == null) {
            return query;
        }

        int first = Math.max(pageable.getFirstResult(), 0);
        int max = pageable.getMaxResults();
        if (max <= 0) {
            return query;
        }

        // Remove trailing semicolon, if any, before appending pagination
        String base = query.trim();
        if (base.endsWith(";")) {
            base = base.substring(0, base.length() - 1);
        }

        if (rdbms == null) {
            // Fallback: ANSI SQL standard
            return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
        }

        switch (rdbms) {
            case POSTGRESQL:
            case MYSQL:
                return base + " LIMIT " + max + " OFFSET " + first;
            case ORACLE:
                // Oracle 12c+ supports OFFSET ... FETCH
                return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
            case IBMDB2:
                // Recent DB2 LUW supports OFFSET ... FETCH
                return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
            case SQL_SERVER:
            case SQL_SERVER_JTDS:
                // SQL Server 2012+ supports OFFSET ... FETCH but requires ORDER BY in the query.
                // If the user's query has no ORDER BY, we add a fallback using column 1.
                String upper = base.toUpperCase();
                if (!upper.contains(" ORDER BY ")) {
                    base = base + " ORDER BY 1";
                }
                return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
            default:
                return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
        }
    }
}
