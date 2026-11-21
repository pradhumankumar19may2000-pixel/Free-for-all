package com.example.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.domain.model.VisualizerFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

class AudioVisualizerEngine(private val context: Context) {

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = 0
    private var batterySaver: Boolean = false

    private val _frame = MutableStateFlow(VisualizerFrame())
    val frame: StateFlow<VisualizerFrame> = _frame.asStateFlow()

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun setBatterySaver(enabled: Boolean) {
        batterySaver = enabled
        if (currentSessionId > 0) {
            attach(currentSessionId, isPlaying = true)
        }
    }

    fun attach(audioSessionId: Int, isPlaying: Boolean) {
        val permGranted = hasRecordPermission()
        if (!permGranted || audioSessionId <= 0 || !isPlaying) {
            releaseVisualizerOnly()
            _frame.value = VisualizerFrame(
                isLiveAudioCapture = false,
                permissionGranted = permGranted
            )
            return
        }

        if (visualizer != null && currentSessionId == audioSessionId) {
            runCatching { visualizer?.enabled = true }
            return
        }

        releaseVisualizerOnly()
        currentSessionId = audioSessionId

        try {
            val captureRate = if (batterySaver) Visualizer.getMaxCaptureRate() / 4 else Visualizer.getMaxCaptureRate() / 2
            val sizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = (256).coerceIn(sizeRange[0], sizeRange[1])

            val viz = Visualizer(audioSessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform == null || waveform.isEmpty()) return
                            val step = (waveform.size / 32).coerceAtLeast(1)
                            val samples = (0 until 32).map { i ->
                                val idx = (i * step).coerceAtMost(waveform.lastIndex)
                                ((waveform[idx].toInt() and 0xFF) - 128) / 128f
                            }
                            val amp = samples.map { kotlin.math.abs(it) }.average().toFloat().coerceIn(0f, 1f)
                            _frame.value = _frame.value.copy(
                                isLiveAudioCapture = true,
                                permissionGranted = true,
                                waveform = samples,
                                overallAmplitude = amp
                            )
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 8) return
                            val bins = fft.size / 2
                            val magnitudes = FloatArray(bins)
                            magnitudes[0] = kotlin.math.abs(fft[0].toInt()).toFloat() / 128f
                            for (k in 1 until bins) {
                                val re = fft[2 * k].toFloat()
                                val im = fft[2 * k + 1].toFloat()
                                magnitudes[k] = (hypot(re, im) / 128f).coerceIn(0f, 1f)
                            }

                            val barCount = 24
                            val bars = (0 until barCount).map { b ->
                                val idx = (b * bins / barCount).coerceIn(0, bins - 1)
                                magnitudes[idx]
                            }
                            val bass = magnitudes.take((bins * 0.15).toInt().coerceAtLeast(2)).average().toFloat().coerceIn(0f, 1f)
                            val mid = magnitudes.drop((bins * 0.15).toInt()).take((bins * 0.35).toInt().coerceAtLeast(2)).average().toFloat().coerceIn(0f, 1f)
                            val treble = magnitudes.takeLast((bins * 0.30).toInt().coerceAtLeast(2)).average().toFloat().coerceIn(0f, 1f)

                            _frame.value = _frame.value.copy(
                                isLiveAudioCapture = true,
                                permissionGranted = true,
                                spectrumBars = bars,
                                bassEnergy = bass,
                                midEnergy = mid,
                                trebleEnergy = treble
                            )
                        }
                    },
                    captureRate,
                    true,
                    true
                )
                enabled = true
            }
            visualizer = viz
        } catch (e: Exception) {
            Log.w("AudioVisualizerEngine", "Visualizer capture unavailable on this device: ${e.message}")
            releaseVisualizerOnly()
            _frame.value = VisualizerFrame(
                isLiveAudioCapture = false,
                permissionGranted = permGranted
            )
        }
    }

    fun releaseVisualizerOnly() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        currentSessionId = 0
    }
}
