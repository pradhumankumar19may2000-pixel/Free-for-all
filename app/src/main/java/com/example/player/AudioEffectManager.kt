package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.example.data.local.EqPresetDao
import com.example.data.local.EqPresetEntity
import com.example.domain.model.EqBand
import com.example.domain.model.EqualizerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AudioEffectManager(private val eqPresetDao: EqPresetDao) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var attachedSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    val builtInPresets = listOf("Flat", "Rock", "Pop", "Jazz", "Classical", "Electronic")

    init {
        scope.launch {
            eqPresetDao.observePresets().collect { custom ->
                _state.update { it.copy(customPresets = custom.map { p -> p.name }) }
            }
        }
    }

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedSessionId) return
        releaseEffects()
        attachedSessionId = audioSessionId

        try {
            val eq = Equalizer(0, audioSessionId).apply { enabled = _state.value.enabled }
            equalizer = eq

            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val numBands = eq.numberOfBands.toInt()
            val bands = (0 until numBands).map { idx ->
                val bandShort = idx.toShort()
                EqBand(
                    index = idx,
                    centerFreqHz = eq.getCenterFreq(bandShort) / 1000,
                    minLevelMb = minMb,
                    maxLevelMb = maxMb,
                    currentLevelMb = eq.getBandLevel(bandShort).toInt()
                )
            }

            bassBoost = runCatching {
                BassBoost(0, audioSessionId).apply {
                    enabled = _state.value.enabled
                    if (strengthSupported) setStrength(_state.value.bassBoostStrength.toShort())
                }
            }.getOrNull()

            virtualizer = runCatching {
                Virtualizer(0, audioSessionId).apply {
                    enabled = _state.value.enabled
                    if (strengthSupported) setStrength(_state.value.virtualizerStrength.toShort())
                }
            }.getOrNull()

            _state.update {
                it.copy(
                    available = true,
                    bands = bands
                )
            }
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Hardware DSP unavailable on this audio session: ${e.message}")
            _state.update {
                it.copy(
                    available = false,
                    bands = defaultFallbackBands()
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled }
        runCatching { virtualizer?.enabled = enabled }
        _state.update { it.copy(enabled = enabled) }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val currentBands = _state.value.bands
        val target = currentBands.getOrNull(bandIndex) ?: return
        val clamped = levelMb.coerceIn(target.minLevelMb, target.maxLevelMb)
        runCatching { equalizer?.setBandLevel(bandIndex.toShort(), clamped.toShort()) }
        _state.update { st ->
            st.copy(
                activePresetName = "Custom",
                bands = st.bands.map { if (it.index == bandIndex) it.copy(currentLevelMb = clamped) else it }
            )
        }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.let { if (it.strengthSupported) it.setStrength(clamped.toShort()) }
        }
        _state.update { it.copy(bassBoostStrength = clamped) }
    }

    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            virtualizer?.let { if (it.strengthSupported) it.setStrength(clamped.toShort()) }
        }
        _state.update { it.copy(virtualizerStrength = clamped) }
    }

    fun setTrebleBoost(levelMb: Int) {
        val bands = _state.value.bands
        if (bands.isNotEmpty()) {
            val topIndex = bands.lastIndex
            setBandLevel(topIndex, levelMb)
            if (bands.size > 3) {
                setBandLevel(topIndex - 1, (levelMb * 0.6f).toInt())
            }
        }
        _state.update { it.copy(trebleBoostMb = levelMb) }
    }

    fun applyBuiltInPreset(presetName: String) {
        val bands = _state.value.bands.ifEmpty { defaultFallbackBands() }
        val normalizedCurve = when (presetName) {
            "Rock" -> listOf(500, 300, -100, 300, 600)
            "Pop" -> listOf(-100, 300, 500, 200, -100)
            "Jazz" -> listOf(400, 200, -200, 200, 500)
            "Classical" -> listOf(500, 300, -200, 400, 500)
            "Electronic" -> listOf(600, 400, 0, 300, 650)
            else -> listOf(0, 0, 0, 0, 0) // Flat
        }
        val updatedBands = bands.mapIndexed { idx, band ->
            val target = normalizedCurve.getOrElse(idx) { 0 }.coerceIn(band.minLevelMb, band.maxLevelMb)
            runCatching { equalizer?.setBandLevel(idx.toShort(), target.toShort()) }
            band.copy(currentLevelMb = target)
        }
        _state.update {
            it.copy(
                activePresetName = presetName,
                bands = updatedBands,
                trebleBoostMb = updatedBands.lastOrNull()?.currentLevelMb ?: 0
            )
        }
    }

    fun saveCustomPreset(name: String) {
        val cleanName = name.trim().take(32)
        if (cleanName.isBlank()) return
        val st = _state.value
        val csv = st.bands.joinToString(",") { it.currentLevelMb.toString() }
        scope.launch {
            eqPresetDao.upsert(
                EqPresetEntity(
                    name = cleanName,
                    bandLevelsCsv = csv,
                    bassBoost = st.bassBoostStrength,
                    virtualizer = st.virtualizerStrength,
                    trebleBoost = st.trebleBoostMb
                )
            )
            _state.update { it.copy(activePresetName = cleanName) }
        }
    }

    fun loadCustomPreset(name: String) {
        scope.launch {
            eqPresetDao.observePresets().collect { presets ->
                val match = presets.firstOrNull { it.name == name } ?: return@collect
                val levels = match.bandLevelsCsv.split(",").mapNotNull { it.toIntOrNull() }
                levels.forEachIndexed { idx, lvl -> setBandLevel(idx, lvl) }
                setBassBoost(match.bassBoost)
                setVirtualizer(match.virtualizer)
                _state.update { it.copy(activePresetName = match.name) }
                return@collect
            }
        }
    }

    private fun defaultFallbackBands(): List<EqBand> = listOf(
        EqBand(0, 60, -1500, 1500, 0),
        EqBand(1, 230, -1500, 1500, 0),
        EqBand(2, 910, -1500, 1500, 0),
        EqBand(3, 3600, -1500, 1500, 0),
        EqBand(4, 14000, -1500, 1500, 0)
    )

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        attachedSessionId = 0
    }
}
