See [BYO FileStore implementation docs](https://github.com/hmcts/pe_arch_design_docs/blob/master/mbd_filestore/implementation/overview.md) in `pe_arch_design_docs`.

---

## Service constraints

### Email attachment size limit — 15 MB maximum

Notification Notify enforces a **15 MB hard limit** on email attachments imposed by the
downstream email providers (GOV.UK Notify, Office 365). Files above this limit are rejected
before download with HTTP 413.

See [`patterns/email-attachment-limits.md`](https://github.com/hmcts/pe_arch_design_docs/blob/master/mbd_filestore/implementation/patterns/email-attachment-limits.md)
for the full rationale, failure behaviour, and instructions for raising the limit in future.
