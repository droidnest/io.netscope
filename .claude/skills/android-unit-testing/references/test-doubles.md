# Test Doubles: Fakes, Stubs, Mocks

## Decision guide

| Situation | Use |
|---|---|
| Repository / DataSource with state | Fake |
| Need to emit Flow values over time | Fake backed by `MutableStateFlow` / `MutableSharedFlow` |
| Single stubbed return, no state | MockK `every`/`coEvery` |
| Contract is "X was called" (analytics, navigator, logger) | MockK `verify` or a recording fake |
| Time | `FakeClock` / `TestClock` injected, never `System.currentTimeMillis()` |
| UUID / random | Injected `IdGenerator` |
| Android `Context`, resources | Interface wrapper (`StringProvider`) + fake |

## Fake repository

```kotlin
class FakeAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    var failWith: Throwable? = null
    val refreshCalls = mutableListOf<String>()

    override fun observeAccounts(): Flow<List<Account>> = accounts

    override suspend fun refresh(customerId: String): Result<Unit> {
        refreshCalls += customerId
        failWith?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    fun emit(list: List<Account>) { accounts.value = list }
}
```
Put shared fakes in a `testFixtures` source set or a `:core:testing` module so every feature module reuses them.

## MockK essentials

```kotlin
private val api: AccountApi = mockk()

coEvery { api.balance(any()) } returns BalanceDto(1_000)
coEvery { api.balance("blocked") } throws HttpException(errorResponse(403))
every { analytics.track(any()) } just Runs

val slot = slot<Event>()
every { analytics.track(capture(slot)) } just Runs
assertThat(slot.captured.name).isEqualTo("transfer_confirmed")

coVerify(exactly = 1) { api.balance("123") }
confirmVerified(analytics)          // no unexpected interactions
```

Rules:
- `coEvery`/`coVerify` for `suspend` functions; `every`/`verify` otherwise.
- Avoid `mockkStatic` / `mockkObject` — they signal hidden dependencies. If unavoidable, `unmockkAll()` in `@After`.
- Prefer `returnsMany` over sequences of `answers` for multiple calls.
- Don't mix Mockito and MockK in the same module.

## Test data builders

```kotlin
fun anAccount(
    id: String = "ACC-1",
    type: AccountType = AccountType.CURRENT,
    balance: BigDecimal = BigDecimal("1000.00"),
    currency: String = "MAD",
) = Account(id, type, balance, currency)
```
Name with `a`/`an` prefix; keep in the test fixtures module.
