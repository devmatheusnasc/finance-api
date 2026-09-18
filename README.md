# finance-api

API financeira em Java 21 e Spring Boot, organizada por módulos funcionais em um único projeto Maven.

## Estado da implementação

- Fase 1: base Spring Boot, PostgreSQL, Flyway, QueryDSL, Actuator e Docker.
- Fase 2: segurança com Keycloak/OAuth2 Resource Server e CORS.
- Fase 3: módulos `user` e `authentication`.
- Fase 4: módulo `account` como referência arquitetural.
- Fase 5: revisão de arquitetura, segurança e testes do módulo de referência.

## Desenvolvimento local

```bash
cp .env.example .env
docker compose up --build
```

Endpoints operacionais expostos:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /actuator/info`

Nenhum segredo deve ser versionado. O arquivo `.env.example` contém apenas valores de exemplo.
