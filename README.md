# legal-rag-platform

AI-powered Legal-Tech platform for legal document analysis, semantic search, and question answering using React, Spring Boot, and Python RAG services.

## Project Overview

`legal-rag-platform` is a university capstone monorepo built to help legal professionals and students work with legal documents more efficiently. The platform enables users to upload legal documents, run semantic search, ask legal questions with Retrieval-Augmented Generation (RAG), receive AI-assisted legal insights, and manage document analysis history.

## Objectives

- Build an end-to-end Legal-Tech platform using a scalable monorepo architecture.
- Support document ingestion and analysis workflows for legal content.
- Provide semantic retrieval over legal document corpora.
- Deliver AI-generated legal assistance grounded in retrieved context.
- Enable team collaboration across frontend, backend, and AI services.

## Team Members

- Member 1 (Project Lead)
- Member 2 (Frontend)
- Member 3 (Backend)
- Member 4 (AI/ML)
- Member 5 (DevOps)
- Member 6 (QA/Documentation)

## Technology Stack

- **Frontend:** React + Vite
- **Backend:** Spring Boot (Maven)
- **AI Service:** Python-based RAG service
- **Containerization:** Docker + Docker Compose
- **Documentation:** Markdown + architecture/diagram artifacts

## System Architecture

The platform follows a clean, service-oriented monorepo architecture:

- **frontend**: User interface for document upload, search, Q&A, and history views.
- **backend**: REST API gateway for authentication, document management, orchestration, and persistence.
- **ai-service**: AI/RAG processing layer for embeddings, retrieval, and answer generation.
- **docs**: Architecture decisions, diagrams, API docs, and meeting notes.

Request flow (high-level):
1. User uploads legal documents via frontend.
2. Backend stores metadata and coordinates processing.
3. AI service indexes content for semantic retrieval.
4. User asks a question; AI service retrieves context and generates a grounded response.
5. Backend returns results and persists history.

## Repository Structure

```text
legal-rag-platform/
├── frontend/                 # React application
├── backend/                  # Spring Boot REST API
├── ai-service/               # Python AI/RAG service
├── docs/
│   ├── architecture/
│   ├── diagrams/
│   ├── api/
│   └── meeting-notes/
├── scripts/
├── .github/
│   ├── workflows/
│   ├── ISSUE_TEMPLATE/
│   └── PULL_REQUEST_TEMPLATE.md
├── docker-compose.yml
├── README.md
├── LICENSE
└── .gitignore
```

## Local Development Setup

### Prerequisites

- Node.js 20+
- Java 21+
- Maven 3.9+
- Python 3.11+
- Docker Desktop / Docker Engine

### Quick Start

1. Clone the repository.
2. Create environment files from service examples when available.
3. Start local dependencies and service placeholders:

```bash
docker compose up --build
```

4. Start services independently during development:
   - Frontend only:

   ```bash
   cd frontend
   docker compose up --build
   ```

   - Backend stack:

   ```bash
   cd backend
   docker compose up --build
   ```

   - AI service stack:

   ```bash
   cd ai-service
   docker compose up --build
   ```

## Development Workflow

1. Create a branch from `develop`.
2. Implement focused changes by service domain.
3. Add/update tests and documentation for affected modules.
4. Open a pull request to `develop`.
5. Require CI checks and team review before merge.

## Branching Strategy

This repository uses GitHub Flow with team conventions:

- `main`: Production-ready stable branch
- `develop`: Integration branch for capstone development
- `feature/*`: New functionality
- `bugfix/*`: Non-critical bug fixes
- `hotfix/*`: Urgent fixes for production-critical issues

## Contribution Guidelines

- Keep pull requests small and focused.
- Follow service boundaries (`frontend`, `backend`, `ai-service`).
- Write clear commit messages and link related tasks/issues.
- Update `docs/` for architecture/API decisions.
- Request at least one reviewer before merging.
- Ensure CI placeholders (and future pipelines) pass before merge.
