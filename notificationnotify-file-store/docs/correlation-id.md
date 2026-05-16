# Correlation ID Convention

Every blob stored by the platform carries a `correlation_id` Azure Blob metadata
key. This document explains what the value means, how it is set, and why.

---

## What is the correlation ID?

The `correlation_id` is a UUID stored as Azure Blob metadata on every blob uploaded
to a container.  It ties a stored file back to the business entity that caused it to
be created — the case, notification request, report, or command that owns it.

Its primary purpose is lifecycle management: "which blobs belong to this transaction
chain?"  Secondary uses include audit and debugging (tracing a stored file back to
its originating event).

---

## Per-caller values (notification-notify reference)

| Caller | `correlation_id` value | Blob path | Notes |
|---|---|---|---|
| `PocaEmailsTask` | `randomUUID()` — a fresh correlation per upload attempt | `internal/{fileId}` | UC1. New UUID generated per `uploadSingleDocument()` call; fileId also `randomUUID()` (different from correlationId). |
| `IngestFileCommandHandler` → `FileIngestor` | Provided by the sender in the `ingest-file` command payload (`correlationId` field) | `internal/{fileId}` | UC2 receiver. The owner service sets the correlation ID when it mints the SAS and dispatches the command; the receiver carries it forward atomically in the copy. |
| Upload interceptor (generic pattern) | Causation chain from `envelope.metadata().causation()`, joined by commas. Falls back to `envelope.metadata().id()` if causation is empty. | `internal/{fileId}` | For commands that upload files via the REST interceptor; fileId should be `randomUUID()`. |

---

## Identity-as-path pattern

For single-upload callers where the business entity ID is a stable UUID, use
`fileId = correlationId`.  The blob path becomes:

```
published/notification-results/{resultId}
inbox/notification-templates/{templateId}
```

This makes the path predictable from the business context, which:
- Lets callers look up a blob without storing the fileId separately
- Makes tests assertable with specific `eq(...)` matchers — no `anyString()` needed
- Ensures a re-upload for the same entity overwrites the same path (idempotent under retry)

---

## Name-based UUID pattern

For callers that may upload multiple blobs per handler invocation (e.g. two language
variants), derive `fileId` from the filename:

```java
final UUID fileId = UUID.nameUUIDFromBytes(fileName.getBytes(UTF_8));
```

`nameUUIDFromBytes` produces a type-3 (MD5-based name) UUID — deterministic for a
given input string, guaranteed different for different strings.  This allows multiple
blobs with the same `correlation_id` but different `fileId` values.

---

## Upload interceptor — causation chain

The upload interceptor is the only caller that stores a comma-separated causation
chain rather than a single UUID:

```java
private static String correlationIdFrom(final JsonEnvelope envelope) {
    final List<UUID> causation = envelope.metadata().causation();
    return causation.isEmpty()
            ? envelope.metadata().id().toString()
            : causation.stream().map(UUID::toString).collect(joining(","));
}
```

The causation list is the ordered chain of command/event IDs that led to the
current message.  Storing all of them enables lifecycle queries across the full
transaction chain.

---

## Design origin

The metadata convention — `correlation_id` and `filename` as required Azure Blob
metadata keys — is mandated by **BYOFS-1.3** in the v6 design
(`byo-filestore-azure-native-design_v6.md`).

The identity-as-path pattern was adopted in the SJP spike to make blob paths
deterministic and testable.  It is not explicitly specified in the design docs but
is consistent with the v6 principle that every blob should be traceable to its
originating business context via `correlation_id`.

The design docs are in
[`hmcts/pe_arch_design_docs`](https://github.com/hmcts/pe_arch_design_docs)
under `mbd_filestore/`.
