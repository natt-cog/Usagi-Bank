# うさぎ銀行 勘定系オンライン (Usagi Bank Core Banking)

A deliberately *legacy* Japanese core-banking application — **Spring Boot 1.5.22 / Java 8 / JSP + jQuery / JUnit 4 / Oracle-flavoured SQL on H2 / a GnuCOBOL end-of-day batch / a Jenkinsfile** — built as a realistic target for migration & modernization demos.

Everything a mid-2010s bank system would have is here on purpose: `javax.*`, `WebSecurityConfigurerAdapter`, Spring Data `findOne()`, Hibernate `Criteria`, Joda-Time, Ehcache 2, JAXB, `BigDecimal.ROUND_DOWN`, MS932 fixed-width host files, Zengin account-type codes, and a 1998 COBOL program that must keep producing the same file to the sen (銭).

## 業務機能 (Business features)

| 画面 / API | 内容 |
|---|---|
| ダッシュボード | 営業日, 店別残高, 残高上位顧客, 有効口座一覧 |
| 顧客 (CIF) | 検索・登録 (CIF 自動採番)・本人確認 (KYC) ステータス更新・口座開設 |
| 口座 | 店番 / 口座番号 / 科目 (普通 1・当座 2・貯蓄 4・定期 9) / 状態 (有効・凍結・休眠・解約) / 入出金 / 取引明細 (ページング) |
| 振込 | 同一店内 0 円, 他店宛 3 万円未満 110 円・3 万円以上 220 円, 1 日あたり振込限度額 (既定 100 万円), 楽観ロック + 口座ロック順序制御 |
| 明細 XML | JAXB による取引明細 XML (`/accounts/{店番}/{口座番号}/statement.xml`) |
| 利息 | 日次利息積数 (年利 ÷ 365, 銭未満切捨て), 半期利払い (2 月・8 月) |
| ホストファイル連携 | `ACCOUNTS.DAT` 出力 / `ACCRUED.DAT` 取込 (固定長 52 桁, MS932, H/D/T レコード, トレーラ件数照合) |
| 監査 | 全 API 呼出しの監査ログ (`AUDIT` ロガー), 操作者記録 |

REST API (`/usagi/api/**`, HTTP Basic): `GET /api/accounts`, `GET /api/accounts/{branch}/{no}`, `GET .../transactions`, `GET .../statement.xml`, `POST /api/transfers`, `GET /api/customers`, admin-only `GET /api/batch/accounts-file`, `POST /api/batch/accrued-file`, `POST /api/batch/eod`.

## 起動 (Running locally)

Requirements: JDK 8, Maven 3.x (GnuCOBOL `cobc` only for the COBOL batch).

```bash
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn spring-boot:run
# → http://localhost:8080/usagi/
```

Demo users (in-memory, `SecurityConfig`):

| user | password | roles |
|---|---|---|
| `teller`  | `teller123` | TELLER (入出金・振込) |
| `admin`   | `admin123`  | ADMIN + TELLER (口座状態変更, バッチ API) |
| `auditor` | `audit123`  | AUDITOR (照会のみ) |

Other endpoints: H2 console `/usagi/h2-console` (JDBC URL `jdbc:h2:mem:usagi`, user `USAGI` / `usagi`), Actuator `/usagi/manage/health`, `/usagi/manage/info`, `/usagi/manage/metrics`.

```bash
# API examples
curl -u teller:teller123 http://localhost:8080/usagi/api/accounts/001/1000001
curl -u teller:teller123 -H 'Content-Type: application/json' -d '{"fromBranchCode":"001","fromAccountNo":"1000001","toBranchCode":"002","toAccountNo":"2000001","amount":50000,"description":"家賃"}' http://localhost:8080/usagi/api/transfers
curl -u admin:admin123 -o ACCOUNTS.DAT http://localhost:8080/usagi/api/batch/accounts-file
```

## テスト (Tests)

```bash
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn test        # 39 tests (JUnit 4, SpringRunner, H2 Oracle mode)
cd batch/cobol && ./run.sh                                     # compile & run UBEOD001, diff against expected/ACCRUED.DAT
```

| Test | What it pins down |
|---|---|
| `InterestServiceTest` | 日次利息計算 (ROUND_DOWN, 当座は無利息) |
| `TransferServiceIT` | 手数料, 限度額, 残高不足, 凍結口座, 同一口座振込 |
| `BatchFileServiceIT` | 固定長レイアウト golden file (`src/test/resources/golden/ACCOUNTS.DAT`), MS932, トレーラ照合 |
| `StatementServiceIT` | JAXB 明細 XML |
| `BankingApiIT` | 認証・ロール別認可・エラーコード (`UB-xxxx`)・日本語ラベル |
| `WebSecurityIT` | 画面フォーム POST のサーバ側ロール制御 (監査ロールは照会のみ), CSRF |
| `CobolParityTest` | Java の利息計算と COBOL `UBEOD001` の出力が 1 銭単位で一致すること |

