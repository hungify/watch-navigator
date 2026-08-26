package com.watchnavigator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchnavigator.R
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTravelMode by remember(preferences) { mutableStateOf(preferences.defaultTravelMode) }
    var drivingThresholdText by remember(preferences) {
        mutableStateOf(preferences.drivingVibrationThresholdMeters.toString())
    }
    var walkingThresholdText by remember(preferences) {
        mutableStateOf(preferences.walkingVibrationThresholdMeters.toString())
    }

    var drivingError by remember { mutableStateOf<String?>(null) }
    var walkingError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is SaveSettingsResult.Success -> {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_saved))
                viewModel.clearSaveResult()
            }
            is SaveSettingsResult.Invalid -> {
                val message = context.getString(result.messageResId, result.min, result.max)
                snackbarHostState.showSnackbar(message)
                viewModel.clearSaveResult()
            }
            null -> Unit
        }
    }

    fun validateAndSave() {
        focusManager.clearFocus()
        drivingError = null
        walkingError = null

        val drivingInput = drivingThresholdText.trim().toIntOrNull()
        if (drivingInput == null) {
            drivingError = context.getString(R.string.error_invalid_number)
            return
        }
        if (drivingInput !in UserPreferences.MIN_THRESHOLD_METERS..UserPreferences.MAX_THRESHOLD_METERS) {
            drivingError =
                context.getString(
                    R.string.error_driving_threshold_range,
                    UserPreferences.MIN_THRESHOLD_METERS,
                    UserPreferences.MAX_THRESHOLD_METERS
                )
            return
        }

        val walkingInput = walkingThresholdText.trim().toIntOrNull()
        if (walkingInput == null) {
            walkingError = context.getString(R.string.error_invalid_number)
            return
        }
        if (walkingInput !in UserPreferences.MIN_THRESHOLD_METERS..UserPreferences.MAX_THRESHOLD_METERS) {
            walkingError =
                context.getString(
                    R.string.error_walking_threshold_range,
                    UserPreferences.MIN_THRESHOLD_METERS,
                    UserPreferences.MAX_THRESHOLD_METERS
                )
            return
        }

        viewModel.save(
            defaultTravelMode = selectedTravelMode,
            drivingThresholdMeters = drivingInput,
            walkingThresholdMeters = walkingInput
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Default Travel Mode Section
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
                        text = stringResource(R.string.label_default_travel_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedTravelMode == TravelMode.DRIVING,
                            onClick = { selectedTravelMode = TravelMode.DRIVING },
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
                            selected = selectedTravelMode == TravelMode.WALKING,
                            onClick = { selectedTravelMode = TravelMode.WALKING },
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

            // 2. Vibration Warning Distances Section
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.label_vibration_thresholds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Distance before turn where watch vibrates to notify upcoming maneuver.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Driving warning distance
                    OutlinedTextField(
                        value = drivingThresholdText,
                        onValueChange = {
                            drivingThresholdText = it
                            drivingError = null
                        },
                        label = { Text(stringResource(R.string.hint_driving_threshold)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            Text(
                                text = "meters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        isError = drivingError != null,
                        supportingText = {
                            if (drivingError != null) {
                                Text(
                                    text = drivingError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Allowed range: 10 – 1000 meters")
                            }
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Walking warning distance
                    OutlinedTextField(
                        value = walkingThresholdText,
                        onValueChange = {
                            walkingThresholdText = it
                            walkingError = null
                        },
                        label = { Text(stringResource(R.string.hint_walking_threshold)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            Text(
                                text = "meters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        isError = walkingError != null,
                        supportingText = {
                            if (walkingError != null) {
                                Text(
                                    text = walkingError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Allowed range: 10 – 1000 meters")
                            }
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = { validateAndSave() }
                            ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Save Button
            Button(
                onClick = { validateAndSave() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_save_settings),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
