# Kotlin 変換規約 (Usagi Bank)

Java 8 / Spring Boot 1.5.22 のまま、ソースを段階的に Kotlin へ置き換えるための規約。
以降の変換チケット (s2.x / s3.x) はすべてこの規約に従う。迷ったら「Java 側の呼び出し元・JSP・テストを一切変更せずに済むか」を判断基準にする。

## 1. 前提 (確定事項)

| 項目 | 値 |
|---|---|
| JDK | 8 (`JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64`) |
| Spring Boot | 1.5.22 据え置き (`javax.*` のまま) |
| Kotlin | 1.9.25, `jvmTarget` 1.8 (`pom.xml` の `kotlin.version`) |
| コンパイラプラグイン | `spring` (all-open プリセット), `jpa` (no-arg プリセット), `no-arg` (JAXB の `@XmlRootElement` / `@XmlType`) |
| ビルド | Kotlin 専用。`kotlin-maven-plugin` が `src/main/kotlin` / `src/test/kotlin` をコンパイル (`maven-compiler-plugin` の既定実行は無効化) |
| COBOL バッチ | `batch/cobol` は変換対象外。現行のまま残す |

## 2. 配置とパッケージ

- Kotlin ソースは `src/main/kotlin` (テストは `src/test/kotlin`) に、**元の Java と同一パッケージ・同一ディレクトリ構成**で置く。
  例: `src/main/java/jp/usagi/bank/service/BankingException.java` → `src/main/kotlin/jp/usagi/bank/service/BankingException.kt`
- 1 クラス 1 ファイル、ファイル名はクラス名と一致させる。
- 変換したら**元の Java ファイルは同じ PR で削除**する (`git rm`)。同名クラスの二重定義を残さない。
- パッケージ名・クラス名・public なメソッド/プロパティ名 (= Java から見た getter 名) は変えない。リネームや構造変更は変換と別 PR にする。

## 3. JPA エンティティ

- **`data class` にしない。** 通常の `class` + `var` プロパティで書く。
  (`data class` の `equals`/`hashCode`/`toString`/`copy` は遅延ロード関連・ID 未採番時の挙動を変え、`toString` が関連を辿って N+1 / 循環参照を起こす)
- 引数なしコンストラクタは `jpa` プラグイン (no-arg) が生成する前提。手書きしない。
- Hibernate のプロキシ (`FetchType.LAZY`) のためクラスは非 final が必要。`spring` プリセットは `@Entity` を open にしないため、
  エンティティ変換チケット (s2.1) で `pom.xml` に `all-open` を追加し `javax.persistence.Entity` / `MappedSuperclass` / `Embeddable` を対象にする。
  それまでの間に書くエンティティは `open class` と `open` プロパティを明示する。
- 主キー・`@Version`・DB 側で採番/設定される列は `var id: Long? = null` のように null 許容で初期値を与える。
- `NOT NULL` 列でもエンティティ生成直後や Hibernate のハイドレーション前は null になり得るため、**Java の初期値をそのまま移す** (`var balance: BigDecimal = BigDecimal.ZERO`)。初期値の無い参照型 (`customer` 等) は `var customer: Customer? = null` とする。`lateinit` は使わない (Java/JSP からの未初期化アクセスで例外になるため)。
- アノテーションの付与先: 対象を指定しない Java アノテーションは、プライマリコンストラクタ内のプロパティでは**コンストラクタ引数**に、クラス本体のプロパティでは**フィールド**に付く。
  エンティティのプロパティはクラス本体に `var` で宣言し、JPA (`@Id`, `@Column` 等) はそのまま付けてよい。Bean Validation (`@NotNull`, `@Pattern` 等) は宣言位置に関わらず `@field:` を明示する (DTO のコンストラクタプロパティで検証が効かなくなる事故を防ぐ)。
  ```kotlin
  @field:NotNull
  @field:Pattern(regexp = "\\d{3}")
  @Column(name = "BRANCH_CODE", nullable = false, length = 3)
  var branchCode: String? = null
  ```
