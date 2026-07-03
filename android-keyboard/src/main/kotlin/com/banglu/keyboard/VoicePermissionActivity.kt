package com.banglu.keyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class VoicePermissionActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            markVoiceDisclosureAccepted()
        }
        setResult(if (granted) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        val hasPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        setBangluContent {
            VoicePermissionScreen(
                onAllow = {
                    if (hasPermission) {
                        markVoiceDisclosureAccepted()
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onCancel = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            )
        }
    }

    private fun markVoiceDisclosureAccepted() {
        remoteBangluPrefs(this)
            .edit()
            .putBoolean("voice_disclosure_accepted", true)
            .apply()
        // Tell the IME so dictation starts right away — the user already
        // expressed intent by tapping the mic; don't make them tap it twice.
        sendBroadcast(
            android.content.Intent(BangluIMEService.ACTION_VOICE_DISCLOSURE_ACCEPTED)
                .setPackage(packageName)
        )
    }
}

@Composable
private fun VoicePermissionScreen(onAllow: () -> Unit, onCancel: () -> Unit) {
    val accepted = remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99080D16))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF6)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "ভয়েস টাইপিং অনুমতি",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF25283D)
                )
                Text(
                    text = "Banglu voice typing আপনার ডিভাইসের speech recognizer ব্যবহার করে। আপনি মাইক চালু করলে অডিও Google বা আপনার ফোনের speech provider-এ transcription-এর জন্য পাঠানো হতে পারে। Banglu অডিও সংরক্ষণ করে না।",
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = Color(0xFF5B6278)
                )
                Text(
                    text = if (accepted.value) "আমি বুঝেছি" else "চালু করতে Allow চাপুন।",
                    fontSize = 14.sp,
                    color = Color(0xFF9A4D3D),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("বাতিল")
                    }
                    Button(
                        onClick = {
                            accepted.value = true
                            onAllow()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA34F3F))
                    ) {
                        Text("Allow")
                    }
                }
            }
        }
    }
}
