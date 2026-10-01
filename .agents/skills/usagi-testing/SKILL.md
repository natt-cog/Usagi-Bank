---
name: testing-usagi-bank
description: Run and browser-test Usagi Bank (Kotlin / Spring Boot 1.5 / JSP, built and run on JDK 8), seed state, confirmations, and role boundaries.
---

# Environment
- Kotlin 1.9 sources (`src/main/kotlin`, `src/test/kotlin`) on Spring Boot 1.5.22 / JSP; the project only builds and runs on JDK 8.
- Run from the repository with `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -q -B spring-boot:run`.
- Before restarting, inspect `lsof -iTCP:8080 -sTCP:LISTEN`; terminate only the app's stale listener.
- Wait for `Started UsagiBankApplication` and use `http://localhost:8080/usagi/login`.
- H2 is in-memory: every restart resets 5 branches, 10 customers, 15 accounts (12 active). Restart after source changes; do not reuse an app whose target directory was cleaned while running.
- Maven needs a reachable mirror in `~/.m2/settings.xml`; `mvn -B test` = 39 tests, `mvn -B verify` adds `ktlint:check`.
- If VS Code's JDT language server is running, disable its autobuild (user setting `"java.autobuild.enabled": false`): it can write error stubs into Maven's `target/classes` while the app runs. Symptom: HTTP 500 with `Name for argument type [java.lang.String] not available...`; confirm with `javap -v` on the class (`Unresolved compilation problems` / `MissingTypes`) before blaming the code change, then stop JDT and the app and `mvn -q -B clean spring-boot:run`.
- Ensure a Japanese font is installed before launching the browser (`fc-list :lang=ja`). If empty, put Noto Sans CJK JP (`https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/OTF/Japanese/NotoSansCJKjp-Regular.otf`) under `~/.local/share/fonts/`, run `fc-cache -f` and restart Chrome before recording.
- If GUI typing drops Japanese text, paste via the clipboard (`printf '山田' | xclip -selection clipboard`, then Ctrl+V) and confirm it is visible before submitting; as a fallback verify the CDP endpoint (`/json/version`) and send `Input.insertText` over the websocket.

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
- `/accounts` filters are 店番 / 科目 / 状態 / 残高 only (list shows separate 店番 and 口座番号 columns; `displayNo` appears on the detail page). Customer-name search lives on `/customers`.
- Seed facts for filter coverage: branch 001 has five accounts, all ACTIVE; branch 003 + ACTIVE lists 3000001 and 3000002 and excludes frozen 3000003; customer 山田 太郎 (CIF `0000000001`) is found by `山田` or `ﾔﾏﾀﾞ`.
- A UI deposit does not exercise `findForUpdate`; locking coverage needs the transfer path (or `TransferServiceIT`).
- DBコンソール nav is intentionally admin-only.

# Role testing
- Test both hidden controls and server authorization. Use browser-origin POSTs carrying the logged-in user's own valid CSRF token; otherwise 403 may only prove CSRF enforcement.
- Auditor: account/customer reads allowed; account/customer mutations and transfers denied.
- Teller: deposits, withdrawals, transfers and customer operations allowed; status changes denied.
- Admin: status and batch exports allowed.
- API routes support HTTP Basic. When testing that independently of UI login, omit browser credentials and supply the Basic header; never extract session cookies.
- Account JSON exposes Japanese labels; `/usagi/manage/health` redirects to the login page when unauthenticated (web chain) and returns UP once logged in.
- Check shared footer Japanese rendering on several page types, not just main content.
