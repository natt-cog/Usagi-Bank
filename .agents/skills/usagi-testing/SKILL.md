---
name: testing-usagi-bank
description: Run and browser-test Usagi Bank's legacy Java 8 JSP app, seed state, confirmations, and role boundaries.
---

# Environment
- Run from the repository with `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -q -B spring-boot:run`.
- Before restarting, inspect `lsof -iTCP:8080 -sTCP:LISTEN`; terminate only the app's stale listener.
- Wait for `Started UsagiBankApplication` and use `http://localhost:8080/usagi/login`.
- H2 is in-memory: every restart resets 5 branches, 10 customers, 15 accounts (12 active). Restart after source changes; do not reuse an app whose target directory was cleaned while running.
- Maven needs a reachable mirror in `~/.m2/settings.xml`; Java 8 is required.
- Ensure a Japanese font is installed before launching the browser (`fc-list :lang=ja`). User-local Noto Sans CJK JP under `~/.local/share/fonts` plus `fc-cache -f` works. Restart Chrome before recording if fonts were added.

# Devin Secrets Needed
None for the local demo. Public fixture logins are documented by the app:
`teller/teller123`, `admin/admin123`, `auditor/audit123`.
Do not use these accounts against a production service.

# Browser workflows
- 金額 inputs normalize full-width digits and commas on blur.
- Deposit, withdrawal, transfer, status changes and account opening use native confirm dialogs: submit then accept.
- Customer birth dates use `yyyy/MM/dd`; new customers start 未確認. Set KYC 確認済 before opening accounts.
- The first new customer on fresh seed is CIF `0000000011`; first new branch-001 account is `1000006`.
- Account `001/1000001` starts at 1,250,000 with 3 ledger entries. The ledger has 20 entries per page; create enough real transactions to exercise next/previous.
- DBコンソール nav is intentionally admin-only.

# Role testing
- Test both hidden controls and server authorization. Use browser-origin POSTs carrying the logged-in user's own valid CSRF token; otherwise 403 may only prove CSRF enforcement.
- Auditor: account/customer reads allowed; account/customer mutations and transfers denied.
- Teller: deposits, withdrawals, transfers and customer operations allowed; status changes denied.
- Admin: status and batch exports allowed.
- API routes support HTTP Basic. When testing that independently of UI login, omit browser credentials and supply the Basic header; never extract session cookies.
- Account JSON exposes Japanese labels; `/usagi/manage/health` returns UP.
- Check shared footer Japanese rendering on several page types, not just main content.
