@file:Suppress("VariableNeverRead")

package com.example.financeapplication.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.financeapplication.datastores.PlannedItemsDataStore
import com.example.financeapplication.datastores.UserPreferencesDataStore
import com.example.financeapplication.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onScreenDetermined: (startDestination: String) -> Unit
) {
    val context = LocalContext.current
    var showAnimation by remember { mutableStateOf(false) }
    
    val isFirstRunState by UserPreferencesDataStore.isFirstRun(context)
        .collectAsState(initial = null)

    val alpha by animateFloatAsState(
        targetValue = if (showAnimation) 0f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "SplashFadeOut"
    )

    LaunchedEffect(isFirstRunState) {
        if (isFirstRunState != null) {
            PlannedItemsDataStore.migrateIfNeeded(context)
            delay(800)
            showAnimation = true

            delay(500)
            val startDestination = if (isFirstRunState == true) "welcome_screen" else "main_screen"
            onScreenDetermined(startDestination)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = Color.Unspecified
        )
    }
}
