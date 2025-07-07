package com.climus.climeet.presentation.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.climus.climeet.databinding.ActivitySplashBinding
import com.climus.climeet.presentation.base.BaseActivity
import com.climus.climeet.presentation.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity<ActivitySplashBinding>(ActivitySplashBinding::inflate) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            delay(SPLASH_DURATION_MILLIS)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }

    private companion object {
        const val SPLASH_DURATION_MILLIS = 2_000L
    }
}
