#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

cleanup() {
    echo ""
    echo "Shutting down..."
    kill $API_PID $WEB_PID 2>/dev/null || true
    wait $API_PID $WEB_PID 2>/dev/null || true
    echo "Done."
}
trap cleanup EXIT INT TERM

echo "Starting backend (Spring Boot, hot-reload)..."
cd "$PROJECT_ROOT"
DATABASE_URL="jdbc:postgresql://localhost:5432/sectool?useSSL=false" \
DATABASE_USERNAME=sectool \
DATABASE_PASSWORD=sectool \
S3_ENDPOINT=http://localhost:9000 \
S3_ACCESS_KEY=rustfsadmin \
S3_SECRET_KEY=rustfsadmin \
S3_BUCKET=sectool \
KREUZBERG_URL=http://localhost:7777 \
LLM_BASE_URL=http://localhost:11434 \
LLM_MODEL="qwen3:8b" \
EMBEDDINGS_BASE_URL=http://localhost:11434 \
EMBEDDINGS_MODEL="qwen3-embedding:0.6b" \
EMBEDDINGS_DIMENSIONS=1024 \
APP_ENV=dev \
./mvnw spring-boot:run &
API_PID=$!

echo "Starting frontend (Vite dev server, HMR)..."
cd "$PROJECT_ROOT/web"
VITE_API_URL=http://localhost:8080/todos-api npx vite --host 0.0.0.0 &
WEB_PID=$!

echo ""
echo "============================================"
echo "  SecTool dev environment running"
echo "  Backend:  http://localhost:8080/todos-api"
echo "  Frontend: http://localhost:5173"
echo "  Swagger:  http://localhost:8080/todos-api/docs.html"
echo "  Press Ctrl+C to stop all"
echo "============================================"
echo ""

wait
