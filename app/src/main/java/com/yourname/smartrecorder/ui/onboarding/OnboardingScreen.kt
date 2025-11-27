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
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
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
    val pagerState = rememberPagerState(pageCount = { 5 }) // Thêm page xin quyền recording
    val currentPage = pagerState.currentPage
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermissionManager = NotificationPermissionManager()
    
    // Record audio permission launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Record audio permission result -> granted: $isGranted")
        
        if (isGranted) {
            // Permission granted → Navigate to next page
            coroutineScope.launch {
                pagerState.animateScrollToPage(3) // Navigate to notification page
            }
        } else {
            // Permission denied → Show warning and navigate to next page anyway
            AppLogger.w(TAG_VIEWMODEL, "[OnboardingScreen] Record audio permission denied - app may not work properly")
            coroutineScope.launch {
                pagerState.animateScrollToPage(3)
            }
        }
    }
    
    // Track if permission dialog was actually shown (user interaction)
    var notificationPermissionDialogShown by remember { mutableStateOf(false) }
    
    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Notification permission result -> granted: $isGranted")
        
        // Update ViewModel state if granted (sync with SettingsStore)
        if (isGranted) {
            viewModel.enableNotifications()
            // Permission granted → Navigate to next page
            coroutineScope.launch {
                pagerState.animateScrollToPage(4) // Navigate to CTA page
            }
        } else {
            // Permission denied → Check if dialog was actually shown
            val activity = context as? android.app.Activity
            val shouldShowRationale = if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                false
            }
            
            AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Permission denied - shouldShowRationale: $shouldShowRationale, dialogShown: $notificationPermissionDialogShown")
            
            // Only navigate if dialog was actually shown (user interaction)
            // If dialog wasn't shown (permanently denied), don't auto-navigate - let user click Next
            if (shouldShowRationale || notificationPermissionDialogShown) {
                // Dialog was shown, user denied → Navigate (user made a choice)
                AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Dialog was shown, user denied - navigating to next page")
                coroutineScope.launch {
                    pagerState.animateScrollToPage(4)
                }
            } else {
                // Dialog might not have been shown (permanently denied)
                AppLogger.w(TAG_VIEWMODEL, "[OnboardingScreen] Permission permanently denied - dialog may not have been shown, waiting for user to click Next")
                // Don't navigate automatically - let user click Next button
            }
        }
        
        // Reset flag
        notificationPermissionDialogShown = false
    }
    
    // Auto-launch notification permission dialog when Page 3 is shown
    LaunchedEffect(currentPage) {
        if (currentPage == 3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check if permission already granted
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                // Không cần check gì, cứ gọi system permission dialog lên
                AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Page 3 shown - auto-launching notification permission dialog")
                notificationPermissionDialogShown = true // Mark that we're attempting to show dialog
                kotlinx.coroutines.delay(300) // Small delay để page animation hoàn tất
                try {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Notification permission dialog launched automatically")
                } catch (e: Exception) {
                    AppLogger.e(TAG_VIEWMODEL, "[OnboardingScreen] Error auto-launching notification permission dialog", e)
                    notificationPermissionDialogShown = false // Reset if launch failed
                }
            } else {
                // Permission already granted → Navigate to next page
                AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Notification permission already granted, navigating to page 4")
                viewModel.enableNotifications() // Sync with SettingsStore
                coroutineScope.launch {
                    pagerState.animateScrollToPage(4)
                }
            }
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
                repeat(5) { index ->
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
            if (currentPage == 4) {
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
                            when (currentPage) {
                                2 -> {
                                    // Page 2: Request record audio permission
                                    // OnboardingScreen chỉ hiện khi cài lại app/data bị xóa
                                    // → Luôn cần hiện System Permission nếu màn hình này hiện
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Page 2 (Record Audio) Next clicked -> hasPermission: $hasPermission")
                                    
                                    if (!hasPermission) {
                                        // Permission not granted → Always request permission dialog
                                        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Requesting record audio permission dialog")
                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        // Permission already granted → Navigate to next page
                                        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Record audio permission already granted, navigating to page 3")
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(3)
                                        }
                                    }
                                }
                                3 -> {
                                    // Page 3: Request notification permission
                                    // OnboardingScreen chỉ hiện khi cài lại app/data bị xóa
                                    // → Luôn cần hiện System Permission nếu màn hình này hiện
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        // Check permission directly using ContextCompat (more reliable)
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        
                                        AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Page 3 (Notification) Next clicked -> hasPermission: $hasPermission")
                                        
                                        if (!hasPermission) {
                                            // Permission not granted → Always request permission dialog
                                            AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Requesting notification permission dialog")
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            // Permission already granted → Update SettingsStore and navigate
                                            AppLogger.d(TAG_VIEWMODEL, "[OnboardingScreen] Notification permission already granted, updating SettingsStore and navigating to page 4")
                                            viewModel.enableNotifications() // Sync with SettingsStore
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(4)
                                            }
                                        }
                                    } else {
                                        // Android < 13: Notifications enabled by default
                                        // Still update SettingsStore for consistency
                                        viewModel.enableNotifications()
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(4)
                                        }
                                    }
                                }
                                else -> {
                                    // Other pages - just navigate
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentPage + 1)
                                    }
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
            // Page 2: Request record audio permission
            Text(
                text = "Microphone Permission",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Smart Recorder needs microphone access to record audio. This is essential for the app to function properly.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ Without this permission, the app cannot record audio and will not work properly.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        3 -> {
            // Page 3: Request notification permission
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
        4 -> {
            // Page 4: CTA
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

