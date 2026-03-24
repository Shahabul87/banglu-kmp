package com.banglu.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BangluSetupScreen()
        }
    }
}

@Composable
fun BangluSetupScreen() {
    val context = LocalContext.current

    var isEnabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var isDefault by remember { mutableStateOf(isKeyboardDefault(context)) }
    var currentStep by remember { mutableIntStateOf(0) }
    var testText by remember { mutableStateOf("") }

    // Poll keyboard status every 2 seconds so steps update after user returns from settings
    val currentContext = rememberUpdatedState(context)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000)
            val ctx = currentContext.value
            isEnabled = isKeyboardEnabled(ctx)
            isDefault = isKeyboardDefault(ctx)
            currentStep = when {
                !isEnabled -> 0
                !isDefault -> 1
                else -> 2
            }
        }
    }

    val brandBlue = Color(0xFF3D5AFE)

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // App title
                Text(
                    text = "\u09AC\u09BE\u0982\u09B2\u09C1",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = brandBlue
                )
                Text(
                    text = "Bengali Phonetic Keyboard",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Step 1: Enable keyboard
                SetupStep(
                    stepNumber = 1,
                    title = "Enable Banglu Keyboard",
                    subtitle = "Turn on Banglu in keyboard settings",
                    isCompleted = isEnabled,
                    isCurrent = currentStep == 0,
                    buttonText = if (isEnabled) "Enabled" else "Enable",
                    brandColor = brandBlue,
                    onClick = {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Set as default
                SetupStep(
                    stepNumber = 2,
                    title = "Set as Default Keyboard",
                    subtitle = "Choose Banglu as your keyboard",
                    isCompleted = isDefault,
                    isCurrent = currentStep == 1,
                    buttonText = if (isDefault) "Default" else "Set Default",
                    brandColor = brandBlue,
                    onClick = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 3: Try it / Settings
                SetupStep(
                    stepNumber = 3,
                    title = "Try Typing Bengali!",
                    subtitle = "Type 'ami' to see \u0986\u09AE\u09BF",
                    isCompleted = false,
                    isCurrent = currentStep == 2,
                    buttonText = "Open Settings",
                    brandColor = brandBlue,
                    onClick = {
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Test typing area
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = { Text("Try typing here...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Type in English, get Bengali instantly",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SetupStep(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
    buttonText: String,
    brandColor: Color,
    onClick: () -> Unit
) {
    val stepColor = when {
        isCompleted -> Color(0xFF4CAF50)
        isCurrent -> brandColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isCurrent) 2.dp else 1.dp,
            color = if (isCurrent) brandColor else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (isCurrent) brandColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step number circle
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = stepColor
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isCompleted) "\u2713" else stepNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action button
            Button(
                onClick = onClick,
                enabled = !isCompleted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) Color(0xFF4CAF50) else brandColor,
                    disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.7f),
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonText, fontSize = 13.sp)
            }
        }
    }
}

fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

fun isKeyboardDefault(context: Context): Boolean {
    val currentIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return currentIme?.startsWith(context.packageName) == true
}
