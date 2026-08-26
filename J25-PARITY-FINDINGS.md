# notification-notify — Java 17 → Java 25 Behavioural Parity Findings

**Context:** cpp-context-notification-notify (Platform Engineering-owned; GOV.UK Notify integration).
Upgraded to Java 25 / WildFly 40 / Jakarta EE 11 on `team/25.104.x` (service-parent-pom 25.104.0-M10).
Baseline: `main` (service-parent-pom 17.104.0, Hibernate 5.4.24). Jira: PEG-3535 (under PEG-3377).

Method: CTP guide *Parity Testing Java17 → Java25* (Confluence 1990371020), 24-BC catalogue —
**J17 is the source of truth; a J25 difference is a finding, not a loosened assertion.** Everything below
was run/captured, not assumed. Date: 2026-08-26.

---

## Headline

**No functional-equivalence blocker.** Golden baseline intact (**0 of 25** test-resource JSON files changed
since J17), single repository rewrite preserved semantics, and the only actionable item is the BC-20
rule-count guard. Notably, capturing J17 empirically **overturned the guide's BC-11 premise** (see §2).

## 1. Persistence — clear

Single repository: `NotificationRepository`.
- **BC-01 / BC-02:** N/A. J17 was a DeltaSpike `EntityRepository` + `CriteriaSupport`; J25 rewrote it to plain
  JPA (Criteria API `findNotifications` → `List`; `findBy(id)` → `entityManager.find(...)` = null ↔ null on
  both). No throwing single-result finder.
- **BC-04:** N/A — no primitive fields on entities.
- **BC-05 / BC-06:** none — no JPQL `!= null` guards, no lazy collections.

## 2. BC-11 — CONFIRMED PARITY (the guide's premise was wrong; guide corrected)

`NotificationNotifyCommandSender` reaches `add(key, value)` with a potentially-null value:
`recordCheckBouncedEmailRequestAsFailed(server, e.getMessage())` and `recordCheckPocaEmailRequestAsFailed(...)`
pass `e.getMessage()` (from a caught `MessagingException | IOException`), which **can be null**.

The guide (BC-11) claimed glassfish silently accepted `add(key, null)` while Parsson throws — i.e. a J25
regression. **Empirically captured on J17 first, then J25:**
- **J17** (glassfish javax.json via `JsonObjects.createObjectBuilder()`): throws
  `NullPointerException: Value in JsonObjects name/value pair cannot be null`.
- **J25** (Parsson jakarta.json via the same helper): throws the **identical** NPE.

The framework's `JsonObjects` helper has its **own null-guard** that fires on both javax and jakarta — so
glassfish never "silently accepted" it here. **BC-11 is parity-neutral, not a J25 regression.** The
null-`getMessage()` path is a *pre-existing latent bug that NPEs identically on both runtimes*. Matching J17
means leaving J25 to throw the same way — **no code fix**. (Guarding the null would make J25 *diverge* from J17;
if we want to harden it, do so on both branches as a separate change — optional, not a parity item.)

The guide's BC-11 row was corrected to "Refuted / parity" (Confluence 1990371020 v6) on the strength of this.

## 3. Actionable

| Item | Detail | Status |
|---|---|---|
| **BC-20** | 2 Drools kbases (COMMAND_API, QUERY_API); could silently load 0 rules → vacuous deny-tests. | ✅ **DONE** — `AccessControlRuleCountTest` in command-api and query-api asserting `> 0` rules; both green on J25. Platform follow-up: add to the shared `BaseDroolsAccessControlTest`. |
| **BC-07** | not present — `liquibase.properties` has no `liquibase.hub.mode`. | n/a |

## 4. Golden-master baseline

**0 of 25** test-resource JSON files changed J17 → J25 (0 expected/output goldens), so J17-recorded
expectations still hold on the J25 runtime — output parity preserved by construction.

## 5. Recommendation

No functional-equivalence blocker. BC-20 guards added. Optional (non-parity) follow-up: null-guard
`e.getMessage()` at the two email-failure call sites so a null-message exception doesn't NPE the
failure-recording command — but that is a latent bug identical on J17 and J25, so raise it separately and
apply to both branches, not as part of this parity change.
