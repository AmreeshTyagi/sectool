# SecTool — Database Schema Design

## Overview

SecTool uses PostgreSQL 16 with the `pgvector` extension. All tables include a `tenant_id` column for multi-tenant isolation (except `tenant` itself). Schema is managed by Hibernate `ddl-auto`.

## Entity-Relationship Diagram

```
tenant
  │
  │ 1:N
  ▼
user_info ─────────────────────────────────────┐
  │                                            │
  │ owns                                       │ created_by
  ▼                                            ▼
task ──▶ code_repository              document
                                        │
                                        │ 1:N
                                        ▼
                                   document_version
                                     │         │
                          ┌──────────┤         │
                          │          │         │
                          ▼          ▼         ▼
                   document_    kb_chunk   document_processing_job
                   artifact        │
                                   │ 1:1
                                   ▼
                              kb_embedding


                                   questionnaire
                                        │
                                        │ 1:N
                                        ▼
                                  questionnaire_item
                                   │           │
                          ┌────────┤           │
                          │        │           │
                          ▼        ▼           ▼
                   answer_    questionnaire_  (sourceLocation
                   suggestion  response       links to sheet/row)
                     │             │
                     │             │ import
                     ▼             ▼
               answer_feedback  answer_library_entry
                                     │
                                     │ 1:1
                                     ▼
                               answer_library_embedding
```

---

## Tables

### `tenant`

Multi-tenancy root entity.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Tenant ID |
| `name` | VARCHAR | NOT NULL | Display name |
| `slug` | VARCHAR | NOT NULL, UNIQUE | URL-safe identifier |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | Creation timestamp |

---

### `user_info`

Authenticated users. Unique on `(tenant_id, email)`.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK, auto-increment | User ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `name` | VARCHAR | NOT NULL | Display name |
| `email` | VARCHAR | NOT NULL | Login email |
| `password_hash` | VARCHAR | | BCrypt hash |
| `role` | VARCHAR(32) | NOT NULL, DEFAULT 'USER' | User role |
| `is_active` | BOOLEAN | DEFAULT true | Account active flag |
| `created_at` | TIMESTAMP | auto-set | |
| `updated_at` | TIMESTAMP | auto-set | |

**Unique constraint:** `(tenant_id, email)`

---

### `document`

Top-level document metadata.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Document ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `type` | VARCHAR | ENUM: POLICY, QUESTIONNAIRE, OTHER | Document classification |
| `title` | VARCHAR | | Display title |
| `source` | VARCHAR | | Origin description |
| `created_by` | INTEGER | | FK → user_info |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `document_version`

Immutable version of a document. Each upload creates a new version.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Version ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `document_id` | UUID | NOT NULL | FK → document |
| `version_num` | INTEGER | | Auto-incrementing per document |
| `original_filename` | VARCHAR | | Uploaded filename |
| `mime_type` | VARCHAR | | MIME type |
| `size_bytes` | BIGINT | | File size |
| `sha256` | VARCHAR | | Content hash |
| `object_key_original` | VARCHAR | | S3 key for original file |
| `status` | VARCHAR | ENUM: UPLOADED, PROCESSING, READY, FAILED | Processing status |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `document_artifact`

Processing outputs stored in S3 (parsed text, JSON, HTML).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Artifact ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `document_version_id` | UUID | NOT NULL | FK → document_version |
| `kind` | VARCHAR | ENUM: PARSED_JSON, EXTRACTED_TEXT, RENDERED_HTML, THUMBNAIL | Artifact type |
| `object_key` | VARCHAR | | S3 key |
| `content_type` | VARCHAR | | MIME type |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `document_processing_job`

Async job queue for the processing pipeline. Uses `SKIP LOCKED` for worker polling.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Job ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `document_version_id` | UUID | NOT NULL | FK → document_version |
| `stage` | VARCHAR | ENUM: PARSE, EXTRACT_QUESTIONS, CHUNK, EMBED, FINALIZE | Pipeline stage |
| `status` | VARCHAR | ENUM: PENDING, RUNNING, DONE, FAILED | Job status |
| `attempt` | INTEGER | DEFAULT 0 | Retry count |
| `locked_at` | TIMESTAMP | | Lock acquisition time |
| `locked_by` | VARCHAR | | Worker instance ID |
| `error_code` | VARCHAR | | Error classification |
| `error_message` | TEXT | | Full error detail |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |
| `updated_at` | TIMESTAMP | NOT NULL, auto-set | |

