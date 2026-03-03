# Transmute Playground

Interactive web UI for the [Transmute](../README.md) media processing library. Upload files, inspect their structure, build transform pipelines, and get generated Kotlin code — all from the browser.

## Architecture

```
┌──────────────────────┐       ┌──────────────────────┐
│   Next.js Frontend   │──────▶│   Ktor API Server    │
│  (React + HeroUI)    │ REST  │  (Netty, port 8080)  │
│  static export -> out │◀──────│                       │
│  port 3000 (dev)     │  WS   │  Transmute instance   │
└──────────────────────┘       │  GStreamer plugin      │
                               └──────────────────────┘
```

| Component | Stack | Location |
|-----------|-------|----------|
| **Frontend** | Next.js 15, React 19, HeroUI, Tailwind CSS 4, TypeScript | `web/` |
| **Server** | Ktor 3, Netty, kotlinx.serialization | `server/` |
| **Shared Models** | Kotlin/JVM data classes | `shared/` |

### How it works

- The **server** creates a `Transmute` instance with the GStreamer plugin installed and exposes REST endpoints for format discovery, file upload/inspect, transform execution, and plugin management
- Format and plugin data is derived **dynamically** from the live codec registries — nothing is hardcoded
- The **frontend** is a Next.js static export (`output: 'export'`). In production, the Ktor server serves these files from `/app/static`. In development, Next.js runs its own dev server on port 3000
- Transform pipelines are constructed visually and executed on the server; the server also generates equivalent Kotlin code

## Development

### Prerequisites

- **JDK 17+** (for the Ktor server)
- **Node.js 22+** and **npm** (for the frontend)
- **GStreamer** runtime installed (for codec operations)

### Quick start

From the repository root:

```bash
# Start both frontend and backend together
./gradlew :transmute-playground:dev
```

This launches:
- **Frontend** at `http://localhost:3000` (Next.js dev server with hot reload)
- **Backend** at `http://localhost:8080` (Ktor server)

### Running individually

```bash
# Frontend only
./gradlew :transmute-playground:frontendDev

# Backend only
./gradlew :transmute-playground:server:run
```

### Building

```bash
# Build the Next.js static export
./gradlew :transmute-playground:frontendBuild

# Build the server shadow JAR
./gradlew :transmute-playground:server:shadowJar
```

## Docker

```bash
# Build from repository root
docker build -f transmute-playground/Dockerfile -t transmute-playground .

# Run
docker run -p 8080:8080 transmute-playground
```

Or with docker-compose:

```bash
cd transmute-playground
docker compose up
```

The Docker image uses a 3-stage build:
1. **Node.js** stage: builds the Next.js static export
2. **Gradle** stage: builds the Ktor server shadow JAR
3. **Runtime** stage: Eclipse Temurin JRE + GStreamer runtime

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Health check with plugin/format counts |
| `GET` | `/api/formats?domain=IMAGE\|AUDIO\|VIDEO` | List supported formats (dynamically derived) |
| `GET` | `/api/transforms?domain=IMAGE\|AUDIO\|VIDEO` | List available transforms |
| `POST` | `/api/upload` | Upload a file (multipart) |
| `POST` | `/api/inspect/{handle}` | Inspect an uploaded file |
| `GET` | `/api/files/{handle}` | Download a file |
| `POST` | `/api/transform` | Execute a transform pipeline |
| `GET` | `/api/plugins` | List installed plugins |
| `GET` | `/api/plugins/{key}` | Get plugin details |
| `PUT` | `/api/plugins/{key}` | Update plugin (enable/disable features) |
| `WS` | `/ws/progress` | Real-time progress events |

## Project Structure

```
transmute-playground/
├── build.gradle.kts          # Umbrella module + dev tasks
├── Dockerfile                # 3-stage Docker build
├── docker-compose.yml
├── server/                   # Ktor backend
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── dev/transmute/playground/
│           ├── PlaygroundServer.kt
│           ├── TransmuteService.kt
│           └── routes/
├── shared/                   # Kotlin data classes (JVM)
│   ├── build.gradle.kts
│   └── src/commonMain/kotlin/
└── web/                      # Next.js frontend
    ├── package.json
    ├── next.config.js
    ├── postcss.config.js
    └── src/
        ├── app/              # Next.js App Router pages
        ├── components/       # React components
        └── lib/              # API client & TypeScript types
```
