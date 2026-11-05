# Layer Recipes

## Contents
1. ViewModel
2. UseCase
3. Repository
4. Mapper / pure functions (parameterized)
5. Retrofit + MockWebServer
6. Room DAO

## 1. ViewModel

Test: initial state, each state transition, one-shot events, SavedStateHandle restore, process-death-relevant args.

```kotlin
class TransferViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeTransferRepository()
    private fun createSut(args: Map<String, Any?> = mapOf("accountId" to "ACC-1")) =
        TransferViewModel(SavedStateHandle(args), repo)

    @Test
    fun `should show the insufficient funds error when submitting given the amount exceeds the balance`() = runTest {
        // Given
        repo.balance = BigDecimal("100")
        val sut = createSut()
        sut.onAmountChanged("500")

        // When
        sut.onSubmit()

        // Then
        assertThat(sut.uiState.value.error).isEqualTo(TransferError.InsufficientFunds)
        assertThat(repo.submittedTransfers).isEmpty()
    }
}
```
Tip: a `createSut()` factory lets each test customize constructor args without duplicating setup.

## 2. UseCase

Pure orchestration → usually fakes only, no dispatcher rule needed unless it launches coroutines.
Cover: success, each failure type mapped correctly, validation rules, combination logic.

## 3. Repository

Scenarios to cover for offline-first repositories:
- cache empty + network success → saves to DB, emits data
- cache present + network failure → emits cached data + error signal
- network returns 401 → maps to `DomainError.Unauthorized` (not raw `HttpException`)
- IOException → `DomainError.Network`

```kotlin
@Test
fun `should return a Network error when refreshing given a network timeout`() = runTest {
    coEvery { api.accounts() } throws SocketTimeoutException()

    val result = sut.refresh()

    assertThat(result.exceptionOrNull()).isInstanceOf(DomainError.Network::class.java)
}
```

## 4. Mapper / pure functions

Use parameterized tests to cover many inputs without duplication.

JUnit 4:
```kotlin
@RunWith(Parameterized::class)
class IbanFormatterTest(private val input: String, private val expected: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} -> {1}")
        fun data() = listOf(
            arrayOf("MA64011519000001205000534921", "MA64 0115 1900 0001 2050 0053 4921"),
            arrayOf("", ""),
        )
    }
    @Test fun `should group the iban in blocks of four when formatting it`() = assertThat(IbanFormatter.format(input)).isEqualTo(expected)
}
```
JUnit 5: `@ParameterizedTest @CsvSource(...)`. Kotest: `withData(...)`.

## 5. Retrofit + MockWebServer

Tests the real JSON contract, interceptors, and error parsing.

```kotlin
class AccountApiTest {
    private val server = MockWebServer()
    private lateinit var api: AccountApi

    @Before fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build().create()
    }
    @After fun tearDown() = server.shutdown()

    @Test
    fun `should parse the list and call the accounts path when getting accounts given a 200 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(readResource("accounts_200.json")))

        val result = api.getAccounts()

        assertThat(result).hasSize(2)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/v1/accounts")
        assertThat(request.getHeader("Authorization")).startsWith("Bearer ")
    }
}
```
Store JSON fixtures in `src/test/resources/`.

## 6. Room DAO

Prefer `src/androidTest` with an in-memory DB for fidelity; Robolectric works for JVM speed.

```kotlin
@RunWith(AndroidJUnit4::class)
class AccountDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: AccountDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.accountDao()
    }
    @After fun tearDown() = db.close()

    @Test
    fun shouldEmitTheAccount_whenObserving_givenItWasInserted() = runTest {
        dao.insert(anAccountEntity())
        dao.observeAll().test {
            assertThat(awaitItem()).containsExactly(anAccountEntity())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```
Also test migrations with `MigrationTestHelper` in androidTest.
