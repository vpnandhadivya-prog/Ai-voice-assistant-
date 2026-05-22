package com.example.voice

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class CommandResult {
    data class TextReply(val speech: String, val display: String = speech) : CommandResult()
    data class HandleIntent(val intent: Intent, val speech: String, val display: String = speech) : CommandResult()
}

object CommandParser {

    private val JOKES_EN = listOf(
        "Why don't scientists trust atoms? Because they make up everything!",
        "What do you call a fake noodle? An imposter!",
        "Why did the smartphone go to therapy? Because it lost its connection!",
        "Why do programmers prefer dark mode? Because light attracts bugs!",
        "How many programmers does it take to change a light bulb? None, that's a hardware problem!"
    )

    private val JOKES_TA = listOf(
        "வழியில் கிடந்த பழைய நாற்காலியை என்ன செய்ய வேண்டும்? உட்கார வேண்டும்!",
        "ஒரு எறும்பு யானை மேல விழுந்தா என்ன ஆகும்? யானைக்கு அடி படும்!",
        "ஏன் கணினி எப்போதுமே குளிர்கிறது? ஏனெனில் அதில் விண்டோஸ் (Windows) திறந்து உள்ளது!"
    )

    private val WEATHER_CONDITIONS_EN = listOf(
        "Sunny and warm. 28 degrees Celsius.",
        "Pleasant and cloudy with gentle breezes. 24 degrees Celsius.",
        "A bit overcast, might drizzle later. 21 degrees Celsius."
    )

    private val WEATHER_CONDITIONS_TA = listOf(
        "வெப்பமான மற்றும் தெளிவான வானிலை. முப்பது டிகிரி செல்சியஸ்.",
        "மழை மேகங்களுடன் இதமான காற்று. இருபத்தி ஐந்து டிகிரி செல்சியஸ்."
    )

