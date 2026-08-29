package com.watchnavigator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchnavigator.R
import com.watchnavigator.engine.NavigationProgress
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.PlaceSuggestion
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.WatchConnectionState
import com.watchnavigator.util.DistanceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onStartNavigationService: () -> Unit,
    onStopNavigationService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.queryFlow.collectAsStateWithLifecycle()
    val suggestionsState by viewModel.suggestionsState.collectAsStateWithLifecycle()
    val selectedDestination by viewModel.selectedDestination.collectAsStateWithLifecycle()
    val travelMode by viewModel.travelMode.collectAsStateWithLifecycle()
    val routeState by viewModel.routeState.collectAsStateWithLifecycle()
    val isNavigating by viewModel.isNavigating.collectAsStateWithLifecycle()
    val navigationProgress by viewModel.navigationProgress.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val watchConnectionState by viewModel.watchConnectionState.collectAsStateWithLifecycle()
    val watchSendError by viewModel.watchSendError.collectAsStateWithLifecycle()
    val isRecalculating by viewModel.isRecalculating.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val stepsListState = rememberLazyListState()

    // Auto-scroll steps list as current navigation step advances
    LaunchedEffect(currentStepIndex, isNavigating) {
        if (isNavigating && currentStepIndex >= 0) {
            stepsListState.animateScrollToItem(currentStepIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
            )
        }
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Navigation & Watch Status Card
            StatusCard(
                isNavigating = isNavigating,
                isRecalculating = isRecalculating,
                routeState = routeState,
                navigationProgress = navigationProgress,
                watchConnectionState = watchConnectionState,
                watchSendError = watchSendError,
                onConnectWatch = { viewModel.connectOrRequestWatchPermission() }
            )

            // 2. Destination Search & Autocomplete
            DestinationSearchSection(
                query = query,
                onQueryChange = { viewModel.onQueryChanged(it) },
                suggestionsState = suggestionsState,
                selectedDestination = selectedDestination,
                onSuggestionSelect = { suggestion ->
                    focusManager.clearFocus()
                    viewModel.selectSuggestion(suggestion)
                },
                onClear = {
                    viewModel.clearSelectedDestination()
                },
                enabled = !isNavigating
            )

            // 3. Travel Mode Selector
            TravelModeSection(
                selectedMode = travelMode,
                enabled = !isNavigating,
                onModeSelect = { viewModel.setTravelMode(it) }
            )

            // 4. Route Loading Indicator
            if (routeState is RouteUiState.Loading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.calculating_route),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 5. Route Error Card with Retry
            if (routeState is RouteUiState.Error) {
                val errorState = routeState as RouteUiState.Error
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.error_route_calculation_failed),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = { viewModel.retryRouteCalculation() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            // 6. Route Overview & Turn Steps Card (When route is ready)
            if (routeState is RouteUiState.Success) {
                val route = (routeState as RouteUiState.Success).route
                RouteOverviewCard(
                    route = route,
                    selectedDestination = selectedDestination
                )

                if (route.steps.isNotEmpty()) {
                    TurnStepsCard(
                        steps = route.steps,
                        currentStepIndex = if (isNavigating) currentStepIndex else -1,
                        navigationProgress = navigationProgress,
                        isNavigating = isNavigating,
                        listState = stepsListState
                    )
                }
            }

            // 7. Navigation Start / Stop Action Button
            val canNavigate = routeState is RouteUiState.Success || isNavigating
            Button(
                onClick = {
                    if (isNavigating) {
                        viewModel.stopNavigation()
                        onStopNavigationService()
                    } else {
                        viewModel.startNavigation()
                        if (viewModel.isNavigating.value) {
                            onStartNavigationService()
                        }
                    }
                },
                enabled = canNavigate,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (isNavigating) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                    )
            ) {
                Icon(
                    imageVector = if (isNavigating) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        if (isNavigating) {
                            stringResource(R.string.btn_stop_navigation)
                        } else {
                            stringResource(R.string.btn_start_navigation)
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -----------------------------------------------------------------------------
// Component: Status & Watch Connection Banner
// -----------------------------------------------------------------------------
@Composable
private fun StatusCard(
    isNavigating: Boolean,
    isRecalculating: Boolean,
    routeState: RouteUiState,
    navigationProgress: NavigationProgress?,
    watchConnectionState: WatchConnectionState,
    watchSendError: String?,
    onConnectWatch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Navigation Status
            Text(
                text = stringResource(R.string.label_navigation_status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val statusText =
                when {
                    isNavigating && isRecalculating -> stringResource(R.string.status_recalculating)
                    isNavigating && navigationProgress != null -> {
                        if (navigationProgress.isArrived) {
                            stringResource(R.string.notification_arrived)
                        } else {
                            val dist =
                                DistanceFormatter.formatDistance(
                                    navigationProgress.remainingDistanceToNextTurnMeters
                                )
                            stringResource(
                                R.string.notification_turn_instruction,
                                dist,
                                navigationProgress.currentStep.instruction
                            )
                        }
                    }
                    isNavigating -> stringResource(R.string.status_navigating)
                    routeState is RouteUiState.Loading -> stringResource(R.string.calculating_route)
                    routeState is RouteUiState.Success -> stringResource(R.string.status_route_ready)
                    else -> stringResource(R.string.status_ready)
                }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isRecalculating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

            // Watch Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val indicatorColor =
                        when {
                            watchSendError != null -> MaterialTheme.colorScheme.error
                            watchConnectionState is WatchConnectionState.Connected -> Color(0xFF4CAF50)
                            watchConnectionState is WatchConnectionState.Connecting -> MaterialTheme.colorScheme.primary
                            watchConnectionState is WatchConnectionState.Unauthorized -> Color(0xFFFFA000)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }

                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                    )

                    Column {
                        Text(
                            text = stringResource(R.string.label_watch_status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val watchText =
                            when {
                                watchSendError != null -> stringResource(R.string.watch_status_error, watchSendError)
                                watchConnectionState is WatchConnectionState.Connected -> {
                                    stringResource(R.string.watch_status_connected, watchConnectionState.deviceName)
                                }
                                watchConnectionState is WatchConnectionState.Connecting -> {
                                    stringResource(R.string.watch_status_connecting)
                                }
                                watchConnectionState is WatchConnectionState.Unauthorized -> {
                                    val msg = watchConnectionState.message
                                    if (msg.isBlank()) {
                                        stringResource(R.string.watch_status_unauthorized)
                                    } else {
                                        "${stringResource(R.string.watch_status_unauthorized)} ($msg)"
                                    }
                                }
                                watchConnectionState is WatchConnectionState.Error -> {
                                    stringResource(R.string.watch_status_error, watchConnectionState.message)
                                }
                                watchConnectionState is WatchConnectionState.Disconnected -> {
                                    val reason = watchConnectionState.reason
                                    if (reason.isNullOrBlank()) {
                                        stringResource(R.string.watch_status_disconnected)
                                    } else {
                                        "${stringResource(R.string.watch_status_disconnected)} ($reason)"
                                    }
                                }
                                else -> stringResource(R.string.watch_status_disconnected)
                            }

                        Text(
                            text = watchText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val buttonText =
                    when {
                        watchSendError != null ||
                            watchConnectionState is WatchConnectionState.Unauthorized ||
                            watchConnectionState is WatchConnectionState.Error -> {
                            stringResource(R.string.retry)
                        }
                        watchConnectionState is WatchConnectionState.Connecting -> stringResource(R.string.btn_watch_connecting)
                        else -> stringResource(R.string.btn_connect_watch)
                    }
                FilledTonalButton(
                    onClick = onConnectWatch,
                    enabled = watchConnectionState !is WatchConnectionState.Connecting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component: Destination Search & Autocomplete
// -----------------------------------------------------------------------------
@Composable
private fun DestinationSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestionsState: SuggestionsUiState,
    selectedDestination: PlaceSuggestion?,
    onSuggestionSelect: (PlaceSuggestion) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.title_destination),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            placeholder = {
                Text(
                    text = stringResource(R.string.hint_search_destination),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Autocomplete suggestions list
        AnimatedVisibility(
            visible = suggestionsState is SuggestionsUiState.Success || suggestionsState is SuggestionsUiState.Loading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (suggestionsState is SuggestionsUiState.Loading) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.searching_places),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (suggestionsState is SuggestionsUiState.Success) {
                    val suggestions = (suggestionsState as SuggestionsUiState.Success).suggestions
                    if (suggestions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_suggestions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            suggestions.forEachIndexed { index, suggestion ->
                                SuggestionItem(
                                    suggestion = suggestion,
                                    onClick = { onSuggestionSelect(suggestion) }
                                )
                                if (index < suggestions.size - 1) {
                                    HorizontalDivider(
                                        color = DividerDefaults.color.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: PlaceSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.primaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (suggestion.secondaryText.isNotBlank()) {
                Text(
                    text = suggestion.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Component: Travel Mode Selector
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelModeSection(
    selectedMode: TravelMode,
    enabled: Boolean,
    onModeSelect: (TravelMode) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.label_travel_mode),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = selectedMode == TravelMode.DRIVING,
                onClick = { onModeSelect(TravelMode.DRIVING) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.mode_driving),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            SegmentedButton(
                selected = selectedMode == TravelMode.WALKING,
                onClick = { onModeSelect(TravelMode.WALKING) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.mode_walking),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component: Route Overview Card
// -----------------------------------------------------------------------------
@Composable
private fun RouteOverviewCard(
    route: NavRoute,
    selectedDestination: PlaceSuggestion?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.title_route_overview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            val destinationName =
                route.destinationAddress.ifBlank {
                    selectedDestination?.primaryText ?: stringResource(R.string.title_destination)
                }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = destinationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = DistanceFormatter.formatDuration(route.totalDurationSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = DistanceFormatter.formatDistance(route.totalDistanceMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component: Turn-by-Turn Steps List Card
// -----------------------------------------------------------------------------
@Composable
private fun TurnStepsCard(
    steps: List<NavStep>,
    currentStepIndex: Int,
    navigationProgress: NavigationProgress?,
    isNavigating: Boolean,
    listState: LazyListState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${stringResource(R.string.title_turn_steps)} (${steps.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(steps) { index, step ->
                    val isActive = isNavigating && index == currentStepIndex
                    TurnStepItem(
                        step = step,
                        index = index,
                        isActive = isActive,
                        remainingDistanceMeters =
                            if (isActive && navigationProgress != null) {
                                navigationProgress.remainingDistanceToNextTurnMeters
                            } else {
                                null
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun TurnStepItem(
    step: NavStep,
    index: Int,
    isActive: Boolean,
    remainingDistanceMeters: Int?
) {
    val backgroundColor =
        if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val contentColor =
        if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Maneuver Icon
            Icon(
                imageVector = getManeuverIcon(step.maneuver),
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            // Step Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
                if (step.streetName.isNotBlank() && step.streetName != step.instruction) {
                    Text(
                        text = step.streetName,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Distance
            val distanceDisplay =
                if (isActive && remainingDistanceMeters != null) {
                    DistanceFormatter.formatDistance(remainingDistanceMeters)
                } else {
                    DistanceFormatter.formatDistance(step.distanceMeters)
                }

            Text(
                text = distanceDisplay,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getManeuverIcon(maneuver: ManeuverType): ImageVector =
    when (maneuver) {
        ManeuverType.TURN_LEFT, ManeuverType.TURN_SLIGHT_LEFT, ManeuverType.TURN_SHARP_LEFT, ManeuverType.UTURN_LEFT,
        ManeuverType.FORK_LEFT, ManeuverType.RAMP_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        ManeuverType.TURN_RIGHT, ManeuverType.TURN_SLIGHT_RIGHT, ManeuverType.TURN_SHARP_RIGHT, ManeuverType.UTURN_RIGHT,
        ManeuverType.FORK_RIGHT, ManeuverType.RAMP_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        ManeuverType.STRAIGHT, ManeuverType.MERGE, ManeuverType.DEPART -> Icons.Default.Straight
        ManeuverType.ARRIVE -> Icons.Default.Flag
        ManeuverType.ROUNDABOUT_LEFT, ManeuverType.ROUNDABOUT_RIGHT -> Icons.Default.Refresh
        ManeuverType.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
    }
