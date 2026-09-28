package com.example.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.net.URLEncoder

data class CommandResult(
    val success: Boolean,
    val actionName: String,
    val description: String,
    val sassyVoiceReply: String
)

/**
 * Handles automatic command execution on device:
 * - Automatically opens requested apps (YouTube, WhatsApp, Instagram, Camera, Maps, etc.)
 * - Opens system utilities (Calculator, Settings, Dialer, Camera)
 * - Automatically executes web searches & URL openings
 * - Provides sassy Hindi voice responses for MJ
 */
class AppCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "AppCommandExecutor"
    }

    /**
     * Executes automatic commands based on app or action name
     */
    fun executeAutoCommand(rawCommand: String, queryParam: String = ""): CommandResult {
        val cmd = rawCommand.lowercase().trim()

        return when {
            // Camera
            cmd.contains("camera") || cmd.contains("photo") || cmd.contains("selfie") -> {
                openCamera()
            }

            // YouTube
            cmd.contains("youtube") || cmd.contains("yt") || cmd.contains("video") -> {
                openAppOrWeb(
                    packageName = "com.google.android.youtube",
                    fallbackUrl = if (queryParam.isNotBlank()) "https://www.youtube.com/results?search_query=${URLEncoder.encode(queryParam, "UTF-8")}" else "https://www.youtube.com",
                    appName = "YouTube"
                )
            }

            // WhatsApp
            cmd.contains("whatsapp") || cmd.contains("chat") -> {
                openAppOrWeb(
                    packageName = "com.whatsapp",
                    fallbackUrl = "https://web.whatsapp.com",
                    appName = "WhatsApp"
                )
            }

            // Instagram
            cmd.contains("instagram") || cmd.contains("insta") || cmd.contains("reels") -> {
                openAppOrWeb(
                    packageName = "com.instagram.android",
                    fallbackUrl = "https://www.instagram.com",
                    appName = "Instagram"
                )
            }

            // Google Maps / Location
            cmd.contains("map") || cmd.contains("maps") || cmd.contains("location") || cmd.contains("rasta") -> {
                openMaps(queryParam)
            }

            // Calculator
            cmd.contains("calculator") || cmd.contains("hisaab") || cmd.contains("calc") -> {
                openCalculator()
            }

            // Settings
            cmd.contains("setting") || cmd.contains("settings") -> {
                openDeviceSettings()
            }

            // Phone Dialer / Call
            cmd.contains("dialer") || cmd.contains("phone") || cmd.contains("call") -> {
                openDialer(queryParam)
            }

            // Spotify / Music
            cmd.contains("spotify") || cmd.contains("music") || cmd.contains("gaana") || cmd.contains("song") -> {
                openAppOrWeb(
                    packageName = "com.spotify.music",
                    fallbackUrl = "https://open.spotify.com",
                    appName = "Spotify"
                )
            }

            // Chrome / Browser
            cmd.contains("chrome") || cmd.contains("browser") || cmd.contains("internet") -> {
                openWebsite(if (queryParam.isNotBlank()) queryParam else "https://www.google.com")
            }

            // Google Search
            cmd.contains("search") || cmd.contains("google") || cmd.contains("dhundo") -> {
                val searchQuery = if (queryParam.isNotBlank()) queryParam else cmd.replace("search", "").replace("google", "").trim()
                searchGoogle(searchQuery.ifBlank { "Google" })
            }

            // Gallery / Photos
            cmd.contains("gallery") || cmd.contains("photos") || cmd.contains("album") -> {
                openGallery()
            }

            // Fallback: Check if it looks like a URL
            cmd.contains("http://") || cmd.contains("https://") || cmd.contains(".com") || cmd.contains(".org") -> {
                openWebsite(cmd)
            }

            else -> {
                // Default search
                searchGoogle(rawCommand)
            }
        }
    }

    fun openWebsite(url: String): CommandResult {
        return try {
            val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            CommandResult(
                success = true,
                actionName = "Open Website",
                description = "Opened: $validUrl",
                sassyVoiceReply = "Lo ji, $validUrl khol diya! Ab batao aage kya karna hai? 😉"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error opening website: ${e.message}")
            CommandResult(
                success = false,
                actionName = "Open Website",
                description = "Failed to open $url",
                sassyVoiceReply = "Arre yaar, website kholne mein dikkat ho gayi. Dobara try karo na!"
            )
        }
    }

    fun searchGoogle(query: String): CommandResult {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.google.com/search?q=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            CommandResult(
                success = true,
                actionName = "Google Search",
                description = "Searched: \"$query\"",
                sassyVoiceReply = "Google par \"$query\" dhoondh diya tumhare liye! Dekho aur batao kaisa laga! 🔍"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}")
            CommandResult(
                success = false,
                actionName = "Google Search",
                description = "Search failed for $query",
                sassyVoiceReply = "Search karne mein thodi pareshani ho gayi baby!"
            )
        }
    }

    fun openCamera(): CommandResult {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CommandResult(
                success = true,
                actionName = "Open Camera",
                description = "Camera launched automatically",
                sassyVoiceReply = "Camera khol diya maine! Smile please, MJ dekh rahi hai! 📸✨"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera launch failed: ${e.message}")
            CommandResult(
                success = false,
                actionName = "Open Camera",
                description = "Camera could not be launched",
                sassyVoiceReply = "Camera kholne mein dikkat aayi. Kahi permission toh block nahi?"
            )
        }
    }

    fun openDialer(phoneNumber: String = ""): CommandResult {
        return try {
            val intent = if (phoneNumber.isNotBlank()) {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            } else {
                Intent(Intent.ACTION_DIAL)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CommandResult(
                success = true,
                actionName = "Open Dialer",
                description = if (phoneNumber.isNotBlank()) "Dialing: $phoneNumber" else "Dialer opened",
                sassyVoiceReply = "Phone dialer khol diya! Kis se baat karne ka plan hai waise? 😏"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Dialer error: ${e.message}")
            CommandResult(
                success = false,
                actionName = "Open Dialer",
                description = "Could not open dialer",
                sassyVoiceReply = "Dialer nahi khul paya baby!"
            )
        }
    }

    fun openCalculator(): CommandResult {
        return try {
            val calcIntent = Intent().apply {
                setAction(Intent.ACTION_MAIN)
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(calcIntent)
            CommandResult(
                success = true,
                actionName = "Open Calculator",
                description = "Calculator opened",
                sassyVoiceReply = "Calculator hazir hai! Kis cheez ka hisaab-kitaab lagana hai aaj? 🔢"
            )
        } catch (e: Exception) {
            // Fallback to web calculator
            openWebsite("https://www.google.com/search?q=calculator")
            CommandResult(
                success = true,
                actionName = "Open Calculator",
                description = "Online Calculator opened",
                sassyVoiceReply = "Calculator khol diya! Lo jodo aur ghatao!"
            )
        }
    }

    fun openDeviceSettings(): CommandResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CommandResult(
                success = true,
                actionName = "Open Settings",
                description = "Device Settings opened",
                sassyVoiceReply = "Settings khol di! Par meri setting toh tumhare sath pehle se hai! 😜"
            )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                actionName = "Open Settings",
                description = "Failed to open settings",
                sassyVoiceReply = "Settings kholne mein dikkat ho gayi!"
            )
        }
    }

    fun openGallery(): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CommandResult(
                success = true,
                actionName = "Open Gallery",
                description = "Photos Gallery opened",
                sassyVoiceReply = "Gallery khol di! Puraani yaadein dekhne ka mood hai kya? 🖼️"
            )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                actionName = "Open Gallery",
                description = "Failed to open gallery",
                sassyVoiceReply = "Gallery nahi khul payi baby!"
            )
        }
    }

    fun openMaps(locationQuery: String = ""): CommandResult {
        return try {
            val uri = if (locationQuery.isNotBlank()) {
                Uri.parse("geo:0,0?q=" + URLEncoder.encode(locationQuery, "UTF-8"))
            } else {
                Uri.parse("geo:0,0?q=restaurants+near+me")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CommandResult(
                success = true,
                actionName = "Open Maps",
                description = if (locationQuery.isNotBlank()) "Maps: $locationQuery" else "Maps opened",
                sassyVoiceReply = "Google Maps khol diya! Kaha leke chal rahe ho mujhe ghumane? 🗺️"
            )
        } catch (e: Exception) {
            openWebsite("https://maps.google.com")
            CommandResult(
                success = true,
                actionName = "Open Maps",
                description = "Maps Web opened",
                sassyVoiceReply = "Maps khol diya! Safar ke maze lo!"
            )
        }
    }

    private fun openAppOrWeb(packageName: String, fallbackUrl: String, appName: String): CommandResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                CommandResult(
                    success = true,
                    actionName = "Open $appName",
                    description = "$appName app opened automatically",
                    sassyVoiceReply = "Lo ji, $appName app khol diya! Enjoy karo baby! 🚀"
                )
            } else {
                // Fallback to web or Play Store
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                CommandResult(
                    success = true,
                    actionName = "Open $appName",
                    description = "$appName web opened",
                    sassyVoiceReply = "$appName khol diya browser mein! Ab maze karo! 💫"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening $appName: ${e.message}")
            openWebsite(fallbackUrl)
            CommandResult(
                success = true,
                actionName = "Open $appName",
                description = "$appName opened",
                sassyVoiceReply = "$appName khol diya tumhare liye!"
            )
        }
    }
}
