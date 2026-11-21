package com.example.data.repository

import android.util.Log
import com.example.domain.model.Track
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiMusicCommandResult(
    val assistantReply: String,
    val actionType: String, // "PLAY_MIX", "CREATE_PLAYLIST", "SEARCH_CATALOG", "CHAT_ONLY"
    val playlistTitle: String,
    val searchKeywords: String,
    val matchedTrackIds: List<String>,
    val targetDurationMinutes: Int
)

class GeminiAssistantRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val commandSchema = Schema.obj(
        properties = mapOf(
            "assistantReply" to Schema.string(description = "Warm, concise DJ response to the user."),
            "actionType" to Schema.enumeration(
                values = listOf("PLAY_MIX", "CREATE_PLAYLIST", "SEARCH_CATALOG", "CHAT_ONLY"),
                description = "Action MUSICVERSE AI should execute inside the player."
            ),
            "playlistTitle" to Schema.string(description = "Short title if creating or playing a mix."),
            "searchKeywords" to Schema.string(description = "Keywords to search open audio catalog if needed (e.g. 'bhojpuri romantic', 'workout electronic', 'meditation ambient', 'hindi')."),
            "matchedTrackIds" to Schema.array(
                items = Schema.string(),
                description = "IDs from the user's provided availableTracks list that fit the request."
            ),
            "targetDurationMinutes" to Schema.integer(description = "Optional target minutes if requested (e.g. 30 for 30-minute meditation), else 0.")
        ),
        optionalProperties = listOf("targetDurationMinutes")
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = commandSchema
            }
        )
    }

    suspend fun interpretMusicCommand(
        userPrompt: String,
        availableTracks: List<Track>,
        currentTrack: Track?,
        recentArtists: List<String>
    ): Result<AiMusicCommandResult> {
        return try {
            val librarySummary = availableTracks.take(40).joinToString("\n") { t ->
                "- id=${t.id} | title=${t.title} | artist=${t.artist} | genre=${t.genre}"
            }
            val nowPlayingInfo = currentTrack?.let {
                "Currently playing: ${it.title} by ${it.artist} (${it.genre})"
            } ?: "Nothing currently playing."

            val prompt = """
                You are MUSICVERSE AI ASSISTANT, an expert AI Music DJ inside an Android music player.
                User request: "$userPrompt"
                $nowPlayingInfo
                Recent user favorite/history artists: ${recentArtists.take(10).joinToString(", ").ifBlank { "None yet" }}

                Available in-app tracks:
                $librarySummary

                Instructions:
                - Choose matchedTrackIds strictly from the available in-app tracks above when any match the mood/genre/language/request.
                - If the user asks for external genres/languages (e.g. Bhojpuri, Hindi, Punjabi, Workout, Meditation) or wants new music, set actionType to CREATE_PLAYLIST or SEARCH_CATALOG or PLAY_MIX and provide helpful searchKeywords for the open audio catalog.
                - Keep assistantReply energetic, helpful, and under 3 sentences.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val json = JSONObject(rawJson)
            val idsArr = json.optJSONArray("matchedTrackIds")
            val matchedIds = buildList {
                if (idsArr != null) {
                    for (i in 0 until idsArr.length()) {
                        val id = idsArr.optString(i)
                        if (id.isNotBlank()) add(id)
                    }
                }
            }

            Result.success(
                AiMusicCommandResult(
                    assistantReply = json.optString("assistantReply", "Here is a fresh mix for you!"),
                    actionType = json.optString("actionType", "PLAY_MIX"),
                    playlistTitle = json.optString("playlistTitle", "MusicVerse AI Mix"),
                    searchKeywords = json.optString("searchKeywords", userPrompt),
                    matchedTrackIds = matchedIds,
                    targetDurationMinutes = json.optInt("targetDurationMinutes", 0)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI command timed out", e)
            Result.failure(IllegalStateException("AI request timed out after 60s. Please retry.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI command failed", e)
            Result.failure(e)
        }
    }

    fun streamConversationalReply(
        userPrompt: String,
        currentTrack: Track?,
        recentGenres: List<String>
    ): Flow<String> = flow {
        val contextLine = currentTrack?.let {
            "Now playing: '${it.title}' by ${it.artist} (${it.genre}). "
        } ?: ""
        val historyLine = if (recentGenres.isNotEmpty()) {
            "User listens to: ${recentGenres.distinct().take(6).joinToString(", ")}. "
        } else ""

        val prompt = """
            You are MUSICVERSE AI ASSISTANT, a knowledgeable music curator and audio engineer.
            $contextLine$historyLine
            Answer concisely and conversationally: $userPrompt
        """.trimIndent()

        val acc = StringBuilder()
        withTimeout(60_000L) {
            chatModel.generateContentStream(prompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    acc.append(part)
                    emit(acc.toString())
                }
            }
        }
        if (acc.isBlank()) {
            throw IllegalStateException("Gemini returned an empty stream.")
        }
    }.catch { error ->
        if (error is CancellationException && error !is TimeoutCancellationException) {
            throw error
        }
        Log.e(TAG, "Streaming AI chat failed", error)
        throw error
    }

    private companion object {
        const val TAG = "GeminiAssistantRepo"
    }
}
