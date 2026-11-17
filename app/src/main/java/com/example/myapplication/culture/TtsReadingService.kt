package com.example.myapplication.culture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import java.util.Locale

class TtsReadingService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentText: String = ""
    private var currentTitle: String = "Lettura in corso"

    companion object {
        const val ACTION_START = "ACTION_START_READING"
        const val ACTION_STOP = "ACTION_STOP_READING"
        const val EXTRA_TEXT = "EXTRA_TEXT"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val CHANNEL_ID = "culture_reading_channel"
        const val NOTIFICATION_ID = 42
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ITALIAN
            isTtsReady = true
            if (currentText.isNotBlank()) {
                speakText(currentText)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentText = intent.getStringExtra(EXTRA_TEXT) ?: ""
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Lettura Audiolibro"
                startForeground(NOTIFICATION_ID, buildNotification(currentTitle, "Lettura in background attiva"))
                if (isTtsReady && currentText.isNotBlank()) {
                    speakText(currentText)
                }
            }
            ACTION_STOP -> {
                stopReading()
            }
        }
        return START_NOT_STICKY
    }

    private fun speakText(text: String) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                stopReading()
            }
            override fun onError(utteranceId: String?) {
                stopReading()
            }
        })

        // Suddivide in blocchi per pronunciare testi lunghi
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reading_utt_id")
    }

    private fun stopReading() {
        tts?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lettura Cultura Background",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, status: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(status)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "Ferma",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, TtsReadingService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
