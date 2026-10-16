package com.example.kyrgyzflashcards

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kyrgyzflashcards.data.AppDatabase
import com.example.kyrgyzflashcards.data.Word
import com.example.kyrgyzflashcards.data.WordSet
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KyrgyzApp() }
    }
}

private enum class Screen { HOME, IMPORT, SETS, STUDY, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KyrgyzApp() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val sets by dao.observeSets().collectAsStateWithLifecycle(initialValue = emptyList())
    var screen by remember { mutableStateOf(Screen.HOME) }
    var importText by remember { mutableStateOf("") }
    var setName by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selectedSet by remember { mutableStateOf<WordSet?>(null) }
    var studyWords by remember { mutableStateOf(listOf<Word>()) }
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var timerEnabled by remember { mutableStateOf(false) }
    var timerMinutes by remember { mutableIntStateOf(5) }
    var remainingSeconds by remember { mutableIntStateOf(300) }
    var pauseTimer by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }.onSuccess {
                importText = it
                parsed = parseWords(it)
                if (setName.isBlank()) setName = "Импортированный набор"
                screen = Screen.IMPORT
            }.onFailure { message = "Не удалось прочитать файл. Попробуйте TXT в кодировке UTF-8." }
        }
    }

    LaunchedEffect(screen, startedAt, pauseTimer, timerEnabled) {
        while (screen == Screen.STUDY && !pauseTimer) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds = if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000 else elapsedSeconds + 1
            if (timerEnabled && remainingSeconds > 0) {
                remainingSeconds -= 1
                if (remainingSeconds <= 0) {
                    screen = Screen.RESULTS
                }
            }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF243B64),
        secondary = androidx.compose.ui.graphics.Color(0xFF278568),
        background = androidx.compose.ui.graphics.Color(0xFFF5F7FB),
        surface = androidx.compose.ui.graphics.Color.White
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Кыргызча карточки", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (screen != Screen.HOME) {
                            IconButton(onClick = { screen = if (screen == Screen.STUDY) Screen.RESULTS else Screen.HOME }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                message?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f))
                            TextButton(onClick = { message = null }) { Text("OK") }
                        }
                    }
                }
                when (screen) {
                    Screen.HOME -> {
                        Spacer(Modifier.height(8.dp))
                        Text("Изучай кыргызские слова", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Импортируй свой список и повторяй слова в удобном темпе.")
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Твои наборы", style = MaterialTheme.typography.titleMedium)
                                Text("${sets.size} сохранённых наборов")
                                Text("Все данные хранятся на устройстве и доступны офлайн.")
                            }
                        }
                        Button(onClick = { screen = Screen.IMPORT; importText = ""; parsed = emptyList(); setName = "" }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить слова")
                        }
                        OutlinedButton(onClick = { screen = Screen.SETS }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Мои наборы") }
                        Button(
                            onClick = {
                                scope.launch {
                                    val ids = sets.map { it.id }
                                    if (ids.isEmpty()) message = "Сначала добавьте набор слов."
                                    else {
                                        studyWords = dao.wordsForSets(ids).distinctBy { it.kyrgyz.trim().lowercase() + "|" + it.russian.trim().lowercase() }
                                        if (studyWords.isEmpty()) message = "В выбранных наборах пока нет слов."
                                        else {
                                            index = 0; flipped = false; correct = 0; wrong = 0
                                            startedAt = System.currentTimeMillis(); elapsedSeconds = 0
                                            remainingSeconds = timerMinutes * 60; pauseTimer = false
                                            screen = Screen.STUDY
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) { Text("Начать обучение — все наборы") }
                        Spacer(Modifier.height(4.dp))
                        Text("Совет: сначала попробуй вспомнить перевод, затем нажми на карточку.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Screen.IMPORT -> {
                        Text("Добавить слова", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = setName, onValueChange = { setName = it }, label = { Text("Название набора") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(
                            value = importText, onValueChange = { importText = it; parsed = parseWords(it) },
                            label = { Text("Вставь слова: кыргызский — русский") },
                            placeholder = { Text("таштоо — бросать\nжудотту    измучил") },
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 140.dp, max = 280.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { filePicker.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(4.dp)); Text("Выбрать TXT")
                            }
                            OutlinedButton(onClick = { parsed = parseWords(importText) }, modifier = Modifier.weight(1f)) { Text("Распознать") }
                        }
                        Text("Распознано пар: ${parsed.size}", fontWeight = FontWeight.SemiBold)
                        if (parsed.isNotEmpty()) {
                            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(parsed) { pair ->
                                    Card {
                                        Row(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text(pair.first, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                            Text("→", Modifier.padding(horizontal = 6.dp))
                                            Text(pair.second, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val cleaned = parseWords(importText).distinctBy { it.first.lowercase() + "|" + it.second.lowercase() }
                                    if (setName.isBlank()) message = "Введите название набора."
                                    else if (cleaned.isEmpty()) message = "Не удалось найти пары. Используйте тире, табуляцию или несколько пробелов."
                                    else {
                                        val id = dao.insertSet(WordSet(name = setName.trim()))
                                        dao.insertWords(cleaned.map { Word(setId = id, kyrgyz = it.first, russian = it.second) })
                                        importText = ""; parsed = emptyList(); setName = ""; screen = Screen.SETS
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("Сохранить набор") }
                    }
                    Screen.SETS -> {
                        Text("Мои наборы", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (sets.isEmpty()) {
                            Text("Пока нет наборов. Добавь слова через кнопку «Добавить слова».")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(sets, key = { it.id }) { set ->
                                    var count by remember(set.id) { mutableIntStateOf(0) }
                                    var learned by remember(set.id) { mutableIntStateOf(0) }
                                    LaunchedEffect(set.id) { count = dao.wordCount(set.id); learned = dao.learnedCount(set.id) }
                                    Card(shape = RoundedCornerShape(18.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(set.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("$count слов • выучено $learned")
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = {
                                                    scope.launch {
                                                        studyWords = dao.wordsForSets(listOf(set.id)).shuffled()
                                                        selectedSet = set
                                                        if (studyWords.isNotEmpty()) {
                                                            index = 0; flipped = false; correct = 0; wrong = 0
                                                            startedAt = System.currentTimeMillis(); elapsedSeconds = 0
                                                            remainingSeconds = timerMinutes * 60; pauseTimer = false
                                                            screen = Screen.STUDY
                                                        }
                                                    }
                                                }, modifier = Modifier.weight(1f)) { Text("Учить") }
                                                OutlinedButton(onClick = {
                                                    scope.launch {
                                                        dao.deleteWordsForSet(set.id); dao.deleteSet(set.id)
                                                    }
                                                }) { Icon(Icons.Default.Delete, contentDescription = "Удалить") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { screen = Screen.IMPORT }, modifier = Modifier.fillMaxWidth()) { Text("Добавить ещё набор") }
                    }
                    Screen.STUDY -> {
                        if (studyWords.isEmpty() || index >= studyWords.size) {
                            screen = Screen.RESULTS
                        } else {
                            val word = studyWords[index]
                            Text("Карточка ${index + 1} из ${studyWords.size}", style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(progress = { (index + 1f) / studyWords.size }, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (timerEnabled) "Осталось: ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}" else "Время: ${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}")
                                TextButton(onClick = { pauseTimer = !pauseTimer }) { Text(if (pauseTimer) "Продолжить" else "Пауза") }
                            }
                            var drag by remember(index) { mutableFloatStateOf(0f) }
                            Card(
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
                                    .pointerInput(index, flipped) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, amount -> drag += amount },
                                            onDragEnd = {
                                                if (flipped && drag > 90) answer(true, word, dao, scope) {
                                                    correct++; nextCard(studyWords.size, index, { index = it }, { flipped = it }, { screen = it })
                                                }
                                                else if (flipped && drag < -90) answer(false, word, dao, scope) {
                                                    wrong++; nextCard(studyWords.size, index, { index = it }, { flipped = it }, { screen = it })
                                                }
                                                drag = 0f
                                            }
                                        )
                                    },
                                shape = RoundedCornerShape(30.dp),
                                colors = CardDefaults.cardColors(containerColor = if (flipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(if (flipped) "ПЕРЕВОД" else "ВСПОМНИ ПЕРЕВОД", style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        if (flipped) word.kyrgyz else word.russian,
                                        style = MaterialTheme.typography.headlineMedium,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(22.dp))
                                    Text(if (flipped) "Свайп вправо: знаю • влево: повторить" else "Нажми, чтобы увидеть ответ", textAlign = TextAlign.Center)
                                }
                            }
                            if (!flipped) {
                                Button(onClick = { flipped = true }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Показать перевод") }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                dao.updateWord(word.copy(wrongCount = word.wrongCount + 1, streak = 0, learned = false, lastReviewed = System.currentTimeMillis()))
                                                wrong++
                                                if (index + 1 >= studyWords.size) screen = Screen.RESULTS else { index++; flipped = false }
                                            }
                                        }, modifier = Modifier.weight(1f).height(54.dp)
                                    ) { Icon(Icons.Default.Close, null); Text("Повторить") }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val streak = word.streak + 1
                                                dao.updateWord(word.copy(correctCount = word.correctCount + 1, streak = streak, learned = streak >= 3, lastReviewed = System.currentTimeMillis()))
                                                correct++
                                                if (index + 1 >= studyWords.size) screen = Screen.RESULTS else { index++; flipped = false }
                                            }
                                        }, modifier = Modifier.weight(1f).height(54.dp)
                                    ) { Icon(Icons.Default.Check, null); Text("Знаю") }
                                }
                            }
                            Text("Для свайпов сначала открой обратную сторону карточки.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Screen.RESULTS -> {
                        val total = correct + wrong
                        Text("Занятие завершено", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Card(shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Результат", style = MaterialTheme.typography.titleLarge)
                                Text("Правильно: $correct", style = MaterialTheme.typography.titleMedium)
                                Text("Ошибки: $wrong", style = MaterialTheme.typography.titleMedium)
                                Text("Точность: ${if (total > 0) correct * 100 / total else 0}%")
                                Text("Время: ${elapsedSeconds / 60} мин ${elapsedSeconds % 60} сек")
                            }
                        }
                        Button(onClick = {
                            studyWords = studyWords.filter { it.streak == 0 || it.wrongCount > it.correctCount }
                            index = 0; flipped = false; correct = 0; wrong = 0
                            if (studyWords.isNotEmpty()) { startedAt = System.currentTimeMillis(); elapsedSeconds = 0; screen = Screen.STUDY }
                            else message = "В списке повторения нет слов."
                        }, modifier = Modifier.fillMaxWidth()) { Text("Повторить сложные слова") }
                        OutlinedButton(onClick = { screen = Screen.HOME }, modifier = Modifier.fillMaxWidth()) { Text("Вернуться на главную") }
                    }
                }
                if (screen == Screen.HOME) {
                    Spacer(Modifier.weight(1f))
                    Text("Офлайн • Кыргызский → русский и обратно", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun parseWords(raw: String): List<Pair<String, String>> {
    return raw.lineSequence().mapNotNull { line ->
        val s = line.trim()
        if (s.isBlank()) return@mapNotNull null
        val separators = listOf(" — ", " – ", " - ", "\t", "—", "–", "-")
        val sep = separators.firstOrNull { s.contains(it) }
        val pair = if (sep != null) {
            val p = s.split(sep, limit = 2)
            if (p.size == 2) p[0].trim() to p[1].trim() else return@mapNotNull null
        } else {
            val p = Regex("\\s{2,}").split(s, limit = 2)
            if (p.size == 2) p[0].trim() to p[1].trim() else return@mapNotNull null
        }
        if (pair.first.isBlank() || pair.second.isBlank()) null else pair
    }.distinctBy { it.first.lowercase() + "|" + it.second.lowercase() }
}

private fun answer(
    correct: Boolean,
    word: Word,
    dao: com.example.kyrgyzflashcards.data.StudyDao,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit
) {
    scope.launch {
        if (correct) dao.updateWord(word.copy(correctCount = word.correctCount + 1, streak = word.streak + 1, learned = word.streak + 1 >= 3, lastReviewed = System.currentTimeMillis()))
        else dao.updateWord(word.copy(wrongCount = word.wrongCount + 1, streak = 0, learned = false, lastReviewed = System.currentTimeMillis()))
        onDone()
    }
}

private fun nextCard(
    total: Int, current: Int,
    setIndex: (Int) -> Unit,
    setFlipped: (Boolean) -> Unit,
    setScreen: (Screen) -> Unit
) {
    if (current + 1 >= total) setScreen(Screen.RESULTS)
    else { setIndex(current + 1); setFlipped(false) }
}
