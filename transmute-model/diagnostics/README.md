# transmute-model:diagnostics

Types for reporting problems found during media file inspection.

## Overview

Defines severity levels, issue codes, and contextual information for problems
and observations discovered while parsing or inspecting media files.

## Key Types

| Type | Purpose |
|------|---------|
| `InspectionIssue` | A single problem/observation (severity, code, message, context) |
| `IssueSeverity` | Severity level: `Info`, `Warning`, `Error` |
| `IssueCode` | Machine-readable issue code (inline value class) |
| `IssueContext` | Location context: byte range, stream ID, detail |

## Dependencies

- `transmute-model:core`
- `transmute-model:identify`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
