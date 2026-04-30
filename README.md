# singular-keycloak-database-federation (port para Keycloak 26)

Port do plugin original `opensingular/singular-keycloak-database-federation` para o **Keycloak 26**, seguindo a abordagem recomendada pela [documentacao oficial do Keycloak para User Storage SPI](https://www.keycloak.org/docs/latest/server_development/index.html#_user-storage-spi).

## Arquitetura do build

Conforme a documentacao oficial, providers do Keycloak 26 sao deployados como JARs simples em `/opt/keycloak/providers/`. Nao usamos fat-jar (shade). O build gera, em `dist/`:

- `singular-user-storage-provider.jar` - o provider em si (so as classes do plugin)
- HikariCP, BCrypt, drivers JDBC (PostgreSQL, MySQL, Oracle, jtds), commons-codec, commons-io, commons-lang3 - como JARs separados

Todos eles sao copiados juntos para `/opt/keycloak/providers/`. O Keycloak os carrega via classloader plano.

## Mudancas em relacao ao Keycloak 17

| Item | Antes | Agora |
| --- | --- | --- |
| Java | 1.8 | 17 |
| Keycloak SPI | 17.0.1 | 26.0.5 |
| `UserLookupProvider` | `getUserById(String, RealmModel)` | `getUserById(RealmModel, String)` |
| `UserQueryProvider` | retornava `List<UserModel>` | retorna `Stream<UserModel>` |
| Hibernate Dialects | usados em `RDBMS`/`PagingUtil` | removidos (extintos no Hibernate 6) |
| Drivers JDBC | mysql-connector-java 8.0.12, ojdbc8 | mysql-connector-j 8.4.0, ojdbc11 |
| HikariCP | 4.0.3 | 5.1.0 |

## Pre-requisitos

- JDK 17+
- Maven 3.8+

## Como compilar

```bash
mvn clean package
```

Resultado em `dist/`: o JAR do provider mais ~10 JARs de dependencias runtime.

## Como instalar no Keycloak 26

1. Pare o Keycloak.
2. Copie **todos** os JARs de `dist/` para `/opt/keycloak/providers/`:

   ```bash
   cp dist/*.jar /opt/keycloak/providers/
   ```

3. Rode o build do Keycloak:

   ```bash
   /opt/keycloak/bin/kc.sh build
   ```

4. Inicie o Keycloak. Em **User Federation -> Add provider**, vai aparecer `singular-db-user-provider`.

## Testar com Docker

```bash
docker build -t keycloak-singular .
docker run --rm -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  keycloak-singular start-dev
```

Acesse http://localhost:8080 (admin/admin).

## Stack completa de teste (Keycloak + PostgreSQL)

```bash
docker compose up --build
```

Sobe um PostgreSQL com tabela `users` de exemplo (vide `docker-compose/init.sql`) e o Keycloak ja com o provider instalado.

Configure no provider:
- **JDBC URL**: `jdbc:postgresql://userdb:5432/userdb`
- **User**: `userdb`
- **Password**: `userdb`
- **RDBMS**: `PostgreSQL`

## Notas tecnicas

### Por que `maven-compiler-plugin:3.11.0` e nao 3.13?

A versao 3.12+ do compiler-plugin ativa o Java modulepath automaticamente quando detecta `module-info` em alguma dependencia (HikariCP 5+ tem). Quando o modulepath e ativado, o JAR `keycloak-server-spi-private` e silenciosamente descartado porque seu nome contem `private`, palavra reservada Java, fazendo a derivacao de Automatic-Module-Name falhar. Resultado: erros falsos de "cannot find symbol" para classes que estao comprovadamente no classpath. Fixar 3.11.0 mantem o classpath classico.

### Paginacao sem Hibernate

O Keycloak 26 usa Hibernate 6, que removeu os Dialects versionados (`PostgreSQL10Dialect`, `MySQL57Dialect`, etc) que o codigo original usava. Reescrevi `PagingUtil` para gerar `LIMIT/OFFSET` (Postgres/MySQL) e `OFFSET ... FETCH NEXT` (Oracle 12+, DB2, SQL Server 2012+) manualmente.

## Licenca

Apache 2.0 (mesma do projeto original).
