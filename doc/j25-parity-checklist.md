# notification-notify — J17 → J25 Behavioural Parity Checklist

Companion to *Parity Testing Java17 → Java25* (Confluence CTP/1990371020). Full write-up: `/J25-PARITY-FINDINGS.md`.
Jira: PEG-3535 (under PEG-3377). Scanned `main` (spp 17.104.0) vs `team/25.104.x` (spp M10).

## Applies

| BC | Trigger | Disposition | Status |
|----|---------|-------------|--------|
| **BC-20** | 2 Drools kbases (COMMAND_API, QUERY_API) could silently load 0 rules → vacuous deny-tests. | Fix (guard) | ✅ `AccessControlRuleCountTest` in both modules (`>0` rules), green |
| **BC-11** | `add(key, e.getMessage())` where `getMessage()` can be null (bounced/poca email failure handlers). | **Parity — no fix** | ✅ Captured J17 (throws framework NPE) → J25 throws identically. Guide corrected (v6). Latent bug identical on both. |

## Cleared / N-A

| BC | Finding |
|----|---------|
| BC-01/02 | All finders `List`; DeltaSpike `CriteriaSupport` `NotificationRepository` → plain JPA Criteria preserving `List`; `findBy(id)`→`find()` null↔null. |
| BC-04 | No primitive entity fields. |
| BC-05 / BC-06 | No JPQL null-guards; no lazy collections. |
| BC-07 | Not present (no `liquibase.hub.mode`). |

## Golden-master baseline

0 of 25 test-resource JSON files changed since J17 (0 output goldens) → output parity preserved.

## Coverage tracker

- [x] BC-20 rule-count guards added (COMMAND_API, QUERY_API), green
- [x] BC-11 captured J17-first — parity confirmed (both throw framework NPE); guide corrected
- [x] BC-01/02/04/05/06/07 verified N-A / not present
