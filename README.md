# SecTool — Questionnaire Automation

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

SecTool helps security teams answer questionnaires faster through AI-powered answering, a reusable answer library, and automated document processing.

## Architecture Overview

- **Backend** — Spring Boot 3.3 / Java 21, REST API at `:8080`
- **Frontend** — React + Vite SPA at `:5173` (dev) or `:3000` (container)
- **Database** — PostgreSQL 16 with pgvector for embeddings
- **Object Storage** — rustfs (S3-compatible) at `:9000`
- **Document Parsing** — Kreuzberg service at `:7777`
- **AI** — Ollama (LLM + Embeddings) for RAG-powered answer suggestions

## Prerequisites

- Java 21 (a Maven wrapper `./mvnw` is included)
- Node.js 18+ and npm
- Podman (or Docker) with Compose
- Ollama running at a reachable host with `qwen3:8b` and `qwen3-embedding:0.6b` pulled

## Quick Start

```bash
make dev
```

This single command:

1. Starts infrastructure containers (Postgres, rustfs, Kreuzberg)
2. Waits for Postgres to be healthy
3. Launches the Spring Boot backend with hot-reload
4. Launches the Vite frontend with HMR

Once running:

| Service        | URL                                              |
| -------------- | ------------------------------------------------ |
| Frontend       | http://localhost:5173                             |
| Backend API    | http://localhost:8080/todos-api                   |
| Swagger        | http://localhost:8080/todos-api/swagger-ui/index.html |
| rustfs Console | http://localhost:9001                             |

Press `Ctrl+C` to stop backend and frontend; infrastructure containers keep running.

## Make Targets

| Target       | Description                                                  |
| ------------ | ------------------------------------------------------------ |
| `make dev`   | Start infra + backend (hot-reload) + frontend (HMR)         |
| `make dev-infra` | Start only infrastructure containers                    |
| `make dev-api`   | Start only the Spring Boot backend                      |
| `make dev-web`   | Start only the Vite frontend                            |
| `make build`     | Full containerized build (no hot-reload)                |
| `make logs`      | Tail infrastructure container logs                      |
| `make stop`      | Stop all containers                                     |
| `make clean`     | Stop containers and remove volumes (destroys data)      |

## Full Containerized Build

To build and run everything in containers (no local Java/Node needed):

```bash
make build
```

The frontend is served at http://localhost:3000 and the API at http://localhost:8080.

## Configuration

Environment variables are pre-configured for local development in the `Makefile` and `scripts/dev.sh`. Key variables:

| Variable              | Default (dev)                    | Description                        |
| --------------------- | -------------------------------- | ---------------------------------- |
| `DATABASE_URL`        | `jdbc:postgresql://localhost:5432/sectool?useSSL=false` | JDBC connection string |
| `DATABASE_USERNAME`   | `sectool`                        | Postgres user                      |
| `DATABASE_PASSWORD`   | `sectool`                        | Postgres password                  |
| `S3_ENDPOINT`         | `http://localhost:9000`          | S3-compatible storage endpoint     |
| `S3_ACCESS_KEY`       | `rustfsadmin`                    | S3 access key                      |
| `S3_SECRET_KEY`       | `rustfsadmin`                    | S3 secret key                      |
| `KREUZBERG_URL`       | `http://localhost:7777`          | Document parsing service           |
| `LLM_BASE_URL`       | `http://localhost:11434`         | Ollama LLM endpoint               |
| `LLM_MODEL`          | `qwen3:8b`                       | LLM model name                     |
| `EMBEDDINGS_BASE_URL` | `http://localhost:11434`        | Ollama embeddings endpoint         |
| `EMBEDDINGS_MODEL`   | `qwen3-embedding:0.6b`           | Embeddings model name              |
| `EMBEDDINGS_DIMENSIONS` | `1024`                        | Embedding vector size              |

> **Ollama networking note:** If Ollama is running directly on your host (not in Docker), `http://localhost:11434` works for `make dev` since the backend also runs on the host. However, if the backend runs inside a Docker/Podman container (`make build`), `localhost` inside the container refers to the container itself — not the host. In that case, use your host's LAN IP instead (e.g. `http://192.168.1.x:11434`).

## Project Structure

```
├── docker/                  # docker-compose.yml + Dockerfiles
├── scripts/dev.sh           # Dev startup script (used by make dev)
├── src/main/java/           # Spring Boot backend
│   └── com/secfix/todos/
│       ├── apis/controllers # REST controllers
│       ├── database/models  # JPA entities
│       ├── services/        # Business logic + AI clients
│       ├── worker/          # Async document processing pipeline
│       └── security/        # JWT auth + tenant context
├── web/                     # React frontend (Vite + TypeScript)
│   └── src/
│       ├── pages/           # Route pages
│       ├── components/      # Shared components
│       └── api/             # API client modules
├── Makefile                 # Dev commands
└── pom.xml                  # Maven build
```

## Known Limitations

- **No RBAC** — All authenticated users within a tenant have equal access. There are no role-based permission checks (admin, editor, viewer, etc.).
- **No Flyway migrations** — Schema is managed by Hibernate `ddl-auto`. Manual SQL is needed for constraint changes (e.g. adding new enum values to CHECK constraints).
- **No audit logging** — Sensitive actions (approvals, deletions, imports) are not recorded in an audit trail.
- **No rate limiting** — No per-tenant or per-user limits on API calls, LLM requests, or embedding generation.
- **No retry/backoff on AI calls** — Ollama requests fail immediately on timeout or error with no automatic retry.
- **Embedding storage** — Embeddings are stored as serialized TEXT and compared in-application via cosine similarity rather than using pgvector's native vector operators and indexes.
- **Single-worker processing** — The document processing pipeline uses a single scheduled poller; there is no horizontal scaling or competing-consumer setup.
- **No health checks / metrics** — No `/actuator/health` or Prometheus-style metrics endpoints are exposed.

## Pending — LLM Testing

End-to-end LLM/RAG answer quality has not been systematically tested. Areas that need validation:

- [ ] Answer accuracy across different policy document formats (TXT, PDF, DOCX)
- [ ] Retrieval precision — verify top-K chunks are genuinely relevant, not just highest cosine
- [ ] Handling of multi-part questions where evidence spans multiple documents
- [ ] Confidence score calibration — check that scores reflect actual answer quality
- [ ] Edge cases: empty knowledge base, very large documents, non-English content
- [ ] Rerank effectiveness — currently implicit (top-K by similarity); a dedicated reranker is not implemented
- [ ] Prompt tuning — the system prompt may need iteration for different questionnaire styles (CAIQ, SIG, custom)
- [ ] Latency profiling — embedding + retrieval + LLM round-trip under realistic document counts

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
