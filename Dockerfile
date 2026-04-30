# =============================================================================
# Stage 1: Build do plugin
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml ./
COPY src ./src

# Build do plugin (sem testes). Gera em /build/dist/:
#  - singular-user-storage-provider.jar  (o provider em si)
#  - HikariCP-*.jar, bcrypt-*.jar, postgresql-*.jar, mysql-connector-j-*.jar,
#    ojdbc11-*.jar, jtds-*.jar, commons-*.jar, ...                (deps runtime)
# IGNORANDO OS CERTIFICADOS DE SEGURANÇA DA NETSKOPE APENAS PARA TESTES
RUN mvn -B -ntp -U -DskipTests \
    -Dmaven.resolver.transport=wagon \
    -Dmaven.wagon.http.ssl.insecure=true \
    -Dmaven.wagon.http.ssl.allowall=true \
    -Dmaven.wagon.http.ssl.ignore.validity.dates=true \
    clean package

# Lista o que foi gerado, para diagnostico
RUN echo "=== Conteudo de /build/dist/ ===" && ls -la /build/dist/ && \
    test -f /build/dist/singular-user-storage-provider.jar

# =============================================================================
# Stage 2: Imagem oficial do Keycloak 26 com o plugin pre-instalado
# =============================================================================
FROM quay.io/keycloak/keycloak:26.0.5 AS keycloak-with-plugin

# Copia TODOS os JARs gerados (provider + dependencias de runtime) para o
# diretorio providers/ do Keycloak. Conforme documentacao oficial, esses JARs
# sao carregados por um classloader plano, nao precisam de fat-jar.
COPY --from=builder /build/dist/*.jar /opt/keycloak/providers/

# Faz o build do Keycloak para registrar o provider
RUN /opt/keycloak/bin/kc.sh build

ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev"]
