# Repository Guidelines

## Project Structure & Module Organization
This repository is organized by runtime:

- `frontend/`: Vue 3 + Vite client. App code is in `src/`, with views under `src/views/`, layouts in `src/layouts/`, shared UI in `src/components/` and `src/component/`, and assets in `src/assets/` and `public/`.
- `backend/`: Spring Boot API. Main code is in `src/main/java/com/daodun`, configuration in `src/main/resources/`, and tests in `src/test/java/com/daodun`.
- `emotion-service/`: standalone Python service for emotion analysis.
- `realtime_dialog/java/`: separate Java demo client for realtime voice dialogue.
- `QAdocs/`: knowledge documents used by the backend RAG ingestion flow.

## Build, Test, and Development Commands
- `cd frontend && npm install`: install frontend dependencies.
- `cd frontend && npm run dev`: start the Vite dev server.
- `cd frontend && npm run build`: run `vue-tsc` and create a production bundle.
- `cd frontend && npm run lint`: run Oxlint and ESLint with autofix.
- `cd backend && ./mvnw spring-boot:run` or `mvnw.cmd spring-boot:run`: start the API on port `8081`.
- `cd backend && ./mvnw test`: run JUnit 5 and Spring Boot tests.
- `cd emotion-service && uvicorn app:app --host 0.0.0.0 --port 8091`: start the emotion service locally.

## Coding Style & Naming Conventions
Use 2-space indentation in Vue, HTML, CSS, and YAML, and 4 spaces in Java. Keep Vue single-file components in PascalCase, for example `InterviewView.vue`. Keep Java classes in PascalCase and packages lower-case under `com.daodun`. Use clear backend suffixes such as `Controller`, `Service`, `ServiceImpl`, and `Repository`. Run `npm run lint` before committing frontend work.

## Testing Guidelines
Backend tests use JUnit 5, Spring Boot Test, Mockito, and AssertJ. Add new tests under `backend/src/test/java/...` and keep names ending in `Test`, for example `InterviewPromptServiceTest`. Prefer focused service-level tests and mock external systems such as Redis, LLM calls, and voice providers.

## Commit & Pull Request Guidelines
Recent history mixes conventional prefixes like `feat:` and `chore:` with very terse fix commits. Prefer short, imperative commit messages with a scope when useful, for example `feat(frontend): add resume upload flow`. PRs should identify the affected module, summarize behavior changes, list verification commands, and include screenshots for UI changes.

## Security & Configuration Tips
Do not commit real credentials. `backend/src/main/resources/application.yml` contains local defaults, so prefer environment variables such as `ARK_API_KEY`, `SPRING_DATA_REDIS_PASSWORD`, and `EMOTION_SERVICE_ENDPOINT` for secrets and machine-specific settings. Treat `QAdocs/` and resume-upload flows as user content and validate changes carefully.
