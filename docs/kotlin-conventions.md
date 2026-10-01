# Kotlin conversion conventions (Java → Kotlin, 1:1 port)

Rules every conversion ticket on the `kotlin-migration` branch followed. The port is complete: the build is Kotlin-only (`src/main/kotlin` / `src/test/kotlin`, no `src/main/java`), so the "mixed codebase" notes below describe the intermediate state — the interop rules themselves still apply because JSP EL, Spring, JPA, JAXB and the libraries are Java callers. Scope of this migration is **language only**: Java 8 / Spring Boot 1.5.22 / JUnit 4 / Joda-Time / `javax.*` all stay, behaviour must stay byte-identical (the 39 tests and the golden files are the contract). The toolchain is Kotlin 1.9.25 with the `spring` and `jpa` compiler plugins and `-Xjsr305=strict` (see `pom.xml`).

## File layout and PR shape

- Kotlin sources live in `src/main/kotlin` / `src/test/kotlin`, **same package** (`jp.usagi.bank.…`), one class per file, same file name as the Java class (`Account.java` → `Account.kt`).
- Delete the Java file in the same PR. One package (e.g. `domain`) or one test class per PR, so each PR is reviewable and `mvn -B test` stays green at every merge.
- Surefire's includes in `pom.xml` are `**/*Test.class` / `**/*IT.class` (class-file patterns, so Kotlin-compiled tests are discovered); converted tests keep their `*Test` / `*IT` names.
- During the migration Kotlin compiled first and `javac` then saw the Kotlin classes, so remaining Java callers kept working as long as the JVM signatures were preserved; JSPs (`${account.displayNo}`) still rely on those signatures (see *Interop*).

## Entities (`domain/`)

Regular `class`, never `data class` (JPA identity, lazy proxies, `equals`/`hashCode` on mutable state). `var` properties mirror the Java fields one to one; the `jpa` plugin supplies the no-arg constructor and the `spring`/`jpa` plugins open the classes — do not write `open` by hand.

Nullability follows the column:

| Java field in `Account.java` | Kotlin |
|---|---|
| `@Id @GeneratedValue private Long id;` | `var id: Long? = null` (null until persisted) |
| `@Column(nullable = false) private String branchCode;` (no default) | `lateinit var branchCode: String` |
| `private AccountStatus status = AccountStatus.ACTIVE;` | `var status: AccountStatus = AccountStatus.ACTIVE` |
| `private BigDecimal balance = BigDecimal.ZERO;` | `var balance: BigDecimal = BigDecimal.ZERO` |
| `@Column(name = "LAST_TXN_ON") private Date lastTransactionOn;` (nullable column) | `var lastTransactionOn: Date? = null` |
| `@ManyToOne(optional = false) private Customer customer;` | `lateinit var customer: Customer` |
| `@Version private long version;` | `@Version var version: Long = 0` — keep as-is, never touch it |

