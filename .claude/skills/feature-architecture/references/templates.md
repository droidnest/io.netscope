# Templates: a History screen

One section per step of the workflow in `SKILL.md`, for a fictional `Wallet` feature and its History
screen. Replace `Wallet` / `History` with the real feature and concept, and every role name
(`DataResult`, `mapData`, `ApiCaller`, `BaseViewModel`, `handleJobFailure`, `AppTheme`,
`AppLoadingDialog`, `BaseAnalyticsHelper`, `AnalyticsEvent`…) with the project's class from
`project.md`. The profile's reference feature has the same steps in real, compiling code: read it when a
template leaves a detail open.

## 1. DTO + endpoint (data)

```kotlin
// data/remote/models/WalletHistoryListResponseApi.kt
internal data class WalletHistoryListResponseApi(
    @SerializedName("operations") val walletHistoryList: List<WalletHistoryEntryApi?>?
)

// data/remote/models/WalletHistoryEntryApi.kt: a JSON object inside a response is its own
// top-level `…Api` class in its own file, never an inner class
internal data class WalletHistoryEntryApi(
    @SerializedName("refOperation") val refOperation: Long?,
    @SerializedName("label") val label: String?,
    @SerializedName("amount") val amount: BigDecimal?,
    // …
)

// data/remote/WalletUrls.kt: the feature's URLs, never added to the shared URL object
internal object WalletUrls {
    const val HISTORY = "wallet/v1/history"
}

// data/remote/WalletService.kt
@GET(WalletUrls.HISTORY)
// + the project's demo-file annotation, when it has a demo mode (profile)
suspend fun getWalletHistory(): Response<WalletHistoryListResponseApi>
```

```proguard
# proguard-rules.pro: consumer rules, applied by the app's R8 pass in minified release builds
-keep class <package-root>.wallet.data.remote.models.** { *; }
```

## 2. Domain model + repository interface (domain)

```kotlin
// domain/model/WalletHistoryEntry.kt (trimmed): nullability is resolved in data
internal data class WalletHistoryEntry(
    val reference: String,
    val label: String,
    val executedAt: Date?,
    val amount: BigDecimal,
    val status: WalletPaymentStatus,
    // …
)

// domain/repository/WalletRepository.kt (trimmed)
internal interface WalletRepository {
    suspend fun fetchHistory(): DataResult<List<WalletHistoryEntry>>
}
```

## 3. Repository implementation (data)

```kotlin
// data/mapper/WalletHistoryMappers.kt (trimmed)
internal fun WalletHistoryListResponseApi?.toDomain(): List<WalletHistoryEntry> =
    this?.walletHistoryList.orEmpty().filterNotNull().map { it.toDomain() }

// data/repository/WalletRepositoryImpl.kt (trimmed): data sources only, never a use case or a ViewModel
internal class WalletRepositoryImpl @Inject constructor(
    private val service: WalletService,
    private val apiCaller: ApiCaller
) : WalletRepository {

    override suspend fun fetchHistory(): DataResult<List<WalletHistoryEntry>> =
        apiCaller.call { service.getWalletHistory() }
            .mapData { it.toDomain() }
}
```

A blocking source (a Room DAO that is not `suspend`, a file) switches thread here, with an injected
`@IoDispatcher ioDispatcher: CoroutineDispatcher` and `withContext(context = ioDispatcher) { … }`; never
`Dispatchers.IO`.

## 4. Wiring (di)

With Hilt:

```kotlin
// di/WalletModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal object WalletModule {
    @Provides
    fun provideWalletService(retrofit: Retrofit): WalletService =   // the project's Retrofit binding (profile)
        retrofit.create(WalletService::class.java)                   // Java method: no argument name
}

// di/WalletBindsModule.kt (trimmed)
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WalletBindsModule {
    @Binds
    abstract fun bindWalletRepository(impl: WalletRepositoryImpl): WalletRepository
}
```

Without a DI framework, the same constructors are called by the project's factory (profile):

```kotlin
// ui/history/WalletHistoryViewModelFactory.kt: builds the graph the Hilt modules would bind
internal class WalletHistoryViewModelFactory(private val repository: WalletRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WalletHistoryViewModel(
        getHistorySectionsUseCase = WalletGetHistorySectionsUseCase(repository = repository),
    ) as T
}
```

## 5. Use case (domain), grouped by area

```kotlin
// domain/usecase/history/WalletGetHistorySectionsUseCase.kt (trimmed)
internal class WalletGetHistorySectionsUseCase @Inject constructor(
    private val repository: WalletRepository
) {
    suspend operator fun invoke(): DataResult<List<WalletHistorySection>> =
        repository.fetchHistory().mapData { entries -> entries.orEmpty().toSections() }
}
```