**CHECK constraint:** `stage IN ('PARSE', 'EXTRACT_QUESTIONS', 'CHUNK', 'EMBED', 'FINALIZE')`

---

### `kb_chunk`

Text chunks extracted from documents for RAG retrieval.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Chunk ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `document_version_id` | UUID | NOT NULL | FK → document_version |
| `chunk_index` | INTEGER | | Ordering within version |
| `text` | TEXT | | Chunk content |
| `metadata` | JSONB | | `{sheet, sheetIndex, startRow, endRow, category, chunkIndex, type}` |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `kb_embedding`

Vector embeddings for knowledge base chunks.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Embedding ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `chunk_id` | UUID | NOT NULL | FK → kb_chunk |
| `embedding_model` | VARCHAR | | Model used (e.g. `qwen3-embedding:0.6b`) |
| `embedding` | TEXT | | Serialized float array (JSON) |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

> **Note:** Embeddings are stored as serialized JSON text and compared in-application using cosine similarity, rather than using pgvector's native `vector` type and operators. This is a known limitation.

---

### `questionnaire`

A security questionnaire to be answered.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Questionnaire ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `name` | VARCHAR | | Display name |
| `type` | VARCHAR | ENUM: SPREADSHEET, DOCUMENT, WEBSITE | Source format |
| `status` | VARCHAR | ENUM: IN_PROGRESS, APPROVED, COMPLETED | Workflow state |
| `progress_percent` | INTEGER | DEFAULT 0 | Answered percentage |
| `due_date` | DATE | | Deadline |
| `owner_user_id` | INTEGER | | FK → user_info |
| `approver_user_id` | INTEGER | | FK → user_info |
| `source_document_version_id` | UUID | | FK → document_version (if extracted from upload) |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |
| `updated_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `questionnaire_item`

Individual question within a questionnaire.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Item ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `questionnaire_id` | UUID | NOT NULL | FK → questionnaire |
| `item_index` | INTEGER | | Display ordering |
| `question_text` | TEXT | | Full question (may include `[Category] Item — Question`) |
| `response_type` | VARCHAR | ENUM: FREE_TEXT, YES_NO_NA, MULTI_SELECT, NUMBER, DATE, UNKNOWN | Expected answer format |
| `current_state` | VARCHAR | ENUM: UNANSWERED, SUGGESTED, DRAFTED, NEEDS_REVIEW, APPROVED | Workflow state |
| `source_location` | JSONB | | `{sheet, row, category, questionCol, answerCol}` |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `questionnaire_response`

User or pre-filled response to a questionnaire item.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Response ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `questionnaire_item_id` | UUID | NOT NULL | FK → questionnaire_item |
| `answer_text` | TEXT | | Answer content |
| `explanation` | TEXT | | Supporting reasoning |
| `status` | VARCHAR | ENUM: DRAFT, APPROVED | Review state |
| `created_by` | INTEGER | | FK → user_info |
| `approved_by` | INTEGER | | FK → user_info |
| `approved_at` | TIMESTAMP | | Approval timestamp |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |
| `updated_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `answer_suggestion`

AI-generated answer suggestion for a questionnaire item.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Suggestion ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `questionnaire_item_id` | UUID | NOT NULL | FK → questionnaire_item |
| `provider` | VARCHAR | | AI provider (e.g. `ollama`) |
| `model` | VARCHAR | | Model used (e.g. `qwen3:8b`) |
| `answer_text` | TEXT | | Generated answer |
| `citations` | JSONB | | `["kb_chunk:<uuid>", ...]` |
| `confidence` | DOUBLE | | Score 0.0 – 1.0 |
| `coverage_status` | VARCHAR | ENUM: OK, INSUFFICIENT_EVIDENCE | Evidence availability |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `answer_feedback`

User feedback on AI suggestions (thumbs up/down).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Feedback ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `answer_suggestion_id` | UUID | NOT NULL | FK → answer_suggestion |
| `thumb` | VARCHAR | ENUM: UP, DOWN | Rating |
| `comment` | TEXT | | Optional comment |
| `created_by` | INTEGER | NOT NULL | FK → user_info |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `answer_library_entry`