Golden files are regenerated with `mvn test -Dgolden.argLine=-Dgolden.update=true` (see `BatchFileServiceIT`).

## リポジトリ構成

```
pom.xml                          Spring Boot 1.5.22 parent, WAR packaging, Java 1.8
Jenkinsfile                      Jenkins 2.x declarative pipeline (WebSphere staging deploy)
src/main/java/jp/usagi/bank/
  config/                        SecurityConfig (2× WebSecurityConfigurerAdapter), WebMvcConfig, AuditLogFilter
  domain/                        JPA entities: Branch, Customer, Account, Transaction + enums (Zengin codes)
  repository/                    Spring Data JPA + Hibernate Criteria (AccountRepositoryImpl) + Oracle ROWNUM native SQL
  service/                       AccountService, TransferService, InterestService, BatchFileService, StatementService, ...
  web/                           JSP controllers (顧客 / 口座 / 振込 / ダッシュボード)
  api/                           REST controllers + DTOs + ApiExceptionHandler (UB-xxxx error codes)
  batch/                         EndOfDayJob (@Scheduled 23:30 JST)
  xml/                           JAXB statement model
src/main/resources/db/migration  Flyway 4: V1 schema (Oracle DDL dialect), V2 seed data (5 支店, 10 顧客, 15 口座)
src/main/webapp/WEB-INF/jsp      JSP/JSTL pages (Japanese UI) + jQuery 1.12.4
batch/cobol/UBEOD001.cbl         COBOL 日次利息積数バッチ (GnuCOBOL), data/, expected/, run.sh
```

## Modernization tracks (demo scenarios)

The codebase is seeded with hotspots for each track. All of them can be validated by the existing test suite — the tests are the parity contract.

### 1. Spring Boot 1.5 / Java 8 → Spring Boot 3 / Java 21 (primary)

| Hotspot | Where |
|---|---|
| `javax.persistence`, `javax.validation`, `javax.servlet`, `javax.xml.bind` → `jakarta.*` / JAXB removed from JDK | every entity, filters, `xml/` |
| `WebSecurityConfigurerAdapter`, `antMatchers`, `NoOpPasswordEncoder`-style plaintext in-memory users, `security.*` properties | `config/SecurityConfig.java`, `application.properties` |
| Spring Data `findOne(id)`, `new PageRequest(...)` | `service/*`, `repository/*` |
| Hibernate 5.0 `Criteria` / `Restrictions` / `Order` (removed in Hibernate 6) | `repository/AccountRepositoryImpl.java` |
| `org.hibernate.validator.constraints.NotBlank` → `jakarta.validation.constraints.NotBlank` | `domain/Customer.java` |
| Flyway 4 (`flyway.*` → `spring.flyway.*`, `baseline-on-migrate`), H2 1.4 → 2.x, Ehcache 2 → JCache/Caffeine | `pom.xml`, `application.properties`, `ehcache.xml` |
| `server.context-path`, `spring.http.encoding.*`, `endpoints.*`/`management.security.enabled` → new Actuator model | `application.properties` |
| `SpringBootServletInitializer` WAR + JSP (JSP still works in Boot 3 with Tomcat but only as WAR) | `UsagiBankApplication.java`, `pom.xml` |
| JUnit 4 (`@RunWith(SpringRunner)`, `@Test(expected=...)`, Hamcrest 1.3) → JUnit 5 | `src/test` |
| Joda-Time → `java.time`, `BigDecimal.ROUND_DOWN` → `RoundingMode`, `new BigDecimal("...")` idioms | `service/*`, `BusinessDateService` |
| `Charset.forName("MS932")`, `IOUtils`, Java 8 stream-less loops → records, `var`, text blocks, `switch` expressions | `service/BatchFileService.java` |

### 2. COBOL batch → Java service
`batch/cobol/UBEOD001.cbl` is the host-side twin of `InterestService` + `BatchFileService.importAccrued`. `CobolParityTest` and `expected/ACCRUED.DAT` define the contract for replacing the COBOL step with `POST /api/batch/eod`.

### 3. JSP/jQuery → React/TypeScript
Ten JSP pages behind `web/*Controller` with the REST API already exposing the same operations. `static/js/usagi.js` contains the jQuery behaviours (full-width digit normalisation, account lookup, table sort) to re-implement.

### 4. Oracle → PostgreSQL
`V1__create_schema.sql` uses `VARCHAR2`, `NUMBER(p,s)`, `SYSDATE`, sequences; repositories use `NVL`, `ROWNUM`, `FROM DUAL`; `application.properties` documents the production Oracle URL. H2 runs in `MODE=Oracle`.

### 5. Jenkins → GitHub Actions
`Jenkinsfile` (declarative, WebSphere `wsadmin` deploy, Nexus, SonarQube 5.6) → workflows with Maven/Temurin matrix + GnuCOBOL step.

## 注意
This is a demo system with fictitious data. Credentials are in-memory demo values only.