A use case that only passes a repository call through is still written: the ViewModel never injects the
repository.

## 6. Analytics (domain interface + data implementation)

```kotlin
// domain/analytics/WalletAnalyticsHelper.kt: what happened, pure Kotlin
internal interface WalletAnalyticsHelper {
    fun logHistoryOpened()
    fun logHistoryItemClicked()
}

// data/analytics/WalletAnalyticsHelperImpl.kt: how it is sent, through the project's base helper
internal class WalletAnalyticsHelperImpl @Inject constructor() : BaseAnalyticsHelper(), WalletAnalyticsHelper {

    override fun logHistoryOpened() {
        logOpenScreen(screenName = SCREEN_WALLET_HISTORY)
    }

    override fun logHistoryItemClicked() {
        logEvent(event = AnalyticsEvent.WALLET_HISTORY_ITEM_CLICK)   // new event, added to the project's event type
    }

    private companion object {
        const val SCREEN_WALLET_HISTORY = "WALLET_HISTORY"
    }
}

// di/WalletBindsModule.kt
@Binds
abstract fun bindWalletAnalyticsHelper(impl: WalletAnalyticsHelperImpl): WalletAnalyticsHelper
```

## 7. Intent + screen UiState + UI models (ui)

```kotlin
// ui/history/WalletHistoryUiIntent.kt
internal sealed interface WalletHistoryUiIntent : UiIntent {
    data object Started : WalletHistoryUiIntent
    data class ItemClicked(val item: WalletHistoryItemUi) : WalletHistoryUiIntent
    data object DetailsOpened : WalletHistoryUiIntent
    data object ErrorShown : WalletHistoryUiIntent
}

// ui/history/WalletHistoryUiState.kt: the T of UiState<T>
internal sealed class WalletHistoryUiState {

    abstract val sections: List<WalletHistorySectionUi>

    val isEmpty: Boolean get() = sections.none { it.items.isNotEmpty() }

    data class Content(override val sections: List<WalletHistorySectionUi>) : WalletHistoryUiState()

    /** [openedItem] is the row whose details must open, until the screen acknowledges it. */
    data class Navigating(
        override val sections: List<WalletHistorySectionUi>,
        val openedItem: WalletHistoryItemUi
    ) : WalletHistoryUiState()
}

// ui/history/mapper/WalletHistoryUiMapper.kt (trimmed): needs a Context, so it is injected
internal class WalletHistoryUiMapper @Inject constructor(private val context: Context) {
    fun toSections(sections: List<WalletHistorySection>): List<WalletHistorySectionUi> = …
}
```

## 8. ViewModel (ui)

```kotlin
// ui/history/WalletHistoryViewModel.kt
@HiltViewModel
internal class WalletHistoryViewModel @Inject constructor(
    private val getHistorySectionsUseCase: WalletGetHistorySectionsUseCase,
    private val historyUiMapper: WalletHistoryUiMapper,
    private val analyticsHelper: WalletAnalyticsHelper
) : BaseViewModel() {

    private val _uiState = MutableStateFlow<UiState<WalletHistoryUiState>>(value = UiState.Idle)
    val uiState: StateFlow<UiState<WalletHistoryUiState>> = _uiState.asStateFlow()

    override fun onIntent(intent: UiIntent) {
        if (intent !is WalletHistoryUiIntent) return
        when (intent) {
            WalletHistoryUiIntent.Started -> {
                if (_uiState.value != UiState.Idle) return
                analyticsHelper.logHistoryOpened()
                loadHistory()
            }

            is WalletHistoryUiIntent.ItemClicked -> {
                analyticsHelper.logHistoryItemClicked()
                updateHistory { WalletHistoryUiState.Navigating(sections = it.sections, openedItem = intent.item) }
            }

            WalletHistoryUiIntent.DetailsOpened -> updateHistory { WalletHistoryUiState.Content(sections = it.sections) }

            WalletHistoryUiIntent.ErrorShown -> _uiState.update { state ->
                if (state is UiState.Error) UiState.Idle else state
            }
        }
    }

    private fun loadHistory() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            runCatching {
                _uiState.value = getHistorySectionsUseCase()
                    .mapData { WalletHistoryUiState.Content(sections = historyUiMapper.toSections(sections = it.orEmpty())) }
                    .toUiState()
            }.onFailure { failure ->
                // The project's job-failure helper: logs, rethrows CancellationException, else calls onFailure
                handleJobFailure(
                    logMessage = "Wallet loadHistory failed",
                    exception = failure,
                    onFailure = { _uiState.value = DataResult.Error(exception = failure as? Exception).toUiState() }
                )
            }
        }
    }

    /** Changes the loaded history; ignored while nothing is loaded. */
    private fun updateHistory(transform: (WalletHistoryUiState) -> WalletHistoryUiState) {
        _uiState.update { state ->
            if (state is UiState.Success) UiState.Success(data = transform(state.data)) else state
        }
    }
}
```

