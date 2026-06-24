# GenChat Local Infrastructure

Starts MySQL, Redis, MinIO, and PgVector only. Run the backend and frontend on the host machine.

## Start

```bash
# 1. Optional: copy the environment template
cp .env.example .env

# 2. Start infrastructure
docker compose up -d

# 3. Check status
docker compose ps
```

## Stop

```bash
docker compose down
```

Remove volumes as well (deletes local database and object storage data):

```bash
docker compose down -v
```

## Services & Ports

| Service | Port | Default credentials | Purpose |
|---------|------|---------------------|---------|
| MySQL | 3306 | `root` / `your_password`, database `chat_agent` | Sessions, files, PPT metadata (Flyway migrations) |
| Redis | 6379 | No password | Agent task state and stop signals |
| MinIO API | 19000 | `ak` / `sk`, bucket `test` | File and generated asset object storage |
| MinIO Console | 19001 | Same as above | Web admin UI |
| PgVector | 5433 | `vectoruser` / `123456`, database `vectordb` | File RAG vector storage |

## Run Backend & Frontend

```bash
# Backend (Java 21; connects to localhost services above by default)
./gradlew bootRun

# Frontend
cd frontend && npm install && npm run dev
```

See `.env.example` in the project root for LLM and other external API keys.

## Notes

- `minio-init` is a one-off job that creates the default bucket; the container exits when done.
- Schema is applied by Flyway when the app starts—no manual SQL import needed.
- The PgVector table `vector_file_info` is created by Spring AI on first use.
