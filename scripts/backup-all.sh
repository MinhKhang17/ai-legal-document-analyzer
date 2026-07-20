#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="${ROOT_DIR}/backups/${TIMESTAMP}"
mkdir -p "${BACKUP_DIR}"

cd "${ROOT_DIR}"

echo "Backing up PostgreSQL to ${BACKUP_DIR}/postgres.sql"
docker compose exec -T postgres pg_dump -U postgres -d ai_legal_db --clean --if-exists > "${BACKUP_DIR}/postgres.sql"

BACKEND_CONTAINER_ID="$(docker compose ps -q backend)"
if [[ -n "${BACKEND_CONTAINER_ID}" ]]; then
  UPLOAD_VOLUME="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/app/uploads"}}{{.Name}}{{end}}{{end}}' "${BACKEND_CONTAINER_ID}")"
  if [[ -n "${UPLOAD_VOLUME}" ]]; then
    echo "Backing up original source files to ${BACKUP_DIR}/shared_uploads.tar.gz"
    docker run --rm \
      -v "${UPLOAD_VOLUME}:/source:ro" \
      -v "${BACKUP_DIR}:/backup-output" \
      alpine:3.20 tar -czf /backup-output/shared_uploads.tar.gz -C /source .
  fi
fi

restart_neo4j() {
  docker compose up -d neo4j >/dev/null 2>&1 || true
}
trap restart_neo4j EXIT

echo "Stopping Neo4j for a consistent database dump"
docker compose stop neo4j
docker compose run --rm --no-deps \
  -v "${BACKUP_DIR}:/backup-output" \
  neo4j neo4j-admin database dump neo4j \
  --to-path=/backup-output \
  --overwrite-destination=true

restart_neo4j
trap - EXIT

echo "Backup completed: ${BACKUP_DIR}"
