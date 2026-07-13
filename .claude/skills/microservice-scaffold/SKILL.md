---
name: microservice-scaffold
description: Use esta skill sempre que for criar um novo microserviço no
  monorepo commerce-hub. Define pom.xml filho, application.yml, Dockerfile,
  CLAUDE.md local e a estrutura de pacotes — diferenciando serviços de
  negócio (Clean Architecture completa) de serviços de infraestrutura
  (discovery-service, api-gateway), que recebem apenas POM + config + main.
---

## Passo 0 — qual é o tipo de serviço?

- **Serviço de negócio** (auth, catalog, inventory, customer, cart, order,
  payment, notification) → siga a **Trilha A**.
- **Serviço de infraestrutura** (discovery-service, api-gateway) → siga a
  **Trilha B**. Esses serviços **não** recebem `domain/`, `application/`
  nem `adapter/` — só `pom.xml` + `application.yml` + classe main +
  `Dockerfile` (ver ADR 004: eles não têm banco nem lógica de negócio).

Em ambas as trilhas: pacote base `br.com.commercehub.{nome-service}`,
porta conforme a tabela abaixo, e o serviço precisa constar em
`<modules>` no `pom.xml` raiz (hoje os 10 serviços já estão listados).

## Tabela de portas

| Serviço               | Porta |
| --------------------- | ----- |
| discovery-service     | 8761  |
| api-gateway           | 8080  |
| auth-service          | 8081  |
| catalog-service       | 8082  |
| inventory-service     | 8083  |
| customer-service      | 8084  |
| cart-service           | 8085  |
| order-service         | 8086  |
| payment-service       | 8087  |
| notification-service  | 8088  |

## Trilha A — serviço de negócio

### Estrutura de pacotes (Clean Architecture — ver CLAUDE.md raiz)

```
{service}/src/main/java/br/com/commercehub/{service}/
├── domain/
│   ├── model/       ← entidades de domínio (sem anotações de framework)
│   └── exception/   ← exceções de negócio
├── application/
│   ├── port/        ← interfaces (repositórios, serviços externos)
│   └── usecase/     ← lógica de negócio, orquestra domain + ports
└── adapter/
    ├── persistence/  ← JPA entities, repositories Spring Data
    ├── web/          ← REST controllers, DTOs de request/response
    └── messaging/    ← publishers/consumers de eventos (quando aplicável)
```

### pom.xml filho

O `pom.xml` raiz é `packaging=pom` com `dependencyManagement` via BOM
import — **não** usa `spring-boot-starter-parent`. Por isso o filho
precisa declarar explicitamente `maven.compiler.release` (a versão do
`maven-compiler-plugin` já é gerenciada no `pluginManagement` do pom
raiz) e o `spring-boot-maven-plugin` **com a execução `repackage`
vinculada** — sem essa `<executions>`, o plugin fica no classpath mas
não roda, e o jar gerado não tem manifesto executável (o `Dockerfile`
falharia com "no main manifest attribute").

O `pluginManagement` do pom raiz também já fixa a versão do
`maven-surefire-plugin` (3.2.5) — sem `spring-boot-starter-parent`, o
padrão herdado do super POM do Maven seria a 2.12.4, que não reconhece
o JUnit Platform (JUnit 5): os testes compilam mas o Surefire roda
silenciosamente zero deles ("Tests run: 0") em vez de falhar. Nada a
declarar no pom filho para isso — é só saber que o comportamento correto
depende do raiz estar íntegro; se algum dia o pom raiz for reescrito sem
essa entrada, todo teste de todo serviço passa a rodar 0 casos sem
aviso.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>br.com.commercehub</groupId>
    <artifactId>commerce-hub</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>

  <artifactId>{nome-service}</artifactId>

  <properties>
    <maven.compiler.release>${java.version}</maven.compiler.release>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <version>${spring-boot.version}</version>
        <executions>
          <execution>
            <goals>
              <goal>repackage</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

**Dependências opcionais** (adicionar só conforme o critério do ADR 003,
não em todo serviço por padrão):

- Chama outro serviço de forma síncrona (ex.: cart→inventory,
  order→inventory, qualquer→catalog)? Adicione
  `spring-cloud-starter-openfeign` e lembre-se: **todo Feign Client
  precisa de fallback**.
  ```xml
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
  </dependency>
  ```
- Publica ou consome eventos (ex.: `CheckoutRequested`, `OrderCreated`,
  `ProductUpdated`)? Adicione `spring-kafka` e lembre-se: **todo evento
  carrega `eventId` para idempotência no consumer**.
  ```xml
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
  </dependency>
  ```

### application.yml

```yaml
spring:
  application:
    name: {nome-service}
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/commercedb}
    username: ${DB_USER:commerce}
    password: ${DB_PASS:commerce123}
  jpa:
    properties:
      hibernate:
        default_schema: {nome_schema}  # ex: catalog, inventory
    hibernate:
      ddl-auto: validate
  flyway:
    schemas: {nome_schema}
    locations: classpath:db/migration

server:
  port: {porta}  # ver tabela de portas acima

eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
```

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/{nome-service}-*.jar app.jar
EXPOSE {porta}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### CLAUDE.md local

```markdown
# {nome-service} — Contexto local

## Responsabilidade única
{uma frase descrevendo o que APENAS esse serviço faz}

## Bounded context
Conhece: {entidades desse serviço}
NÃO conhece: {o que é responsabilidade de outro serviço}

(preencher com base na tabela de bounded contexts do ADR 004)

## Schema PostgreSQL
`{nome_schema}` — nunca acessar outros schemas.

## Dependências de outros serviços
- {serviço}: via Feign Client para {operação}
- {serviço}: publica/consome evento {NomeDoEvento}
```

Convenções globais (UUID como ID, `BigDecimal` para dinheiro,
`OffsetDateTime` para datas, DTOs como Records, BCrypt strength 12,
paginação com `Pageable`/`Page<T>`) já estão documentadas no `CLAUDE.md`
raiz — não duplicar aqui, só seguir.

## Trilha B — serviço de infraestrutura

Sem `domain/`, `application/` nem `adapter/`. Apenas:

```
{service}/src/main/java/br/com/commercehub/{service}/
└── {Service}Application.java
```

### discovery-service (Eureka server)

`pom.xml` — única dependência além de test:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

(mais `maven.compiler.release` e `spring-boot-maven-plugin` como na
Trilha A — sem `data-jpa`, `postgresql`, `flyway` ou `eureka-client`.)

`application.yml`:

```yaml
spring:
  application:
    name: discovery-service

server:
  port: 8761

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

Classe main com `@EnableEurekaServer`.

### api-gateway

`pom.xml` — dependências além de test:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

(sem `data-jpa`, `postgresql` ou `flyway` — ADR 004: api-gateway "não
tem banco, não tem domínio".)

`application.yml`:

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true

server:
  port: 8080

eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
```

Classe main só com `@SpringBootApplication` (roteamento e validação de
JWT ficam em filtros/config do Gateway, não em `@EnableX`).

### Dockerfile e CLAUDE.md local (Trilha B)

`Dockerfile`: igual ao template da Trilha A (troque nome do jar e
porta).

`CLAUDE.md` local simplificado (sem "Bounded context" nem "Schema
PostgreSQL", pois não se aplicam):

```markdown
# {nome-service} — Contexto local

## Responsabilidade única
{uma frase — ex.: "registro de serviços via Eureka" ou "roteamento e
validação de JWT para os demais serviços"}

## Observação
Serviço de infraestrutura — sem banco de dados, sem lógica de negócio,
sem estrutura hexagonal (ADR 004).
```
