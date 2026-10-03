# Token Standard Auditor (Java)

A local contract-standard compliance checking service. Submit a contract's ABI and bytecode, and
receive a severity-classified compliance report with findings and locations.

## Features

- **ABI + bytecode parsing** with distinguishable errors for malformed input, unknown types and
  unparseable bytecode.
- **Mainstream token standards**: ERC-20 (fungible), ERC-721 (non-fungible) and ERC-1155 (multi-token),
  covering required function signatures, return conventions, required events and indexed-parameter
  rules. Functions declared in the ABI but not implemented in the bytecode are reported as
  non-compliant.
- **Three severity levels**: ERROR, WARNING and INFO. Reports are deterministic: re-checking the same
  contract against the same rule set always produces identical conclusions.
- **Versioned, runtime-updatable rule sets** per standard. Each check snapshots the rule set at
  submission time, so in-flight checks are unaffected by later updates. Checks without an explicit
  version use the standard's default version deterministically.
- **Batch + concurrent submission** with content-based caching. Only contracts whose content changes
  are re-checked; concurrent submissions never observe stale conclusions.
- **Cancellation + timeout**. Cancelled or timed-out checks leave no partial report; completed checks
  are unaffected.
- **Persistence + crash recovery**. Completed reports survive restarts; incomplete checks are
  discarded on restart (no incomplete reports are ever visible).

## Rule Model

A **rule** is a stateless check against a parsed contract (`ContractContract` = ABI functions/events
+ parsed bytecode). Each rule has a unique id, a severity and a description, and returns zero or more
`Finding`s.

Rules are grouped into versioned **rule sets** (`RuleSet`) keyed by `(standard, version)`. The built-in
rule sets are:

| Standard | Version | Description |
|----------|---------|-------------|
| ERC20 | 1.0 | ERC-20 Fungible Token Standard |
| ERC721 | 1.0 | ERC-721 Non-Fungible Token Standard |
| ERC1155 | 1.0 | ERC-1155 Multi Token Standard |

Each check snapshots the rule set for the requested `(standard, version)` at submission time. Rule
updates replace the registry's rule set; they never mutate snapshots held by in-flight checks.

### Rule types

Rules are declaratively configurable for runtime updates. Supported rule types:

| Type | Parameters | Checks |
|------|------------|--------|
| `REQUIRED_FUNCTION` | `signature` | Function is declared in ABI and implemented in bytecode |
| `REQUIRED_EVENT` | `signature`, `indexed` | Event is declared, emitted, and indexed params match |
| `FUNCTION_OUTPUT` | `signature`, `outputs` | Function return types match expected canonical types |
| `FUNCTION_MUTABILITY` | `signature`, `mutability` | Function state mutability matches expected value |
| `ABI_BYTECODE_CONSISTENCY` | — | All ABI-declared functions are implemented in bytecode |
| `OPTIONAL_FUNCTION` | `signature` | Recommended function is declared and implemented |
| `EVENT_INDEXING` | `signature`, `indexed` | Existing event's indexed params match expected positions |

## Severity Definitions

| Severity | Meaning |
|----------|---------|
| ERROR | Non-compliance with the target standard (missing required function/event, wrong signature, missing indexed params, declared-but-not-implemented). A report with any ERROR is `compliant: false`. |
| WARNING | Recommended convention violated (return type mismatch, wrong mutability, missing optional function). The contract is still broadly compliant. |
| INFO | Informational observation with no compliance impact. |

## Caching Semantics

Results are cached by **content fingerprint**:

```
contentKey = standard | version | SHA256(abi) | SHA256(bytecode)
```

- Submitting the same content again returns the cached report immediately (no re-evaluation), marked
  `cached: true` with `cachedFrom` pointing at the original check.
- Changing the ABI or bytecode changes the fingerprint, so the contract is re-checked.
- Concurrent submissions of the same content each compute independently but deterministically, so no
  submission ever returns a stale conclusion.

## Persistence & Crash Recovery

- Check records are persisted as JSON files under `auditor.store-dir` (default `./data/checks`),
  written atomically (temp file + move) so a crash never leaves a partially written report.
- On startup, records in a terminal state (`COMPLETED`, `CANCELLED`, `TIMED_OUT`, `FAILED`) are
  loaded and remain queryable.
- Records left in a non-terminal state (`PENDING`, `RUNNING`) from a crash are **discarded** on
  restart: they represent incomplete checks and must never be visible as reports.

## API Overview

### Checks

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/checks` | Submit a single check |
| POST | `/api/checks/batch` | Submit a batch of checks |
| GET | `/api/checks` | List all checks |
| GET | `/api/checks/{id}` | Get a check by id |
| POST | `/api/checks/{id}/cancel` | Cancel a check (no-op if already terminal) |

### Rules

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/rules` | List rule sets (optional `standard`, `version` filters) |
| GET | `/api/rules/standards` | List registered standards |
| PUT | `/api/rules/{standard}/{version}` | Replace a rule set at runtime |

### Parsing

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/parse/abi` | Parse an ABI, returning functions and events |
| POST | `/api/parse/bytecode` | Parse bytecode, returning selectors and topics |

### Error codes

| Code | Meaning |
|------|---------|
| `ABI_PARSE_ERROR` | ABI is malformed, has an unknown item type or unknown Solidity type |
| `BYTECODE_PARSE_ERROR` | Bytecode is empty, not hex or has odd length |
| `UNKNOWN_STANDARD` | Requested standard is not registered |
| `UNKNOWN_VERSION` | Requested version is not registered |
| `CHECK_NOT_FOUND` | No check exists with the given id |
| `RULE_CONFIGURATION_ERROR` | Rule descriptor is invalid |

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `auditor.store-dir` | `./data/checks` | Directory for persisted check records |
| `auditor.check.threads` | `0` (available processors) | Worker threads for check evaluation |
| `auditor.check.timeout-seconds` | `60` | Default per-check timeout |

## Building & Testing

```bash
mvn test
```