- `isActive()` のような派生 getter は `val isActive: Boolean get() = ...` で書き、JPA に永続化されないことを確認する (`@get:Transient` が必要なら付ける)。
- `getDisplayNo()` のような派生値は `val displayNo: String get() = "$branchCode-$accountNo"` (Java からは `getDisplayNo()` のまま)。

## 4. 金額・数値

- 金額・利率・利息は **`BigDecimal` のまま**。`Double` / `Long` に置き換えない。
- 丸めモードは Java の指定をそのまま移す (`BigDecimal.ROUND_DOWN` 等の定数も含め、意味を変えない。`RoundingMode` への置換は別 PR)。
- 比較は `compareTo` (`a.compareTo(b) < 0` または `a < b`)。**`==` は使わない** (Kotlin の `==` は `equals` でスケール差 `1.0 != 1.00` を区別する)。
- 演算子 (`+`, `-`, `*`) は `BigDecimal.add/subtract/multiply` と同等なので使ってよい。**`/` は使わない** (Kotlin の `div` は `RoundingMode.HALF_EVEN` 固定) — 除算は必ず `divide(x, scale, mode)` を明示する。
- 文字列化は `toPlainString()` (固定長ファイル・画面表示・メッセージで指数表記を出さない)。

## 5. null 許容方針 (Java との相互運用)

Java のコードが残っている間は、境界で**プラットフォーム型 (`String!`) を放置しない**。

- Kotlin で宣言する public API の型は必ず `T` か `T?` を明示する。Java の戻り値を受け取るローカル変数・プロパティも型を書く (`val c: Customer? = customerRepository.findByCifNo(cifNo)`)。
- **Java から呼ばれる引数**は、全呼び出し元で非 null が保証できる場合のみ非 null 型にする (Kotlin は public 引数に null チェックを挿入し、null なら `NullPointerException` になる)。
  外部入力 (リクエストパラメータ・DTO・リポジトリ検索キー) 由来で null の可能性が否定できないものは `T?` にする。
- Spring Data の `findOne` / `findByXxx` などの戻り値は `T?` として受ける。
- `!!` は原則禁止。null なら業務例外を投げる (`?: throw AccountNotFoundException(...)`)。
- コレクションは Java に返すなら `List<T>` / `MutableList<T>` を用途で使い分ける。Java 側が `add` するもの (例: `ApiError.getDetails().add(...)`) は `MutableList`。

## 6. static 相当

- Java の `static` メソッドで Java から呼ばれるもの → `companion object` 内の関数に `@JvmStatic` を付ける (Java からは `AccountService.validateAmount(x)` のまま)。
- `public static final` 定数 → プリミティブ/String は `companion object { const val NS = "..." }`、それ以外は `@JvmField val HOST_CHARSET: Charset = ...`。
- `private static final` 定数 → `companion object` 内の `private val` (プリミティブ/String は `private const val`)。
- Java の package-private (`static void validateAmount`, `static final BigDecimal TAX_RATE`) は Kotlin に存在しない。`internal` は JVM 上で名前が mangle され Java から呼べなくなるため、**Java 呼び出し元・テストが残る間は public** にし、KDoc に「パッケージ内部用」と書く。
- ユーティリティのみのクラスでも、Java から `Xxx.method()` で呼ばれている間はトップレベル関数にせず `object` + `@JvmStatic` か `companion object` で残す。
- `serialVersionUID` → `companion object { private const val serialVersionUID = 1L }` (外側クラスの `private static final long` として出力される)。

## 7. ロガー

SLF4J を使い、`companion object` に置く。名前は Java と同じ `log` (監査は `audit`)。

```kotlin
companion object {
    private val log: Logger = LoggerFactory.getLogger(InterestService::class.java)
    private val audit: Logger = LoggerFactory.getLogger("AUDIT")
}
```

- ロガー名 (= クラス FQCN / `"AUDIT"`) を変えない (logback 設定・監査ログ収集が依存)。
- メッセージはプレースホルダ `{}` を使う (`log.info("処理件数={}", count)`)。文字列テンプレートで組み立てない。

## 8. 例外クラス

