# CLAUDE.md — cpp-context-notification-notify

Guidance for Claude Code when working in this context. For the platform-wide DDD/CQRS
conventions and build commands, see the workspace-root `CLAUDE.md`.

## What this context does

Notification bounded context: sends emails (GOV.UK Notify + SMTP), letters, and POCA
emails; tracks notification status in a viewstore. Because it sends email, it is the
**only** context that depends on the Jakarta Mail stack and GreenMail — this is the main
way its upgrade work differs from a "standard" context.

## Module notes

- `notificationnotify-azure` — an **Azure Functions** app (`notificationnotify-azure-functions`,
  packaged jar, deployed to Azure PaaS, **not** WildFly) plus a storage-accounts helper. It is
  a reactor module and so is built under the same JDK as the rest of the context.
- `notificationnotify-event/notificationnotify-event-processor` — runs in WildFly; hosts the
  email sending / bounce handling and uses Jakarta Mail.

## Java 25 / WildFly 40 / Jakarta EE 11 upgrade — context-specific fixes

These are the non-obvious fixes beyond the generic per-context playbook
(`cpp-framework-java-upgrade-pilot/guides/CLAUDE-upgrade-playbook.md`):

1. **Jakarta Mail (`javax.mail` → `jakarta.mail`).**
   - `event-processor` (WildFly-deployed): depend on `jakarta.mail:jakarta.mail-api` with
     scope `provided` — WildFly 40 supplies the Angus implementation at runtime. Do **not**
     bundle an impl into a container deployment.
   - `azure-functions` (standalone, no container): bundle the impl —
     `org.eclipse.angus:angus-mail` (compile). Version via the root-pom `angus-mail.version`
     property (not yet BOM-managed).
   - Old dep was `com.sun.mail:javax.mail`.

2. **GreenMail must be 2.x** (`greenmail.version` in the root pom). GreenMail 1.x is built
   against `javax.mail`; 2.0.0+ switched to `jakarta.mail`. The IT email tests import
   `jakarta.mail`, so 1.x will not compile. Not BOM-managed.

3. **`org.everit.json.schema` groupId change.** The 25.104.x common-bom manages this under the
   jitpack coordinates `com.github.everit-org.json-schema` (the old 17.x parent managed the
   legacy `org.everit.json` groupId). Use `com.github.everit-org.json-schema:org.everit.json.schema`
   (version from BOM). Same `org.everit.json.schema.*` API.

4. **azure-functions compiler plugin.** The module used to pin
   `plugins.maven.compiler.version=3.8.0`, which cannot target Java 25. Remove the override so
   it inherits the parent's `3.15.0`.

5. **DeltaSpike → JPA.** `NotificationRepository` was a DeltaSpike `@Repository` abstract
   `EntityRepository`. It is now a concrete `@ApplicationScoped` bean with
   `@PersistenceContext(unitName = "notificationnotify") EntityManager`, implementing `save`
   (merge), `findBy` (find), and the existing criteria `findNotifications`. Its test uses
   JUnit 5 + `HibernateTestEntityManagerProvider` against the test persistence unit
   `notificationnotify-test-persistence-unit` (H2, `RESOURCE_LOCAL`).

6. **JUnit 5 only.** JUnit 4 was a transitive hangover from the DeltaSpike test-control /
   OpenEJB stack. With DeltaSpike removed, no module depends on JUnit 4 — do not reintroduce
   `org.junit.Test` / `@RunWith`. Both former JUnit 4 tests (`NotificationRepositoryTest`,
   `NotificationFactoryTest`) are now JUnit 5.

## Upgrade branch / PR targets

- Spike branch: `java-25-wildfly-40-upgrade-spike` (off `main`).
- PR target (integration/merge): `team/25.104.x`.
- Pipeline: `azure-pipelines.yaml` on the JDK25/WF40 track — pool `ubuntu-j25-postgres`,
  templates `ref: 'wildfly40'`, `aksDeployBranch: 'wildfly40'`. Non-Camunda, so no `isCamunda`.