    fun parse(context: Context, command: String, isTamil: Boolean): CommandResult {
        val cleanCommand = command.lowercase(Locale.getDefault()).trim()
        Log.d("CommandParser", "Parsing: $cleanCommand (Tamil: $isTamil)")

        // 1. TAMIL OFFLINE COMMANDS
        if (isTamil) {
            return when {
                cleanCommand.contains("நேரம்") || cleanCommand.contains("மணி") -> {
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    CommandResult.TextReply("இப்போது நேரம் $time.", "இப்போது நேரம்: $time")
                }
                cleanCommand.contains("தேதி") || cleanCommand.contains("நாள்") -> {
                    val date = SimpleDateFormat("dd MMMM yyyy", Locale("ta", "IN")).format(Date())
                    CommandResult.TextReply("இன்று தேதி $date.", "இன்று: $date")
                }
                cleanCommand.contains("பேட்டரி") || cleanCommand.contains("மின்கலம்") -> {
                    val batteryLevel = getBatteryLevel(context)
                    CommandResult.TextReply("உங்கள் பேட்டரி அளவு $batteryLevel சதவீதம்.", "பேட்டரி அளவு: $batteryLevel%")
                }
                cleanCommand.contains("நகைச்சுவை") || cleanCommand.contains("ஜோக்") -> {
                    val joke = JOKES_TA.random()
                    CommandResult.TextReply(joke)
                }
                cleanCommand.contains("வானிலை") -> {
                    val weather = WEATHER_CONDITIONS_TA.random()
                    CommandResult.TextReply("இன்றைய வானிலை $weather", "வானிலை: $weather")
                }
                cleanCommand.contains("கேமரா") || cleanCommand.contains("புகைப்படம்") -> {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        CommandResult.HandleIntent(intent, "கேமராவை திறக்கிறேன்.", "கேமராவை திறக்கிறேன்...")
                    } else {
                        CommandResult.TextReply("உங்கள் சாதனத்தில் கேமரா பயன்பாடு கிடைக்கவில்லை.")
                    }
                }
                cleanCommand.contains("தேடு") || cleanCommand.contains("கூகுள்") -> {
                    val searchTerms = cleanCommand.replace("தேடு", "").replace("கூகுள்", "").trim()
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, searchTerms.ifEmpty { "தமிழ்" })
                    }
                    CommandResult.HandleIntent(intent, "இணையத்தில் தேடுகிறேன்.", "இணையத்தில் தேடுகிறேன்...")
                }
                cleanCommand.contains("அலாரம்") -> {
                    val (hour, minute) = parseAlarmTime(cleanCommand)
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "நெபுலா தமிழ் அலாரம்")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                    CommandResult.HandleIntent(intent, "$hour மணி $minute நிமிடத்திற்கு அலாரம் அமைக்கிறேன்.", "அலாரம் அமைக்கப்பட்டது: $hour:$minute")
                }
                cleanCommand.contains("உதவி") || cleanCommand.contains("யார்") -> {
                    CommandResult.TextReply(
                        "நான் நெபுலா, உங்கள் குரல் உதவியாளர். நான் நேரம் கூற, கேமரா திறக்க, அலாரம் வைக்க மற்றும் இணையத்தில் தேட உதவுவேன்.",
                        "உதவி கையேடு:\n• 'நேரம்' - தற்போதைய நேரம்\n• 'பேட்டரி' - பேட்டரி நிலை\n• 'கேமரா' - கேமராவை திற\n• 'ஜோக்' - நகைச்சுவை\n• 'அலாரம்' - அலாரம் வைக்க\n• 'தேடு [வார்த்தை]' - கூகுள் தேடல்"
                    )
                }
                else -> {
                    // Fallback to Gemini if online (managed outside), or basic default Tamil
                    CommandResult.TextReply(
                        "மன்னிக்கவும், எனக்கு அது இன்னும் புரியவில்லை. இணையம் இணைக்கப்பட்ட பின் கேட்கவும் அல்லது மற்ற கட்டளைகளை முயற்சிக்கவும்."
                    )
                }
            }
        }

        // 2. ENGLISH OFFLINE COMMANDS
        return when {
            cleanCommand.contains("time") || cleanCommand.contains("what time") -> {
                val time = SimpleDateFormat("h:mm a", Locale.US).format(Date())
                CommandResult.TextReply("The time is $time.", "Current Time: $time")
            }
            cleanCommand.contains("date") || cleanCommand.contains("what day") || cleanCommand.contains("today") -> {
                val date = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US).format(Date())
                CommandResult.TextReply("Today is $date.", "Today's Date: $date")
            }
            cleanCommand.contains("battery") || cleanCommand.contains("charge") -> {
                val batteryLevel = getBatteryLevel(context)
                CommandResult.TextReply("Your device battery level is at $batteryLevel percent.", "Battery Level: $batteryLevel%")
            }
            cleanCommand.contains("joke") || cleanCommand.contains("tell me a joke") || cleanCommand.contains("make me laugh") -> {
                val joke = JOKES_EN.random()
                CommandResult.TextReply(joke)
            }
            cleanCommand.contains("weather") || cleanCommand.contains("temperature") || cleanCommand.contains("forecast") -> {
                val weather = WEATHER_CONDITIONS_EN.random()
                CommandResult.TextReply("Current weather is $weather", "Weather: $weather")
            }
            cleanCommand.contains("open camera") || cleanCommand.contains("launch camera") || cleanCommand.startsWith("camera") -> {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                CommandResult.HandleIntent(intent, "Opening your camera app.", "Launching camera...")
            }
            cleanCommand.contains("open maps") || cleanCommand.contains("navigate") -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("geo:0,0?q=restaurants")
                }
                CommandResult.HandleIntent(intent, "Opening maps.", "Launching maps...")
            }
            cleanCommand.contains("calculator") -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    CommandResult.HandleIntent(intent, "Opening calculator.", "Launching calculator...")
                } catch (e: Exception) {
                    CommandResult.TextReply("I couldn't find a default calculator. Please launch it manually.")
                }
            }
            cleanCommand.contains("music") || cleanCommand.contains("play song") -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    CommandResult.HandleIntent(intent, "Opening your music companion.", "Launching music app...")
                } catch (e: Exception) {
                    // Fallback to youtube search
                    val ytIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=music"))
                    CommandResult.HandleIntent(ytIntent, "Searching youtube for music.", "Opening YouTube music...")
                }
            }
            cleanCommand.startsWith("search") || cleanCommand.startsWith("google") || cleanCommand.startsWith("find") -> {
                val query = cleanCommand.replace("search", "").replace("google", "").replace("find", "").trim()
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query.ifEmpty { "AI technology" })
                }
                CommandResult.HandleIntent(intent, "Searching the web for $query", "Searching the Web for '$query'...")
            }
            cleanCommand.contains("alarm") || cleanCommand.contains("wake me up") -> {
                val (hour, minute) = parseAlarmTime(cleanCommand)
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Nebula AI Alarm")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                CommandResult.HandleIntent(intent, "Setting an alarm for $hour:$minute.", "Alarm set for $hour:${String.format("%02d", minute)}")
            }
            cleanCommand.contains("help") || cleanCommand.contains("what can you do") || cleanCommand.contains("features") -> {
                CommandResult.TextReply(
                    "I am Nebula, your futuristic voice assistant. You can ask me about the time, battery, weather, ask for a joke, set alarms, or command me to open apps like camera and map.",
                    "Quick Commands List:\n• 'What time is it?' - Device time\n• 'Check battery' - Current power level\n• 'Open camera' - Quick shutter\n• 'Tell me a joke' - Light humor\n• 'Set alarm for 7:30' - Alarm scheduler\n• 'Search space facts' - Google search\n• Say 'Speak Tamil' to toggle language!"
                )
            }
            else -> {
                CommandResult.TextReply("I couldn't process this request offline. Please make sure internet is active, or use 'help' for offline capabilities.")
            }
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            return (level * 100 / scale.toFloat()).toInt()
        }
        return 75 // Mock fallback
    }

    private fun parseAlarmTime(command: String): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        var hour = calendar.get(Calendar.HOUR_OF_DAY) + 1
        var minute = 0

        try {
            // Check for simple patterns like "7 30", "7:30", "8:15", "8"
            val timePattern = "(\\d{1,2})[:\\s](\\d{2})".toRegex()
            val match = timePattern.find(command)
            if (match != null) {
                hour = match.groupValues[1].toInt()
                minute = match.groupValues[2].toInt()
            } else {
                val singleHourPattern = "(\\d{1,2})".toRegex()
                val singleMatch = singleHourPattern.find(command)
                if (singleMatch != null) {
                    hour = singleMatch.groupValues[1].toInt()
                }
            }
            // Simple validation
            if (hour !in 0..23) hour = 8
            if (minute !in 0..59) minute = 0
            
            // If pm is mentioned and it's 12-hour format, adjust
            if ((command.contains("pm") || command.contains("p.m.")) && hour < 12) {
                hour += 12
            }
            if ((command.contains("am") || command.contains("a.m.")) && hour == 12) {
                hour = 0
            }
        } catch (e: Exception) {
            Log.e("CommandParser", "Error parsing alarm time: ${e.message}")
        }
        return Pair(hour, minute)
    }
}
