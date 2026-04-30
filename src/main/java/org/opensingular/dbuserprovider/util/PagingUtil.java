package org.opensingular.dbuserprovider.util;

import org.opensingular.dbuserprovider.persistence.RDBMS;

/**
 * Utilitário de paginação implementado sem dependência do Hibernate.
 *
 * As versões anteriores deste projeto utilizavam {@code org.hibernate.dialect.Dialect}
 * e {@code org.hibernate.engine.spi.RowSelection} para gerar a porção de
 * paginação da query, mas essas APIs foram removidas/alteradas no Hibernate 6
 * (utilizado pelo Keycloak 26). Portanto, agora geramos as cláusulas de
 * paginação manualmente por RDBMS.
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
     * Anexa a cláusula de paginação adequada ao RDBMS no final da query.
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

        // Remove ; final, se houver, antes de anexar paginação
        String base = query.trim();
        if (base.endsWith(";")) {
            base = base.substring(0, base.length() - 1);
        }

        if (rdbms == null) {
            // Fallback: padrão ANSI SQL
            return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
        }

        switch (rdbms) {
            case POSTGRESQL:
            case MYSQL:
                return base + " LIMIT " + max + " OFFSET " + first;
            case ORACLE:
                // Oracle 12c+ suporta OFFSET ... FETCH
                return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
            case IBMDB2:
                // DB2 LUW recente suporta OFFSET ... FETCH
                return base + " OFFSET " + first + " ROWS FETCH NEXT " + max + " ROWS ONLY";
            case SQL_SERVER:
            case SQL_SERVER_JTDS:
                // SQL Server 2012+ suporta OFFSET ... FETCH, mas requer ORDER BY na query.
                // Caso a query do usuário não tenha ORDER BY, adicionamos um fallback usando a coluna 1.
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