## 9. Compose screen + host (ui)

The screen is a composable: it draws the content, the loader and the popups, and reacts to `Navigating`. The
host only owns the ViewModel and navigates.

```kotlin
// ui/history/list/WalletHistoryScreen.kt (trimmed)
@Composable
internal fun WalletHistoryScreen(
    uiState: UiState<WalletHistoryUiState>,
    onIntent: (WalletHistoryUiIntent) -> Unit,
    onOpenDetails: (WalletHistoryItemUi) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Loading and Error carry no content: the last one shown stays under the loader and the error sheet
    var content by remember { mutableStateOf<WalletHistoryUiState?>(value = null) }
    uiState.dataOrNull()?.let { content = it }

    // The sheet keeps the error until closed; the ViewModel goes back to its content meanwhile
    var shownError by remember { mutableStateOf<UiState.Error?>(value = null) }
    when (uiState) {
        UiState.Loading -> AppLoadingDialog()

        is UiState.Error -> LaunchedEffect(key1 = uiState) {
            shownError = uiState
            onIntent(WalletHistoryUiIntent.ErrorShown)
        }

        is UiState.Success -> when (val state = uiState.data) {
            is WalletHistoryUiState.Navigating -> LaunchedEffect(key1 = state.openedItem) { onOpenDetails(state.openedItem) }
            is WalletHistoryUiState.Content -> Unit
        }

        UiState.Idle -> Unit
    }
    shownError?.let { WalletErrorSheet(error = it, onDismissRequest = { shownError = null }) }

    HistoryContent(
        sections = content?.sections,
        onBackClick = onBackClick,
        onItemClick = { onIntent(WalletHistoryUiIntent.ItemClicked(item = it)) },
        modifier = modifier,
    )
}
```

### Host: a Fragment

```kotlin
// ui/history/list/WalletHistoryFragment.kt
@AndroidEntryPoint
internal class WalletHistoryFragment : BaseFragment() {

    private val viewModel: WalletHistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Sent before the screen composes: the ComposeView composes as soon as it is attached, before onViewCreated
        if (savedInstanceState == null) viewModel.onIntent(intent = WalletHistoryUiIntent.Started)
        return ComposeView(context = requireContext()).apply {
            setViewCompositionStrategy(strategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    WalletHistoryScreen(
                        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
                        onIntent = viewModel::onIntent,
                        onOpenDetails = ::openDetails,
                        onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    // When the base Fragment pads a child of the root with the system bar insets, override its hook so it
    // pads the root: a ComposeView has no child yet when the insets are applied (profile)

    private fun openDetails(item: WalletHistoryItemUi) {
        findNavController().navigate(
            directions = WalletHistoryFragmentDirections
                .actionWalletHistoryFragmentToWalletHistoryDetailsFragment(historyItem = item)
        )   // or the base Fragment's navigation helper (profile)
        viewModel.onIntent(intent = WalletHistoryUiIntent.DetailsOpened)
    }
}
```

No layout file: destination `walletHistoryFragment` in `res/navigation/wallet_navigation.xml`. The module
applies the project's Compose plugin and depends on lifecycle-runtime-compose for
`collectAsStateWithLifecycle` (profile).

### Host: a Navigation Compose route

```kotlin
// ui/navigation/WalletNavGraph.kt (trimmed): the destination plays the Fragment's role
composable(route = ROUTE_HISTORY) {
    val viewModel: WalletHistoryViewModel = viewModel(factory = WalletHistoryViewModelFactory(repository = repository))
    // or hiltViewModel() with Hilt; runs again when the user comes back, so the ViewModel ignores a second Started
    LaunchedEffect(key1 = Unit) { viewModel.onIntent(intent = WalletHistoryUiIntent.Started) }
    WalletHistoryScreen(
        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onOpenDetails = { item ->
            navController.navigate(route = routeHistoryDetails(id = item.id))
            viewModel.onIntent(intent = WalletHistoryUiIntent.DetailsOpened)
        },
        onBackClick = { navController.popBackStack() },
    )
}
```

A confirmation after an action (saved, sent) is a `Saved` / `Done` content state shown with `AppPopupDialog` +
`AppPopup`; its ack intent moves the state to `Navigating`.
