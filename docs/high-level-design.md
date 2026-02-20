# SecTool — High-Level Design: Questionnaire Automation Flow

## 1. System Overview

SecTool automates security questionnaire answering by combining document processing, knowledge base construction, and RAG-powered AI suggestions. The system ingests policy documents and questionnaires, extracts structured questions, and generates answers grounded in the organization's own documentation.

```
┌─────────────┐     ┌──────────────────┐     ┌──────────────────────┐
│  React SPA  │────▶│  Spring Boot API │────▶│  PostgreSQL+pgvector │
│  (Vite)     │     │  (:8080)         │     │  (:5432)             │
└─────────────┘     └──────┬───────────┘     └──────────────────────┘
                           │
                    ┌──────┼──────────┐
                    │      │          │
               ┌────▼──┐ ┌▼───────┐ ┌▼──────────┐
               │rustfs │ │Kreuz-  │ │  Ollama    │
               │(S3)   │ │berg    │ │  LLM+Embed │
               │:9000  │ │:7777   │ │  :11434    │
               └───────┘ └────────┘ └────────────┘
```

## 2. Core Flows

### 2.1 Document Upload & Processing Pipeline

The pipeline runs asynchronously via a scheduled worker polling a job queue.

```
User uploads file
        │
        ▼
  ┌───────────┐    presigned URL    ┌─────────┐
  │ Create    │───────────────────▶│  rustfs  │
  │ Document  │                    │  (S3)    │
  │ + Version │                    └─────────┘
  └─────┬─────┘
        │ POST /complete
        ▼
  ┌──────────────────────────────────────────────────────────┐
  │              Document Processing Pipeline                 │
  │                                                          │
  │  ┌───────┐   ┌──────────────┐   ┌───────┐   ┌───────┐  │
  │  │ PARSE │──▶│ EXTRACT_QUES │──▶│ CHUNK │──▶│ EMBED │──▶ FINALIZE
  │  └───┬───┘   │ (quest. only)│   └───┬───┘   └───┬───┘  │
  │      │       └──────────────┘       │            │      │
  │      ▼                              ▼            ▼      │
  │  Kreuzberg / POI            KbChunk rows   KbEmbedding  │
  │  → EXTRACTED_TEXT                          (1024-dim)    │
  │  → PARSED_JSON                                          │
  │  → RENDERED_HTML                                        │
  └──────────────────────────────────────────────────────────┘
```

**Stage details:**

| Stage | Trigger | Action | Output |
|-------|---------|--------|--------|
| PARSE | Upload complete | Kreuzberg extracts text/tables; POI for XLSX | Artifacts: EXTRACTED_TEXT, PARSED_JSON, RENDERED_HTML |
| EXTRACT_QUESTIONS | Questionnaire docs only | Pattern-match headers across all sheets, extract items | Questionnaire + QuestionnaireItem rows |
| CHUNK | After parse/extract | Split into <=2000-char chunks; structured (row-based) or paragraph-based | KbChunk rows with metadata |
| EMBED | After chunking | Ollama generates 1024-dim vectors per chunk | KbEmbedding rows |
| FINALIZE | After embedding | Mark version READY | DocumentVersion status = READY |

**Job queue mechanism:**
- Jobs stored in `document_processing_job` table
- Worker polls every 5 seconds per stage
- Locking via `SELECT ... FOR UPDATE SKIP LOCKED`
- Each job tracks: stage, status (PENDING/RUNNING/DONE/FAILED), attempt count, error details

### 2.2 Questionnaire Extraction (XLSX)

When a document of type `QUESTIONNAIRE` is uploaded, the EXTRACT_QUESTIONS stage analyzes the parsed spreadsheet structure.

