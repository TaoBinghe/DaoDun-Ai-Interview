# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

AI模拟面试与能力提升软件 — an AI-powered mock interview platform for CS students. Supports voice+text multi-turn interviews with LLM-driven evaluation and emotion analysis. Built as a Vue 3 frontend + Spring Boot backend + Python ML service.

## Module Map

| Directory | Role |
|-----------|------|
| `frontend/` | Vue 3 + Vite + TypeScript + Tailwind CSS 4 + Element Plus + Pinia |
| `backend/` | Spring Boot 3.4, Java 17, JPA + PostgreSQL + pgvector, Redis, Spring Security + JWT |
| `emotion-service/` | Standalone FastAPI + YOLO (face detection) + YOLO classifier (AffectNet emotion), port `8091` |
| `realtime_dialog/java/` | Reference demo for Volcano voice dialogue — not part of the main app |
| `QAdocs/` | Markdown knowledge base ingested into pgvector for RAG during interviews |

## Build, Test, and Run

```bash
# Frontend (port varies, Vite default 5173)
cd frontend && npm install
cd frontend && npm run dev          # dev server
cd frontend && npm run build        # type-check + production build
cd frontend && npm run lint         # Oxlint + ESLint with autofix

# Backend (port 8081)
cd backend && ./mvnw spring-boot:run
cd backend && ./mvnw test           # JUnit 5 + Spring Boot Test + Mockito

# Emotion service (port 8091)
cd emotion-service && uvicorn app:app --host 0.0.0.0 --port 8091
```

## Core Architecture

### Interview Lifecycle (HTTP REST + WebSocket)

```
User creates session  →  GET /api/interview/session  (select position + resume)
       ↓
User joins interview  →  WebSocket /ws/interview?token=...  (voice) or HTTP-only (text)
       ↓
LLM generates welcome →  PromptService.buildWelcomeMessages() → LLM → subtitle + TTS audio
       ↓
┌─ Multi-turn loop ─────────────────────────────────────────────────┐
│  1. User speaks (audio_chunk → audio_commit) or types (text_answer)│
│  2. STT: streaming ASR (api/v2/asr) or buffered recognition        │
│  3. InterviewService.postTurn(userId, sessionId, request)          │
│     a. Save USER turn                                              │
│     b. Retrieve RAG knowledge chunks (pgvector cosine similarity)   │
│     c. PromptService.buildMessages(...) assembles LLM prompt        │
│     d. ArkChatService calls doubao-seed-1-6-flash-250828          │
│     e. Parse LLM JSON decision → action: follow_up/next_question   │
│                                   /change_question/complete         │
│     f. If next_question: draw next question, attach to reply        │
│     g. Save INTERVIEWER turn, return response                       │
│  4. TTS synthesis → interviewer_audio + subtitle back to browser   │
│  5. Emotion frames captured periodically → YOLO analysis            │
└────────────────────────────────────────────────────────────────────┘
       ↓
User ends session   →  POST /api/interview/session/{id}/complete
       ↓
Async evaluation    →  EvaluationService generates multi-dimension report
       ↓
View report         →  GET /api/interview/session/{id}/evaluation
```

### LLM Decision State Machine

The LLM does NOT just chat — it returns structured JSON parsed into `LlmDecision`:

```json
{
  "action": "follow_up|next_question|change_question|complete",
  "reply": "面试官的下一句话",
  "next_difficulty": 1|2|3,
  "next_type": "technical_qa|algorithm|scenario|behavioral",
  "key_points": ["考察点1", "考察点2"]
}
```

If JSON parsing fails, the system **gracefully degrades** to `follow_up` with the raw response as reply text — the interview never crashes.

### WebSocket Message Protocol (`/ws/interview`)

Browser → Server:
- `audio_chunk` — base64 audio blob (streaming, sent in chunks)
- `audio_commit` — signals end of utterance, triggers STT + LLM
- `text_answer` — text input mode, bypasses STT
- `play_welcome` — requests welcome message (with optional `textOnly: true` to skip TTS)
- `emotion_frame` — base64 webcam snapshot for emotion analysis
- `ping` → server replies `pong`

Server → Browser:
- `connected` — handshake confirmation
- `user_transcript` — STT result (the recognized text)
- `subtitle` — interviewer's text reply (always sent; may include `codingProblemContent` for algorithm questions)
- `interviewer_audio` — base64 TTS audio
- `emotion_status` — per-frame emotion result (`{emotion, confidence, hasFace}`)
- `error` — non-fatal errors (connection stays open)

