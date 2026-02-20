.PHONY: dev dev-infra dev-api dev-web stop logs clean

PROJECT_ROOT := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
COMPOSE_FILE := $(PROJECT_ROOT)/docker/docker-compose.yml

-include $(PROJECT_ROOT)/.env
export

# --- Main targets ---

## Start everything: infra containers + backend (hot-reload) + frontend (HMR)
dev: dev-infra
	@$(PROJECT_ROOT)/scripts/dev.sh

## Start only infrastructure containers (postgres, rustfs, kreuzberg)
dev-infra:
	podman compose -f $(COMPOSE_FILE) up -d postgres rustfs kreuzberg
	@echo "Waiting for postgres..."
	@until podman compose -f $(COMPOSE_FILE) exec postgres pg_isready -U sectool -q 2>/dev/null; do sleep 1; done
	@echo "Infrastructure ready."

## Start only backend with hot-reload
dev-api:
	DATABASE_URL="jdbc:postgresql://localhost:5432/sectool?useSSL=false" \
	DATABASE_USERNAME=sectool \
	DATABASE_PASSWORD=sectool \
	S3_ENDPOINT=http://localhost:9000 \
	S3_ACCESS_KEY=rustfsadmin \
	S3_SECRET_KEY=rustfsadmin \
	S3_BUCKET=sectool \
	KREUZBERG_URL=http://localhost:7777 \
	LLM_PROVIDER=anthropic \
	LLM_BASE_URL=https://api.anthropic.com \
	LLM_API_KEY=$(ANTHROPIC_API_KEY) \
	LLM_MODEL="claude-sonnet-4-20250514" \
	EMBEDDINGS_PROVIDER=anthropic \
	EMBEDDINGS_BASE_URL=https://api.voyageai.com \
	EMBEDDINGS_API_KEY=$(VOYAGE_API_KEY) \
	EMBEDDINGS_MODEL="voyage-3.5" \
	EMBEDDINGS_DIMENSIONS=1024 \
	APP_ENV=dev \
	$(PROJECT_ROOT)/mvnw -f $(PROJECT_ROOT)/pom.xml spring-boot:run

## Start only frontend with HMR
dev-web:
	cd $(PROJECT_ROOT)/web && VITE_API_URL=http://localhost:8080/todos-api npx vite --host 0.0.0.0

## Stop all containers
stop:
	podman compose -f $(COMPOSE_FILE) down

## Show infra container logs
logs:
	podman compose -f $(COMPOSE_FILE) logs -f postgres rustfs kreuzberg

## Full build in containers (no hot-reload)
build:
	podman compose -f $(COMPOSE_FILE) up -d --build

## Stop and remove volumes
clean:
	podman compose -f $(COMPOSE_FILE) down -v