```
Parsed JSON (tables array)
        │
        ▼
  ┌─────────────────────────┐
  │ For each sheet:         │
  │  1. Scan rows 0–14 for  │
  │     header patterns     │
  │  2. Score confidence    │
  │  3. Identify columns:   │
  │     Question, Category, │
  │     Answer, Item        │
  └────────┬────────────────┘
           │ all qualifying sheets
           ▼
  ┌─────────────────────────┐
  │ For each data row:      │
  │  • Build question text  │
  │    [Category] Item —    │
  │    Question             │
  │  • Record sourceLocation│
  │    {sheet, row, cols}   │
  │  • If answer present →  │
  │    create DRAFT response│
  └────────┬────────────────┘
           │
           ▼
  Questionnaire (IN_PROGRESS)
  + N QuestionnaireItems
  + M QuestionnaireResponses (pre-filled)
```

**Header detection patterns:**
- Question columns: `question`, `query`, `requirement`, `specifics`, `description`, `detail`
- Category columns: `category`, `section`, `domain`, `group`, `topic`
- Answer columns: `answer`, `response`, `comment`, `finding`, `observation`

### 2.3 RAG-Powered Answer Suggestion

When a user requests an AI suggestion for a questionnaire item:

```
User clicks "Get AI Suggestion"
        │
        ▼
  ┌──────────────────────┐
  │ 1. Answer Library    │──── exact match? ──▶ Return (confidence 0.95)
  │    Lookup            │         │ no
  └──────────────────────┘         ▼
  ┌──────────────────────┐
  │ 2. Embed Question    │──▶ float[1024] query vector
  └──────────┬───────────┘
             ▼
  ┌──────────────────────┐
  │ 3. Retrieve Chunks   │
  │  • Load all tenant   │
  │    embeddings        │
  │  • Filter: policy    │
  │    docs only (excl.  │
  │    questionnaires)   │
  │  • Cosine similarity │
  │  • Threshold >= 0.1  │
  │  • Top K = 5         │
  └──────────┬───────────┘
             ▼
  ┌──────────────────────┐
  │ 4. LLM Generation    │
  │  • System prompt:    │
  │    security Q&A      │
  │    assistant          │
  │  • Context: top-5    │
  │    chunks + IDs      │
  │  • Question text     │
  └──────────┬───────────┘
             ▼
  ┌──────────────────────┐
  │ 5. Build Response    │
  │  • answerText        │
  │  • citations[]       │
  │  • confidence (0–1)  │
  │  • coverageStatus    │
  │    (OK or            │
  │    INSUFFICIENT_     │
  │    EVIDENCE)         │
  └──────────────────────┘
```

**Citation format:** `["kb_chunk:<uuid>", ...]` stored as JSONB. Each citation links to a `KbChunk` that can be fetched to show source text with document title, type, and version number.

### 2.4 Answer Workflow & Library Import

```
  QuestionnaireItem
  (UNANSWERED)
       │
       │ AI suggestion
       ▼
  (SUGGESTED)
       │
       │ user edits / saves draft
       ▼
  (DRAFTED)
       │
       │ reviewer approves
       ▼
  (APPROVED)
       │
       │ questionnaire completed
       │ with import flag
       ▼
  AnswerLibraryEntry
  (source: IMPORTED)
```

**Import paths:**
1. **On completion** — `POST /questionnaires/{id}/complete?importAnswersToLibrary=true` imports all approved responses.
2. **Manual review** — `GET /imports/pending-answers/list` shows approved responses not yet in the library. `POST /imports/pending-answers/import` imports selected ones.

Library entries are normalized (lowercased, trimmed) for future exact-match lookups, which take priority over RAG retrieval.

## 3. Multi-Tenancy

Tenant isolation is enforced at every layer:

| Layer | Mechanism |
|-------|-----------|
| Authentication | JWT contains `tenant_id` claim; `JwtAuthenticationFilter` sets `TenantContext` (thread-local) |
| API | All controllers read `TenantContext.getTenantId()` |
| Database | Every entity has a `tenant_id` column; all repository queries filter by it |
| Object Storage | S3 keys scoped: `tenant/{tenantId}/documents/{docId}/versions/{verId}/...` |
| Worker | `DocumentProcessingJob` includes `tenant_id`; operations are tenant-scoped |

## 4. Authentication Flow

