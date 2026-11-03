package com.flow.timetable

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : ComponentActivity() {
    private lateinit var database: ScheduleDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ScheduleDatabase(this)
        setContent {
            FlowApp(
                loadEntries = database::getEntries,
                saveEntries = database::replaceEntries,
                scanImage = ::scanImage
            )
        }
    }

    private fun scanImage(uri: Uri, onResult: (Result<List<TimetableEntry>>) -> Unit) {
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (exception: Exception) {
            onResult(Result.failure(exception))
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        line.boundingBox?.let { box ->
                            OcrLine(line.text, box.left.toFloat(), box.top.toFloat(),
                                box.right.toFloat(), box.bottom.toFloat())
                        }
                    }
                }
                onResult(Result.success(TimetableReconstructor.reconstruct(lines)))
            }
            .addOnFailureListener { exception -> onResult(Result.failure(exception)) }
            .addOnCompleteListener { recognizer.close() }
    }
}

@Composable
private fun FlowApp(
    loadEntries: () -> List<TimetableEntry>,
    saveEntries: (List<TimetableEntry>) -> Unit,
    scanImage: (Uri, (Result<List<TimetableEntry>>) -> Unit) -> Unit
) {
    val ink = Color(0xFF152A25)
    val green = Color(0xFF28664E)
    val canvas = Color(0xFFF6F7F2)
    var entries by remember { mutableStateOf(emptyList<TimetableEntry>()) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { entries = loadEntries() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scanning = true
        message = null
        scanImage(uri) { result ->
            scanning = false
            result.fold(
                onSuccess = { scanned ->
                    if (scanned.isEmpty()) {
                        message = "Couldn’t find day, time, and class labels together. Try a clearer timetable photo."
                    } else {
                        entries = scanned
                        saveEntries(scanned)
                        message = "Timetable scanned and saved on this device."
                    }
                },
                onFailure = { error ->
                    message = error.localizedMessage ?: "Could not read the selected image."
                }
            )
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = canvas) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ink)
                        .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 28.dp)
                ) {
                    Text("FLOW", color = Color(0xFFA8D9B3), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Your week,\nin rhythm.", color = Color.White, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !scanning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8D9B3), contentColor = ink),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = ink)
                            Text("  Reading timetable…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("＋  Scan a timetable", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Text("THIS WEEK", color = green, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(12.dp))
                    message?.let {
                        Text(it, color = if (it.startsWith("Couldn’t")) Color(0xFF9A4A36) else green, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (entries.isEmpty()) {
                        EmptyState()
                    } else {
                        val grouped = entries.groupBy { it.day }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            grouped.forEach { (day, lessons) ->
                                item(key = day) {
                                    Text(day, color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                items(lessons, key = { "${it.day}-${it.time}-${it.subject}" }) { lesson ->
                                    LessonCard(lesson, green, ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(54.dp).background(Color(0xFFE8F1E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✳", color = Color(0xFF28664E), fontSize = 24.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Make room for what matters.", color = Color(0xFF152A25), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose a clear photo of your timetable to get started. Your schedule stays on this device.",
                color = Color(0xFF6D7772),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun LessonCard(lesson: TimetableEntry, green: Color, ink: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp, 38.dp).background(green, RoundedCornerShape(2.dp)))
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(lesson.subject, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(lesson.time, color = Color(0xFF6D7772), fontSize = 13.sp)
            }
            Text("CLASS", color = green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}