Reusable Q&A pairs built from approved questionnaire responses.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Entry ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `question_text` | TEXT | | Original question |
| `question_normalized` | TEXT | | Lowercased/trimmed for matching |
| `answer_text` | TEXT | | Approved answer |
| `explanation` | TEXT | | Supporting context |
| `source` | VARCHAR | ENUM: IMPORTED, GENERATED, MANUAL | How entry was created |
| `created_by` | INTEGER | | FK → user_info |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |
| `updated_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `answer_library_embedding`

Vector embeddings for answer library entries (future similarity search).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Embedding ID |
| `tenant_id` | UUID | NOT NULL | FK → tenant |
| `entry_id` | UUID | NOT NULL | FK → answer_library_entry |
| `embedding_model` | VARCHAR | | Model used |
| `embedding` | TEXT | | Serialized float array |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | |

---

### `task`

Generic task management (legacy feature).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK, auto-increment | Task ID |
| `tenant_id` | UUID | | FK → tenant |
| `name` | VARCHAR | NOT NULL | Task name |
| `description` | VARCHAR | | Task details |
| `priority` | VARCHAR(32) | NOT NULL, ENUM: LOW, MEDIUM, HIGH | |
| `status` | VARCHAR(32) | NOT NULL, ENUM: PENDING, IN_PROGRESS, COMPLETED | |
| `owner` | INTEGER | NOT NULL, FK → user_info | ON DELETE CASCADE |
| `code_repository` | INTEGER | FK → code_repository | ON DELETE CASCADE |

---

### `code_repository`

GitHub repository tracking (legacy feature).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK, auto-increment | Repository ID |
| `tenant_id` | UUID | | FK → tenant |
| `name` | VARCHAR | NOT NULL | Repository name |
| `owner` | VARCHAR | NOT NULL | GitHub owner |
| `url` | VARCHAR | | Repository URL |
| `stars` | INTEGER | | Star count |
| `open_issues_count` | INTEGER | | Open issues |
| `license` | VARCHAR | | License name |
| `status` | VARCHAR(32) | DEFAULT 'ACTIVE', ENUM: ACTIVE, DELETED, INVALID | |
| `created_at` | DATE | | |
| `updated_at` | DATE | | |

---

## Enum Reference

| Enum | Values |
|------|--------|
| DocumentType | `POLICY`, `QUESTIONNAIRE`, `OTHER` |
| DocumentVersionStatus | `UPLOADED`, `PROCESSING`, `READY`, `FAILED` |
| DocumentArtifactKind | `PARSED_JSON`, `EXTRACTED_TEXT`, `RENDERED_HTML`, `THUMBNAIL` |
| ProcessingJobStage | `PARSE`, `EXTRACT_QUESTIONS`, `CHUNK`, `EMBED`, `FINALIZE` |
| ProcessingJobStatus | `PENDING`, `RUNNING`, `DONE`, `FAILED` |
| QuestionnaireType | `SPREADSHEET`, `DOCUMENT`, `WEBSITE` |
| QuestionnaireStatus | `IN_PROGRESS`, `APPROVED`, `COMPLETED` |
| QuestionnaireItemState | `UNANSWERED`, `SUGGESTED`, `DRAFTED`, `NEEDS_REVIEW`, `APPROVED` |
| ResponseType | `FREE_TEXT`, `YES_NO_NA`, `MULTI_SELECT`, `NUMBER`, `DATE`, `UNKNOWN` |
| ResponseStatus | `DRAFT`, `APPROVED` |
| CoverageStatus | `OK`, `INSUFFICIENT_EVIDENCE` |
| AnswerSource | `IMPORTED`, `GENERATED`, `MANUAL` |
| FeedbackThumb | `UP`, `DOWN` |
| CodeRepositoryStatus | `ACTIVE`, `DELETED`, `INVALID` |
| TaskPriority | `LOW`, `MEDIUM`, `HIGH` |
| TaskStatus | `PENDING`, `IN_PROGRESS`, `COMPLETED` |

## S3 Object Key Structure

All file storage is tenant-scoped:

```
tenant/{tenant_id}/documents/{document_id}/versions/{version_id}/original
tenant/{tenant_id}/documents/{document_id}/versions/{version_id}/extracted_text
tenant/{tenant_id}/documents/{document_id}/versions/{version_id}/parsed_json
tenant/{tenant_id}/documents/{document_id}/versions/{version_id}/rendered_html
```