```
  Register                          Login
  POST /auth/register               POST /auth/login
       │                                 │
       ▼                                 ▼
  Create/find Tenant             Validate tenant + credentials
  Create UserInfo                Verify BCrypt hash
  (BCrypt hash password)              │
       │                              │
       └──────────┬───────────────────┘
                  ▼
           Generate JWT
           (tenant_id, user_id,
            email, role)
                  │
                  ▼
           Return token
```

All subsequent requests carry `Authorization: Bearer <token>`. The `JwtAuthenticationFilter` validates the token, extracts claims, and populates `TenantContext` + Spring Security authentication.

## 5. API Surface

### Documents (`/documents`)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/documents` | Create document metadata |
| GET | `/documents` | List documents for tenant |
| POST | `/documents/{id}/versions` | Create version + get upload URL |
| POST | `/document-versions/{id}/complete` | Mark uploaded, start pipeline |
| GET | `/document-versions/{id}` | Version details with artifacts |
| GET | `/document-versions/{id}/preview` | Presigned download URL |
| GET | `/document-versions/{id}/artifacts/{kind}` | Presigned artifact URL |
| GET | `/document-versions/{id}/artifacts/{kind}/content` | Raw artifact content |

### Questionnaires (`/questionnaires`)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/questionnaires` | List (optional status filter) |
| POST | `/questionnaires` | Create questionnaire |
| GET | `/questionnaires/{id}` | Detail with items |
| POST | `/questionnaires/{id}/import/spreadsheet` | Import preview |
| POST | `/questionnaires/{id}/import/spreadsheet/columns` | Submit column mappings |
| POST | `/questionnaires/{id}/items/{itemId}/suggest` | AI suggestion |
| POST | `/questionnaires/{id}/items/{itemId}/response` | Save response |
| POST | `/questionnaires/{id}/complete` | Complete + optional library import |

### RAG & Imports (`/rag`, `/imports`, `/kb`)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/rag/suggest` | Generate answer suggestion |
| POST | `/rag/feedback` | Submit feedback |
| GET | `/imports/pending-answers` | Count pending |
| GET | `/imports/pending-answers/list` | List pending |
| POST | `/imports/pending-answers/import` | Import to library |
| GET | `/kb/chunks/{chunkId}` | Chunk detail with source info |

## 6. Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Spring Boot Application                      │
│                                                                     │
│  ┌─────────────┐  ┌──────────────────┐  ┌───────────────────────┐  │
│  │   Security   │  │    Controllers   │  │      Services          │  │
│  │             │  │                  │  │                       │  │
│  │ JwtFilter   │  │ AuthController   │  │ DocumentsService      │  │
│  │ TenantCtx   │  │ DocumentsCtrl    │  │ QuestionnairesService │  │
│  │ SecurityCfg │  │ QuestionnairesC  │  │ RagService            │  │
│  │             │  │ RagController    │  │ ChunkingService       │  │
│  └─────────────┘  └──────────────────┘  │ StorageService        │  │
│                                         │ KreuzbergClient       │  │
│  ┌─────────────────────────────────┐    │ SpreadsheetParsingSvc │  │
│  │     Worker                      │    │ QuestionnaireExtract  │  │
│  │                                 │    │ OllamaLlmClient      │  │
│  │  DocumentProcessingWorker       │    │ OllamaEmbeddingsClnt │  │
│  │  (Scheduled: 5s polling)        │    └───────────────────────┘  │
│  │  PARSE→EXTRACT→CHUNK→EMBED→FIN │                                │
│  └─────────────────────────────────┘                                │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    JPA Repositories                          │    │
│  │  Document · DocumentVersion · DocumentArtifact · KbChunk    │    │
│  │  KbEmbedding · Questionnaire · QuestionnaireItem            │    │
│  │  QuestionnaireResponse · AnswerSuggestion · AnswerFeedback  │    │
│  │  AnswerLibraryEntry · DocumentProcessingJob · Tenant · User │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
          │              │              │              │
     PostgreSQL      rustfs (S3)    Kreuzberg       Ollama
     + pgvector       :9000          :7777         :11434
       :5432
```