`lateinit` is only for non-null columns that have no sensible default (it preserves the Java "set after `new Account()`" pattern used by tests); it is **not** a way to dodge `?`. Derived getters stay as computed properties with the same JVM name: `val displayNo: String get() = "$branchCode-$accountNo"` and `val isActive: Boolean get() = status == AccountStatus.ACTIVE` (keep the `is` prefix so Java's `account.isActive()` still resolves). Annotations go on the property without a use-site target: JPA/JAXB annotations only target `FIELD`/`METHOD`, so Kotlin puts them on the backing field, matching `@XmlAccessorType(XmlAccessType.FIELD)` in `xml/Statement.java` and JPA field access in the entities.

## DTOs and API payloads (`api/dto/`, `xml/`)

`data class` with `val` properties and `companion object { @JvmStatic fun from(a: Account): AccountDto }` replacing the static factories (`AccountDto.from`, `TransferResponse.from`). `jackson-module-kotlin` is on the classpath and auto-registered by Spring 4.3, but two rules keep the JSON contract identical:

- **Request bodies** (`TransferRequest`): every property is nullable with `= null`, and validation annotations use the `field:` target: `@field:NotNull @field:Pattern(regexp = "\\d{3}", message = "店番は3桁の数字で入力してください") val fromBranchCode: String? = null`. A non-null property without a default would make Jackson reject a missing field before Bean Validation runs, turning today's `UB-0001` 400 into a `UB-9999` 500 (`BankingApiIT` posts transfers without `description`). Without `field:` the annotation lands on the constructor parameter and is never validated.
- **Response bodies** (`AccountDto`, `TransferResponse`, `ApiError`): non-null `val` where Java always sets the value, `?` only where Java can produce `null` (`TransferResult.fee`). Jackson annotations also use `field:`/`get:` targets: `@field:JsonFormat(shape = STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Tokyo") val openedOn: Date`. Mutable collections initialised in Java (`ApiError.details`) stay `val details: MutableList<String> = ArrayList()` — they are filled through the getter by `ApiExceptionHandler`.

JAXB models (`Statement`, `StatementEntry`) are *not* data classes: JAXB needs a no-arg constructor and mutable fields, so convert them like entities (`var`, `@XmlJavaTypeAdapter(DateAdapter::class)`, `const val NS = "http://usagi.jp/bank/statement/1.0"` in the companion so `Statement.NS` stays a Java constant usable in annotations).

## Services, controllers, exceptions

- Constructor injection via the primary constructor: `@Service class BatchFileService(private val accountRepository: AccountRepository, private val businessDateService: BusinessDateService)`. `@Value` stays on the parameter and the `$` must be escaped: `@Value("\${usagi.transfer.daily-limit:1000000}") private val dailyLimit: BigDecimal`.
- `@Transactional`, `@CacheEvict`, `@Scheduled`, `@PreAuthorize` keep their exact attributes (`@Transactional(isolation = Isolation.READ_COMMITTED)`, `readOnly = true`). The `spring` plugin opens annotated classes so proxies work; no manual `open`.
- Checked exceptions that Java callers or tests still see get `@Throws`: `@Throws(IOException::class) fun exportAccounts(out: OutputStream): Int` (called from `BatchApiController`, which declares `throws IOException`).
- Array-valued annotation attributes use brackets: `@RequestMapping(value = ["/api/transfers"], produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])`, `@ExceptionHandler(AccountNotFoundException::class, CustomerNotFoundException::class)`.
- Loggers and constants move to the companion: `companion object { private val log = LoggerFactory.getLogger(InterestService::class.java) }`.
- Static helpers become companion functions annotated `@JvmStatic` (`InterestService.dailyInterest`, `BatchFileService.formatRecord`/`numeric`/`statusFlag` are all called statically from tests).
- `switch` on an enum (`BatchFileService.statusFlag`) becomes an exhaustive `when` expression; drop the unreachable `default` throw only when `when` is exhaustive.
- Exceptions: `class BankingException(val errorCode: String, message: String) : RuntimeException(message)`; subclasses keep their `UB-xxxx` codes and message text verbatim; `serialVersionUID` becomes `private const val serialVersionUID = 1L` in the companion (compiled to the same static field).

## Interop while the codebase is mixed

Java → Kotlin callers (remaining Java classes, JSP EL, JUnit 4 tests) must see the same JVM API:

- Property `balance` compiles to `getBalance()`/`setBalance()` automatically. Boolean properties keep their Java name (`isActive`).
- Constants: `const val RECORD_LENGTH = 52` (primitives/strings) and `@JvmField val HOST_CHARSET: Charset = Charset.forName("MS932")` (everything else) in a `companion object`, so `BatchFileService.HOST_CHARSET` / `RECORD_LENGTH` stay static field reads in `BatchFileServiceIT` and `CobolParityTest`.
- Companion functions that Java calls get `@JvmStatic`; functions with default parameters that replace Java overloads get `@JvmOverloads`.
- Do **not** use `internal` for anything Java still calls — the compiler mangles `internal` member names. Package-private Java members used by tests (`static String formatRecord(Account)`, `static void validateAmount(BigDecimal)`) become public `@JvmStatic` members for now.
- Nested static classes (`TransferService.TransferResult`, `BatchFileService.ImportResult`) become nested (non-`inner`) classes, so `BatchFileService.ImportResult` import paths stay valid.

Kotlin → Java callers (Spring Data, JPA, Joda, commons): Spring 4.3 has no nullability annotations, so every Java return value is a platform type. Declare the type explicitly at the call site and decide nullability from the Java contract: `val account: Account? = accountRepository.findByBranchCodeAndAccountNo(branch, accountNo)` (returns `null` when absent, which `importAccrued` relies on to count `skipped`), `val accounts: List<Account> = accountRepository.findAll()`.

## Types that do not change

- `BigDecimal` everywhere money or rates appear; `java.util.Date` on entities/DTOs/JAXB; Joda-Time (`LocalDate`, `DateTimeFormatter`) in services. No `java.time`, no `kotlin.math`, no `Int`/`Long` amounts.
- Keep the arithmetic literal: `InterestService.dailyInterest`'s `.divide(HUNDRED, 10, BigDecimal.ROUND_DOWN).divide(DAYS_IN_YEAR, 2, BigDecimal.ROUND_DOWN)` and `BatchFileService.numeric`'s `setScale(scale, BigDecimal.ROUND_DOWN)` are copied as-is (`@Suppress("DEPRECATION")` if the int rounding constants warn). Never use Kotlin's `/` operator on `BigDecimal` — it silently applies `RoundingMode.HALF_EVEN` and changes the sen (銭) rounding that `CobolParityTest` pins down. `+`/`-`/`compareTo` (`amount < FEE_THRESHOLD`) are safe; `==` on `BigDecimal` is `equals` (scale-sensitive) exactly as in Java.
- Fixed-width parsing keeps its offsets (`line.substring(35, 52)`, `line[0]`) and `Charset.forName("MS932")`.
- Japanese Javadoc becomes KDoc with the same text (`/** 預金口座. … */`); the layout tables in `BatchFileService` and `TransferService` stay in the class KDoc.

## How to convert and what to review

1. Convert with IntelliJ J2K (or by hand for small classes), move the result to `src/main/kotlin`, delete the Java file.
2. Hand-review every file for: `!!` (replace with an explicit `?`/`lateinit` decision or a `requireNotNull` with a message), untyped platform-type `val`s, `lateinit` on anything that has a default or a nullable column, `internal` on Java-visible members, missing `@field:` on DTO annotations, missing `@JvmStatic`/`@JvmField`/`@Throws`, unescaped `$` in `@Value`/regex strings, `data class` on an entity, J2K's `Int` for Java `long` (`version`, `declaredCount` must stay `Long`).
3. Run `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -B test` — 39 tests, golden files untouched — and smoke-check the affected screens/APIs as described in `.agents/skills/usagi-testing/SKILL.md`.
4. Run `mvn ktlint:format` before committing; `mvn -B verify` runs `ktlint:check` (ktlint `intellij_idea` style from `.editorconfig`) and fails the build on any violation.
