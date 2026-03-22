package com.banglu.keyboard

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Placeholder settings activity for the Banglu IME.
 *
 * Referenced in xml/method.xml as the settingsActivity for the input method.
 * Will be expanded with actual preference controls in a future iteration.
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        layout.addView(
            TextView(this).apply {
                text = getString(R.string.settings_title)
                textSize = 24f
            }
        )

        layout.addView(
            TextView(this).apply {
                text = "Settings coming soon..."
                textSize = 16f
            }
        )

        setContentView(layout)
    }
}
