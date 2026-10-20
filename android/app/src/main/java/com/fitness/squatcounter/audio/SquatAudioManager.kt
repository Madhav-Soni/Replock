package com.fitness.squatcounter.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import com.fitness.squatcounter.R
import java.util.Locale

/**
 * Manages audio feedback for squats:
 * 1. Speaks rep count "1, 2, 3..." using TextToSpeech upon valid rep.
 * 2. Plays a short bundled beep (200ms sine wave) using SoundPool when tracking is lost.
 */
class SquatAudioManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val soundPool: SoundPool
    private var beepSoundId: Int = 0
    private var isSoundPoolLoaded = false

    init {
        tts = TextToSpeech(context.applicationContext, this)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == beepSoundId) {
                isSoundPoolLoaded = true
            }
        }

        try {
            beepSoundId = soundPool.load(context, R.raw.beep, 1)
        } catch (_: Exception) {
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    /**
     * Speaks the rep number (e.g. "1", "2", "3").
     */
    fun speakRep(repNumber: Int) {
        if (!isTtsReady) return
        tts?.speak(
            repNumber.toString(),
            TextToSpeech.QUEUE_FLUSH,
            null,
            "rep_$repNumber"
        )
    }

    /**
     * Plays the short bundled beep for "person not detected / tracking lost".
     */
    fun playTrackingLostBeep() {
        if (isSoundPoolLoaded && beepSoundId != 0) {
            soundPool.play(beepSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        soundPool.release()
    }
}
