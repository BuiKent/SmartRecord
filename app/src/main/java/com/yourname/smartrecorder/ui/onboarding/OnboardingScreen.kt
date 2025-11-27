package com.yourname.smartrecorder.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.yourname.smartrecorder.core.permissions.NotificationPermissionManager
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onNavigateToPremium: () -> Unit = {},
    onNavigateToRate: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val currentPage = pagerState.currentPage
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Check notification permission state from system
    var hasNotificationPermission by remember { mutableStateOf(false) }
    val notificationPermissionManager = NotificationPermissionManager()
    
    // Initialize permission state
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check actual system permission state
            hasNotificationPermission = notificationPermissionManager.areNotificationsEnabled(context)
            AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Initial notification permission state: $hasNotificationPermission")
        } else {
            // Android < 13 doesn't need notification permission (enabled by default)
            hasNotificationPermission = true
        }
    }
    
    // Permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Notification permission result -> granted: $isGranted")
        hasNotificationPermission = isGranted
        
        // Update ViewModel state if granted
        if (isGranted) {
            viewModel.enableNotifications()
        }
        
        // Refresh system state to ensure sync
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Small delay to allow system to update
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(150)
                val actualState = notificationPermissionManager.areNotificationsEnabled(context)
                hasNotificationPermission = actualState
                AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Refreshed permission state after result: $actualState")
            }
        }
        
        // Auto-navigate to next page
        coroutineScope.launch {
            pagerState.animateScrollToPage(3)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Welcome to\nSmart Recorder",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OnboardingPageContent(page = page)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                repeat(4) { index ->
                    val alpha by animateFloatAsState(
                        targetValue = if (index == currentPage) 1f else 0.3f,
                        animationSpec = tween(300),
                        label = "indicator"
                    )
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 10.dp else 6.dp)
                            .alpha(alpha)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            )
                    )
                }
            }
            
            // Navigation buttons
            if (currentPage == 3) {
                // Last page - CTA buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.completeOnboarding()
                            onNavigateToPremium()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Star, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upgrade to Premium")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            viewModel.completeOnboarding()
                            onComplete()
                            onNavigateToRate()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rate App")
                    }
                    
                    Button(
                        onClick = {
                            viewModel.completeOnboarding()
                            onComplete()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get Started")
                    }
                }
            } else {
                // Other pages - Back/Next buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentPage - 1)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }
                    }
                    Button(
                        onClick = {
                            if (currentPage == 2) {
                                // Request notification permission
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    // Check current system state before requesting
                                    val currentSystemState = notificationPermissionManager.areNotificationsEnabled(context)
                                    AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Page 2 Next clicked -> currentSystemState: $currentSystemState, hasNotificationPermission: $hasNotificationPermission")
                                    
                                    if (!currentSystemState && !hasNotificationPermission) {
                                        // Permission not granted → Request permission dialog
                                        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Requesting notification permission")
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        // Permission already granted or Android < 13 → Navigate to next page
                                        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Permission already granted or Android < 13, navigating to page 3")
                                        hasNotificationPermission = true
                                        if (currentSystemState) {
                                            viewModel.enableNotifications()
                                        }
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(3)
                                        }
                                    }
                                } else {
                                    // Android < 13: Notifications enabled by default
                                    hasNotificationPermission = true
                                    viewModel.enableNotifications()
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(3)
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: Int) {
    when (page) {
        0 -> {
            // Page 0: Giới thiệu app
            Text(
                text = "Smart Recorder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Record, transcribe, and study with AI-powered features. Transform your audio into text, create flashcards, and enhance your learning experience.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        1 -> {
            // Page 1: Tính năng chính
            Text(
                text = "Key Features",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureItem(
                    title = "Real-time Transcription",
                    description = "Live speech-to-text with Google ASR"
                )
                FeatureItem(
                    title = "Offline Whisper",
                    description = "Powerful offline transcription"
                )
                FeatureItem(
                    title = "Smart Study Tools",
                    description = "Create flashcards from your recordings"
                )
            }
        }
        2 -> {
            // Page 2: Request notification permission
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Allow notifications to see recording status in the notification bar. This helps you monitor your recordings even when the app is in the background.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notifications are enabled by default on this Android version.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        3 -> {
            // Page 3: CTA
            Text(
                text = "Ready to Start?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Upgrade to Premium for advanced features, rate the app to support development, or get started with the free version.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeatureItem(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

