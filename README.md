# finance-api

API financeira em Java 21 e Spring Boot, mantida como um único projeto Maven e organizada por
domínio funcional.

## Estado

- Fase 0: fundação arquitetural implementada.
- Fase 1: autenticação backend implementada.
- Fase 2: hardening da autenticação e threat model implementados.

Não existem MFA, organizações, frontend ou funcionalidades financeiras nesta fase.

## Arquitetura

Os módulos funcionais serão criados somente quando houver comportamento real:

```text
br.com.finance
├── config
│   ├── exception
│   └── observability
├── modules
│   └── authentication
│       ├── domain
│       ├── application
│       └── infrastructure
│           ├── persistence
│           └── web
└── FinanceApiApplication
```

O domínio não poderá depender de Spring, JPA, Servlet ou camadas externas. A camada de aplicação
também não poderá depender de configuração ou infraestrutura. Essas regras são verificadas por
testes ArchUnit.

## Configuração

Os profiles disponíveis são `local`, `test`, `dev` e `prod`. Credenciais não possuem valor padrão
e devem ser fornecidas externamente pelas variáveis:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT` — opcional, padrão `8080`
- `DB_POOL_MAX_SIZE` — opcional, padrão `10`
- `DB_POOL_MIN_IDLE` — opcional, padrão `2`
- `CORS_ALLOWED_ORIGINS` — lista de origens explícitas separadas por vírgula
- `AUTH_RATE_LIMIT_HASH_SECRET` — segredo aleatório com no mínimo 32 caracteres
- `AUTH_SESSION_DURATION` — opcional, padrão `PT30M`, máximo de 24 horas
- `AUTH_SESSION_IDLE_TIMEOUT` — opcional, padrão `PT15M`, nunca maior que a duração absoluta
- `AUTH_SESSION_ACTIVITY_UPDATE_INTERVAL` — opcional, padrão `PT5M`, deve ser menor que o timeout
  ocioso e reduz a frequência de escrita da atividade da sessão
- `AUTH_REGISTRATION_RATE_MAX_ATTEMPTS` — opcional, padrão `5`
- `AUTH_REGISTRATION_RATE_WINDOW` — opcional, padrão `PT1H`
- `AUTH_REGISTRATION_RATE_BLOCK_DURATION` — opcional, padrão `PT1H`

Para executar localmente sem Docker:

```bash
cp .env.example .env
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Substitua a senha e o segredo HMAC de exemplo no `.env`. O arquivo é ignorado pelo Git e importado
somente pelo profile `local`. Cookies de autenticação são sempre `Secure`; use um navegador que
trate `localhost` como contexto seguro ou HTTPS local.

## Docker local

O Compose existe exclusivamente para desenvolvimento local:

```bash
cp .env.example .env
docker compose -f compose.local.yaml up --build
```

Banco e API ficam vinculados à interface local (`127.0.0.1`).

## Observabilidade

- logs em texto legível nos profiles `local`, `test` e `dev`;
- logs JSON no formato Logstash exclusivamente no profile `prod`;
- nome da aplicação e correlation ID incluídos nos logs quando disponíveis;
- header `X-Correlation-ID` gerado pela API;
- correlation ID incluído nas respostas de erro;
- logs HTTP sem query string, payload, credenciais ou tokens;
- nível padrão da aplicação em `INFO`, sem SQL do Hibernate habilitado;
- somente o endpoint Actuator `health` fica exposto;
- detalhes e componentes do health check não são retornados.

Eventos HTTP não registram corpo, query string, e-mail, senha, cookie ou token.

Endpoints operacionais:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

## Banco de dados

O PostgreSQL é obrigatório. O Hibernate usa `ddl-auto: validate` e a inicialização por `schema.sql`
está desabilitada. Toda alteração futura de schema deverá ser feita por migration Flyway em
`src/main/resources/db/migration`.

As migrations `V1__create_authentication_tables.sql` e `V2__harden_authentication_sessions.sql`
criam usuários, credenciais, sessões, timeout ocioso e controle persistente de tentativas. Senhas
são armazenadas somente como hash Argon2id e sessões somente como SHA-256 do token aleatório.

## Autenticação

A API usa sessão opaca server-side, sem JWT próprio e sem `HttpSession`. O token de 256 bits é
entregue somente no cookie `__Host-FINANCE_SESSION`, configurado com `Secure`, `HttpOnly`,
`SameSite=Strict`, path raiz e sem `Domain`. A sessão possui expiração absoluta de 30 minutos,
timeout ocioso de 15 minutos e revogação no logout. A atividade é persistida em intervalos de cinco
minutos por update condicional, sem lock pessimista nem escrita em toda requisição autenticada.

Endpoints:

- `GET /api/auth/csrf` — obtém o token CSRF e cria seu cookie;
- `POST /api/auth/register` — cria a conta e sempre responde `204`, inclusive para e-mail já
  existente, evitando enumeração;
- `POST /api/auth/login` — autentica e cria a sessão;
- `POST /api/auth/logout` — revoga a sessão de forma idempotente;
- `GET /api/auth/me` — endpoint protegido com os dados do usuário autenticado.

Os três endpoints `POST` exigem o cookie gerado por `/api/auth/csrf` e o token no header
`X-CSRF-TOKEN`. Login inválido usa uma resposta única para conta ausente, senha incorreta, conta
inativa ou credencial bloqueada. Tentativas são limitadas no PostgreSQL por conta e origem, usando
chaves HMAC para não persistir e-mail ou endereço IP nessa tabela. O cadastro também possui limite
por origem antes do cálculo Argon2id, e os limites usam locks transacionais para resistir a rajadas
concorrentes.

Documentação de segurança:

- [`docs/security/threat-model.md`](docs/security/threat-model.md)
- [`docs/security/authentication-review.md`](docs/security/authentication-review.md)

## Validação

```bash
./mvnw clean verify
```

O teste de integração usa PostgreSQL real com Testcontainers e requer acesso a um Docker daemon.
