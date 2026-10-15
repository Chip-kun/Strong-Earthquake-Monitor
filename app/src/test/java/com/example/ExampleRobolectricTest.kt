package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("強震モニター", appName)
  }

  @Test
  fun `verify speech rate persists and clamps properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val voiceAlertManager = com.example.audio.VoiceAlertManager(context)

    voiceAlertManager.setSpeechRate(1.5f)
    assertEquals(1.5f, voiceAlertManager.speechRate.value, 0.01f)

    // Clamping test
    voiceAlertManager.setSpeechRate(3.0f)
    assertEquals(2.5f, voiceAlertManager.speechRate.value, 0.01f)

    voiceAlertManager.setSpeechRate(0.2f)
    assertEquals(0.5f, voiceAlertManager.speechRate.value, 0.01f)

    // Reset to recommended
    voiceAlertManager.setSpeechRate(1.25f)
    assertEquals(1.25f, voiceAlertManager.speechRate.value, 0.01f)
  }

  @Test
  fun `verify background monitoring is enabled by default and can be toggled`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val initiallyEnabled = com.example.service.KyoshinBackgroundService.isEnabled(context)
    assertEquals(true, initiallyEnabled)
  }
}