### RAG Pipeline

1. **Ingestion** at startup: `KnowledgeBaseInitializer` scans `QAdocs/` → chunks text → calls Volcano embedding API → stores vectors in PostgreSQL via pgvector
2. **Retrieval** per turn: `KnowledgeRetrievalService` embeds the user's latest answer → cosine similarity search → top-K chunks injected into LLM system prompt
3. Configuration in `application.yml`: `rag.retrieval.top-k: 3`, `rag.retrieval.min-score: 0.3`

### Emotion Analysis Pipeline

Browser captures webcam frames (throttled, configurable min interval) → sends as base64 via WebSocket `emotion_frame` → backend `EmotionAnalysisService` forwards to Python `emotion-service` (FastAPI, port 8091) → YOLO face detection → YOLO AffectNet emotion classifier → returns dominant emotion + confidence.

### External Service Dependencies

All LLM, embedding, STT, and TTS go through **Volcano Ark (火山引擎方舟)**:

| Env Variable | Purpose |
|--------------|---------|
| `ARK_API_KEY` | LLM chat + embedding API key |
| `ARK_EMBEDDING_ENDPOINT` | Embedding endpoint ID (format: `ep-xxxxx`) |
| `VOLCANO_STT_ENDPOINT` | STT WebSocket URL (default: `api/v2/asr` for streaming) |
| `VOLCANO_STT_API_KEY` / `VOLCANO_STT_APP_ID` | STT credentials |
| `VOLCANO_TTS_API_KEY` / `VOLCANO_TTS_APP_KEY` | TTS credentials |
| `EMOTION_SERVICE_ENDPOINT` | Emotion service URL (default: `http://localhost:8091/analyze/frame`) |
| `SPRING_DATA_REDIS_PASSWORD` | Redis password |

## Backend Structure

```
com.daodun
├── controller/     → REST endpoints (Auth, Interview, Resume, Knowledge, Position, Question, User)
├── websocket/      → InterviewWebSocketHandler + WsAuthHandshakeInterceptor (JWT auth via query param)
├── service/        → Interfaces + impl/ (InterviewService, ArkChatService, VoiceRecognitionService, etc.)
├── config/         → SecurityConfig (stateless JWT), CorsConfig, RedisConfig, AsyncConfig, DataInitializer
├── entity/         → JPA entities: User, InterviewSession, InterviewTurn, Position, Question, KnowledgeDocument, etc.
├── repository/     → Spring Data JPA repositories
├── dto/            → Request/response DTOs organized by domain (interview/, resume/, voice/)
├── voice/volcano/  → Low-level Volcano SDK clients (streaming ASR, TTS WebSocket, auth)
└── util/           → JwtUtil
```

## Frontend Structure

```
frontend/src/
├── views/          → Page-level components: LoginView, HomeView, InterviewView, InterviewReportView, ResumeView, ProfileView
├── components/     → Reusable UI (including discussion/ subdir for forum feature)
├── component/      → Legacy/shared components
├── layouts/        → MainLayout.vue (sidebar + top nav shell)
├── router/         → Vue Router with lazy-loaded routes, MainLayout wraps authenticated pages
├── stores/         → Pinia stores (user.ts, counter.ts)
├── services/       → voiceWebSocket.ts (WebSocket client encapsulating the interview protocol)
└── utils/          → Shared utilities
```

## Coding Conventions

- **Vue**: 2-space indent, PascalCase SFC filenames, Tailwind utility classes
- **Java**: 4-space indent, PascalCase classes, lower-case packages under `com.daodun`
- **Backend naming**: `*Controller`, `*Service` (interface), `*ServiceImpl`, `*Repository`
- **Testing**: JUnit 5 + Spring Boot Test + Mockito + AssertJ. Service-level tests preferred; mock Redis, LLM, and voice providers
- **Commit messages**: short, imperative, prefixed with scope (`feat(frontend): ...`, `fix(backend): ...`)

## Security Notes

- Real credentials in `application.yml` are placeholders; production must use environment variables
- JWT access token: 2h expiry; refresh token: 7d (14d with "remember me")
- All endpoints except `/api/auth/**` require JWT; WebSocket auth via `?token=` query param in handshake
- Passwords bcrypt-hashed; verification codes stored in Redis with TTL
