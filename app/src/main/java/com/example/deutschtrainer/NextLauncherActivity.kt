package com.example.deutschtrainer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Keep the separate Next icon and open the learner screen directly. */
class NextLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TrainerActivity::class.java))
        finish()
    }
}