- 業務例外は `BankingException` (`open class`) を継承する。派生クラスは final (`class`) でよい。
- エラーコードはコンストラクタの `val errorCode: String` (Java/JSP からは `getErrorCode()`)。
- メッセージは文字列テンプレートで組み立てるが、文言・連結順は Java と**一字一句同じ**にする (画面・API・テストが文言を検証する)。
- `serialVersionUID` は §6 の通り companion object の `private const val`。
- `@Throws` はチェック例外を Java から catch させる必要がある場合のみ付ける (業務例外は `RuntimeException` 系なので不要)。

```kotlin
class AccountNotFoundException(
    branchCode: String?,
    accountNo: String?,
) : BankingException("UB-1001", "口座が存在しません: $branchCode-$accountNo") {
    companion object {
        private const val serialVersionUID = 1L
    }
}
```

## 9. Spring コンポーネント

- `@Service` / `@Component` / `@Controller` / `@Configuration` / `@Transactional` / `@Cacheable` のクラスは `spring` プラグインで自動的に open になるので `open` を書かない。
- DI はコンストラクタインジェクション (`class TransferService(private val accountRepository: AccountRepository, ...)`)。フィールドの `@Autowired lateinit var` は使わない。
- `@Value` の `$` はエスケープする: `@Value("\${usagi.transfer.daily-limit:1000000}") private val dailyLimit: BigDecimal`。
- `@Transactional` などのプロキシ対象メソッドを `private` にしない (Java 同様、プロキシが効かない)。

## 10. JAXB / DTO

- JAXB クラスは `no-arg` プラグイン (`@XmlRootElement` / `@XmlType`) で引数なしコンストラクタが生成される前提。`var` プロパティで書き、要素順 (`propOrder`) とアクセス型は Java と同じにする。
- JSON DTO は Jackson (`jackson-module-kotlin` 導入済み)。JSON のプロパティ名・null 時の出力・Bean Validation (`@field:NotNull`) を Java と同じにする。
- DTO は `data class` 可 (エンティティではないため)。ただし JSP から getter で参照されるものは getter 名が変わらないことを確認する。

## 11. スタイル

- インデント 4 スペース、末尾カンマあり、ワイルドカード import 禁止 (ktlint 標準)。
- コメント・KDoc は Java の日本語コメントを移す。
- `Joda-Time` / `java.util.Date` 等の型は変換時に置き換えない (ライブラリ移行は別 PR)。

## 12. 変換チケットのチェックリスト

1. 対象 Java ファイルを `src/main/kotlin` の同一パッケージへ `.kt` として作成し、元の `.java` を `git rm`。
2. Java 側の呼び出し元・JSP・テストを変更せずにコンパイルが通ること (変更が必要なら規約違反を疑う)。
3. `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -B test` が 39 件成功。
4. `(cd batch/cobol && ./run.sh)` が成功 (ホストファイル連携に影響がないことの確認)。
5. PR は `kotlin-migration-v2` 向けに作成し、本文に 3・4 の結果を記載。

## 付録: パイロット変換 (s1.2)

`jp.usagi.bank.service` の例外クラス群 7 ファイルを本規約で変換した。

| Java (削除) | Kotlin |
|---|---|
| `BankingException.java` | `BankingException.kt` (`open class`, `val errorCode`) |
| `AccountNotActiveException.java` | `AccountNotActiveException.kt` |
| `AccountNotFoundException.java` | `AccountNotFoundException.kt` (引数 `String?`: リクエスト由来) |
| `CustomerNotFoundException.java` | `CustomerNotFoundException.kt` (引数 `String?`: リクエスト由来) |
| `InsufficientFundsException.java` | `InsufficientFundsException.kt` |
| `InvalidAmountException.java` | `InvalidAmountException.kt` |
| `TransferLimitExceededException.java` | `TransferLimitExceededException.kt` |

Java 側 (`AccountService`, `TransferService`, `CustomerService`, `ApiExceptionHandler`, `WebExceptionHandler`, 各 Controller, `TransferServiceIT`) は無変更で `new Xxx(...)` / `getErrorCode()` / `getMessage()` を使い続けている。
