package com.example.personalassistant

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Ink = Color(0xFF10111A)
private val Panel = Color(0xFF1A1C29)
private val Purple = Color(0xFF9B87FF)
private val Soft = Color(0xFFB8BACB)
private const val MAX_MODEL_BYTES = 1_000_000_000L
private val EngineeringPrompts = listOf(
    "Calculate stress and strain for a loaded shaft",
    "Explain a mechanism and its degrees of freedom",
    "Size a compression spring from force and travel",
    "Review a design for failure modes and safety factors"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraApp(this) }
    }
}

data class ChatMessage(val text: String, val fromUser: Boolean)

@Composable
private fun AuraApp(activity: ComponentActivity) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var searchWeb by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(LocalQwenEngine.isLoaded()) }
    var status by remember { mutableStateOf("Mechanical engineering workspace · import a small Qwen3 GGUF model to begin offline.") }
    val messages = remember { mutableStateListOf(ChatMessage("Welcome to AURA Engineering. I help with mechanical design, mechanics, materials, thermodynamics, manufacturing, CAD planning, and engineering calculations. I will state assumptions, use SI units by default, show equations, and flag checks that need validation before fabrication.", false)) }
    val scope = rememberCoroutineScope()

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            status = "Importing model into app storage…"
            val result = withContext(Dispatchers.IO) { importModel(activity, uri) }
            result.onSuccess { file ->
                status = "Loading ${file.name} locally. First load can take a while…"
                val loaded = withContext(Dispatchers.IO) { LocalQwenEngine.load(file) }
                loaded.onSuccess {
                    modelReady = true
                    status = "Qwen3 loaded on this phone. Chat works offline."
                }.onFailure { e -> status = e.message ?: "Could not load model."; modelReady = false }
            }.onFailure { e -> status = e.message ?: "Model import failed." }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        val saved = LocalQwenEngine.modelFile(activity)
        if (saved.exists() && saved.length() > 0L) {
            busy = true
            status = "Loading your saved local model…"
            withContext(Dispatchers.IO) { LocalQwenEngine.load(saved) }
                .onSuccess { modelReady = true; status = "Qwen3 loaded. Offline chat is ready." }
                .onFailure { modelReady = false; status = it.message ?: "Saved model could not load. Re-import a smaller model." }
            busy = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Purple, onBackground = Color.White, onSurface = Color.White)) {
        Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Purple.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) { Text("✦", color = Purple, fontSize = 24.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("AURA", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("MECHANICAL ENGINEERING · LOCAL QWEN3", fontSize = 9.sp, color = Soft, letterSpacing = .5.sp)
                }
                Surface(color = if (modelReady) Color(0xFF244D3D) else Panel, shape = RoundedCornerShape(50)) {
                    Text(if (modelReady) "LOCAL READY" else "NO MODEL", modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), fontSize = 9.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (modelReady) "Engineering copilot · on device" else "Set up your engineering model", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (modelReady) "Mechanical calculations, design reasoning and technical explanations." else "Choose a compatible Qwen3 .gguf model file.", fontSize = 11.sp, color = Soft)
                }
                TextButton(enabled = !busy, onClick = { modelPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }) { Text("Model", color = Purple) }
            }
            Text("ENGINEERING QUICK STARTS", color = Soft, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EngineeringPrompts.take(2).forEach { suggestion ->
                    OutlinedButton(onClick = { input = suggestion }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 7.dp)) {
                        Text(suggestion, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EngineeringPrompts.drop(2).forEach { suggestion ->
                    OutlinedButton(onClick = { input = suggestion }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 7.dp)) {
                        Text(suggestion, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthIn(max = 310.dp).background(if (message.fromUser) Color(0xFF40356D) else Panel, RoundedCornerShape(17.dp)).padding(13.dp)) {
                            if (!message.fromUser) Text("AURA", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(message.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
                if (busy) item { Text("Working locally${if (searchWeb) " and searching the web" else ""}…", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = searchWeb, onCheckedChange = { searchWeb = it }, enabled = !busy, colors = CheckboxDefaults.colors(checkedColor = Purple))
                Column(Modifier.weight(1f)) {
                    Text("Search the web", color = Color.White, fontSize = 12.sp)
                    Text("Optional · no API key · requires internet", color = Soft, fontSize = 10.sp)
                }
            }
            if (status.isNotBlank()) Text(status, color = Soft, fontSize = 10.sp, modifier = Modifier.padding(bottom = 7.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Panel, RoundedCornerShape(23.dp)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (input.isEmpty()) Text("Ask about a mechanism, calculation, material, or manufacturing process…", color = Soft, fontSize = 13.sp, lineHeight = 17.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 19.sp),
                        cursorBrush = SolidColor(Purple),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { })
                    )
                }
                Button(onClick = {
                    val prompt = input.trim()
                    if (prompt.isNotEmpty() && !busy) {
                        if (!modelReady) { status = "Import and load a Qwen3 GGUF model first."; return@Button }
                        messages.add(ChatMessage(prompt, true)); input = ""; busy = true; status = ""
                        scope.launch {
                            var webContext = ""
                            if (searchWeb) {
                                status = "Searching DuckDuckGo without an API key…"
                                val searchResult = WebSearchService.search(prompt)
                                searchResult.onSuccess { webContext = it }
                                    .onFailure { status = "Web search failed; answering with the local model only. ${it.message.orEmpty()}" }
                            }
                            val answer = withContext(Dispatchers.IO) { LocalQwenEngine.generate(messages.toList(), webContext) }
                            answer.onSuccess { messages.add(ChatMessage(it, false)); if (status.isBlank() || !searchWeb) status = "Generated locally${if (webContext.isNotBlank()) " with web results" else ""}." }
                                .onFailure { status = it.message ?: "Local inference failed." }
                            busy = false
                        }
                    }
                }, enabled = input.isNotBlank() && !busy, shape = CircleShape, contentPadding = PaddingValues(horizontal = 17.dp, vertical = 11.dp)) { Text("↑", fontSize = 19.sp) }
            }
        }
    }
}

private fun importModel(context: Context, uri: Uri): Result<File> = runCatching {
    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "model.gguf"
    require(displayName.endsWith(".gguf", ignoreCase = true)) { "Select a GGUF model file. Safetensors and GGUF split shards are not supported by this starter." }
    val target = LocalQwenEngine.modelFile(context)
    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, "model-import.tmp")
    var total = 0L
    context.contentResolver.openInputStream(uri)?.use { input ->
        temp.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_MODEL_BYTES) { "Model exceeds the 1 GB safety limit for this phone." }
                output.write(buffer, 0, n)
            }
        }
    } ?: error("Android could not open that file.")
    require(total > 0) { "The selected model file is empty." }
    if (target.exists()) target.delete()
    check(temp.renameTo(target)) { "Could not save model in app storage." }
    target
}
