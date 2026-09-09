package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.MemorizerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemorizerViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MemorizerRepository(application)

    // Auth State
    private val _currentUser = MutableStateFlow<UserSession?>(
        // Start with pre-authenticated default user for instant access or seamless entry
        UserSession(
            userId = "1235",
            email = "user1235@memorizer.app",
            displayName = "User #1235",
            isGuest = false,
            isGoogleUser = false
        )
    )
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    // Navigation State: "home", "flashcard", "games", "admin", "profile", "article_reader"
    private val _currentRoute = MutableStateFlow("home")
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()
    private val routeBackStack = mutableListOf<String>()

    fun setRoute(route: String) {
        if (_currentRoute.value != route) {
            if (routeBackStack.isEmpty() || routeBackStack.last() != _currentRoute.value) {
                routeBackStack.add(_currentRoute.value)
            }
            _currentRoute.value = route
        }
    }

    fun navigateBack(): Boolean {
        if (activeArticle.value != null) {
            activeArticle.value = null
            return true
        }
        while (routeBackStack.isNotEmpty()) {
            val prev = routeBackStack.removeAt(routeBackStack.lastIndex)
            if (prev != _currentRoute.value) {
                _currentRoute.value = prev
                return true
            }
        }
        if (_currentRoute.value != "home") {
            _currentRoute.value = "home"
            return true
        }
        return false
    }

    // Courses State
    val allCourses: StateFlow<List<CourseEntity>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCourseId = MutableStateFlow("")

    fun selectCourse(id: String) {
        activeCourseId.value = id
        currentWordIndex.value = 0
    }

    fun createCourse(title: String, description: String? = null, initialContent: String? = null, isJson: Boolean = false) {
        viewModelScope.launch {
            val course = repository.createCourse(title, description)
            activeCourseId.value = course.id
            _statusMessage.value = "Course '${course.title}' created & selected!"
            if (!initialContent.isNullOrBlank()) {
                val uid = _currentUser.value?.userId ?: "1235"
                val res = repository.importCourseFile(initialContent, isJson, course.id, uid)
                res.onSuccess { count ->
                    _statusMessage.value = "Course '${course.title}' created with $count words!"
                }
            }
        }
    }

    fun updateCourseTitle(courseId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateCourseTitle(courseId, newTitle)
            _statusMessage.value = "Course renamed to '$newTitle'"
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.deleteCourse(courseId, uid)
            if (activeCourseId.value == courseId) {
                val remaining = allCourses.value.filter { it.id != courseId }
                activeCourseId.value = remaining.firstOrNull()?.id ?: ""
            }
            _statusMessage.value = "Course removed"
        }
    }

    // Articles State (Read Article Feature)
    val allArticles: StateFlow<List<ArticleEntity>> = repository.allArticles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeArticle = MutableStateFlow<ArticleEntity?>(null)

    fun selectArticle(article: ArticleEntity?) {
        activeArticle.value = article
    }

    private val prefs = application.getSharedPreferences("memorizer_prefs", android.content.Context.MODE_PRIVATE)

    // Dark / Night Theme State
    val isDarkTheme = MutableStateFlow(prefs.getBoolean("is_dark_theme", false))

    fun toggleDarkTheme() {
        val next = !isDarkTheme.value
        isDarkTheme.value = next
        prefs.edit().putBoolean("is_dark_theme", next).apply()
    }

    // Flashcard Flip Animation Setting (Toggle in Profile: On/Off)
    val isFlipAnimationEnabled = MutableStateFlow(prefs.getBoolean("is_flip_animation_enabled", true))

    fun setFlipAnimationEnabled(enabled: Boolean) {
        isFlipAnimationEnabled.value = enabled
        prefs.edit().putBoolean("is_flip_animation_enabled", enabled).apply()
        _statusMessage.value = if (enabled) "Card flip animation enabled" else "Card flip animation disabled"
    }

    // Flashcard Focus Mode (Hides bottom nav, enlarges card, positions tag buttons at bottom)
    val isFocusMode = MutableStateFlow(false)

    fun toggleFocusMode() {
        isFocusMode.value = !isFocusMode.value
    }

    fun setFocusMode(enabled: Boolean) {
        isFocusMode.value = enabled
    }

    // Home Widget State
    val widgetCategory = MutableStateFlow("all") // "all", "know", "confusion", "dont_know", "unrated"
    val currentWidgetWord = MutableStateFlow<VocabularyWordEntity?>(null)

    fun setWidgetCategory(category: String) {
        widgetCategory.value = category
        cycleNextWidgetWord()
    }

    fun cycleNextWidgetWord() {
        val cat = widgetCategory.value
        val all = allWords.value
        val candidates = when (cat) {
            "all" -> all
            "know" -> all.filter { it.status == "know" }
            "confusion" -> all.filter { it.status == "confusion" }
            "dont_know" -> all.filter { it.status == "dont_know" }
            "unrated" -> all.filter { it.status == "unrated" }
            else -> all
        }
        if (candidates.isNotEmpty()) {
            val currentId = currentWidgetWord.value?.id
            val pool = if (candidates.size > 1) candidates.filter { it.id != currentId } else candidates
            currentWidgetWord.value = pool.randomOrNull() ?: candidates.first()
        } else {
            currentWidgetWord.value = null
        }
    }

    fun saveArticle(title: String, content: String, author: String = "Unknown Author", id: String? = null) {
        viewModelScope.launch {
            val saved = repository.saveArticle(title, content, author, activeCourseId.value, id)
            activeArticle.value = saved
            _statusMessage.value = "Article '${saved.title}' saved locally!"
        }
    }

    fun deleteArticle(id: String) {
        viewModelScope.launch {
            repository.deleteArticle(id)
            if (activeArticle.value?.id == id) {
                activeArticle.value = null
            }
            _statusMessage.value = "Article removed"
        }
    }

    // Custom Backup Tree URI state
    val customBackupTreeUri = MutableStateFlow<String?>(repository.backupManager.getCustomTreeUri())

    fun setCustomBackupTreeUri(uri: Uri) {
        val uriStr = uri.toString()
        repository.backupManager.setCustomTreeUri(uriStr)
        customBackupTreeUri.value = uriStr
        _statusMessage.value = "Custom backup directory configured!"
    }

    fun setActiveCourse(id: String) = selectCourse(id)
    fun setActiveArticle(article: ArticleEntity?) = selectArticle(article)

    // Vocabulary State
    val allWords: StateFlow<List<VocabularyWordEntity>> = repository.allWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val distinctGroups: StateFlow<List<Int>> = activeCourseId.flatMapLatest { cId ->
        if (cId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else repository.getDistinctGroupsForCourse(cId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flashcard Screen Filters, Sorting & Navigation (Persisted across sessions)
    private val savedGroups = prefs.getStringSet("selected_card_groups", emptySet())
        ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    private val savedStatuses = prefs.getStringSet("selected_card_statuses", emptySet()) ?: emptySet()
    private val savedSortOrder = prefs.getString("card_sort_order", "default") ?: "default"

    val selectedGroups = MutableStateFlow<Set<Int>>(savedGroups) // empty = all groups
    val selectedStatuses = MutableStateFlow<Set<String>>(savedStatuses) // empty = all statuses
    val cardSortOrder = MutableStateFlow(savedSortOrder) // "default", "a_z", "z_a", "random"
    private val randomSeed = MutableStateFlow(System.currentTimeMillis())
    val currentWordIndex = MutableStateFlow(0)

    // Legacy compatibility accessors
    val selectedGroup = MutableStateFlow<Int?>(null)
    val selectedStatusFilter = MutableStateFlow("all")

    private fun persistFilters() {
        prefs.edit()
            .putStringSet("selected_card_groups", selectedGroups.value.map { it.toString() }.toSet())
            .putStringSet("selected_card_statuses", selectedStatuses.value)
            .putString("card_sort_order", cardSortOrder.value)
            .apply()
    }

    fun toggleGroup(group: Int) {
        val current = selectedGroups.value
        selectedGroups.value = if (current.contains(group)) current - group else current + group
        currentWordIndex.value = 0
        persistFilters()
    }

    fun clearGroups() {
        selectedGroups.value = emptySet()
        currentWordIndex.value = 0
        persistFilters()
    }

    fun selectGroup(group: Int?) {
        selectedGroups.value = if (group == null) emptySet() else setOf(group)
        selectedGroup.value = group
        currentWordIndex.value = 0
        persistFilters()
    }

    fun toggleStatus(status: String) {
        val current = selectedStatuses.value
        selectedStatuses.value = if (current.contains(status)) current - status else current + status
        currentWordIndex.value = 0
        persistFilters()
    }

    fun clearStatuses() {
        selectedStatuses.value = emptySet()
        currentWordIndex.value = 0
        persistFilters()
    }

    fun selectStatusFilter(status: String) {
        selectedStatuses.value = if (status == "all") emptySet() else setOf(status)
        selectedStatusFilter.value = status
        currentWordIndex.value = 0
        persistFilters()
    }

    fun setCardSortOrder(order: String) {
        cardSortOrder.value = order
        currentWordIndex.value = 0
        persistFilters()
    }

    fun reshuffleCards() {
        randomSeed.value = System.currentTimeMillis()
        currentWordIndex.value = 0
    }

    fun resetAllCardFilters() {
        selectedGroups.value = emptySet()
        selectedStatuses.value = emptySet()
        cardSortOrder.value = "default"
        currentWordIndex.value = 0
        persistFilters()
    }

    val filteredWords: StateFlow<List<VocabularyWordEntity>> = combine(
        combine(allWords, activeCourseId, selectedGroups) { words, courseId, groups ->
            Triple(words, courseId, groups)
        },
        selectedStatuses,
        cardSortOrder,
        randomSeed
    ) { (words, courseId, groups), statuses, sortOrder, seed ->
        if (courseId.isBlank()) {
            // Without active course, show no flashcard data
            emptyList()
        } else {
            val filtered = words.filter { w ->
                w.courseId == courseId &&
                (groups.isEmpty() || groups.contains(w.group)) &&
                (statuses.isEmpty() || statuses.contains(w.status))
            }

            when (sortOrder) {
                "a_z" -> filtered.sortedBy { it.word.lowercase() }
                "z_a" -> filtered.sortedByDescending { it.word.lowercase() }
                "random" -> {
                    val rng = java.util.Random(seed)
                    filtered.shuffled(rng)
                }
                else -> filtered // Default course/insertion order
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allCourses.collect { list ->
                if (activeCourseId.value.isBlank() && list.isNotEmpty()) {
                    activeCourseId.value = list.first().id
                } else if (list.isNotEmpty() && list.none { it.id == activeCourseId.value }) {
                    activeCourseId.value = list.first().id
                } else if (list.isEmpty()) {
                    activeCourseId.value = ""
                }
            }
        }
        viewModelScope.launch {
            filteredWords.collect { list ->
                if (list.isEmpty()) {
                    currentWordIndex.value = 0
                } else if (currentWordIndex.value >= list.size) {
                    currentWordIndex.value = list.size - 1
                }
            }
        }
        viewModelScope.launch {
            allWords.collect { words ->
                if (currentWidgetWord.value == null && words.isNotEmpty()) {
                    cycleNextWidgetWord()
                }
            }
        }
    }

    // Games State
    val allGames: StateFlow<List<GamePracticeEntity>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Question Bank State
    val allQuestions: StateFlow<List<QuestionBankEntity>> = repository.allQuestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User Progress State
    val userProgress: StateFlow<UserProgressEntity?> = _currentUser.flatMapLatest { user ->
        val id = user?.userId ?: "1235"
        repository.getProgress(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Notification / Toast Message
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showMessage(msg: String) {
        _statusMessage.value = msg
    }

    // Profile Management
    fun updateProfile(displayName: String, avatarUri: String?, targetExam: String, dailyGoal: Int, bio: String) {
        val current = _currentUser.value ?: UserSession(userId = "1235", displayName = displayName)
        _currentUser.value = current.copy(
            displayName = displayName,
            avatarUri = avatarUri,
            targetExam = targetExam,
            dailyWordGoal = dailyGoal,
            bio = bio
        )
        _statusMessage.value = "Profile updated successfully!"
    }

    // Auth Actions
    fun loginWithCredentials(id: String, pass: String) {
        viewModelScope.launch {
            val result = repository.supabaseService.loginWithCredentials(id, pass)
            result.onSuccess { session ->
                _currentUser.value = session
                _statusMessage.value = "Welcome back, ${session.displayName}!"
                repository.refreshProgressAndSync(session.userId)
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Authentication failed"
            }
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            val result = repository.supabaseService.loginWithGoogle("Google User")
            result.onSuccess { session ->
                _currentUser.value = session
                _statusMessage.value = "Signed in with Google"
                repository.refreshProgressAndSync(session.userId)
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _statusMessage.value = "Signed out"
    }

    // Flashcard Actions: Fix skipping bug by pre-targeting exact next word
    fun rateWord(wordId: String, status: String) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val currentList = filteredWords.value
            val currentIndex = currentWordIndex.value.coerceIn(0, (currentList.size - 1).coerceAtLeast(0))

            // Identify the exact card that should follow the current card
            val nextCardId = if (currentList.size > 1) {
                if (currentIndex < currentList.size - 1) {
                    currentList[currentIndex + 1].id
                } else {
                    currentList[0].id // Wrap to start if at end
                }
            } else null

            // Update database
            repository.updateWordStatus(wordId, status, uid)

            // Safely navigate to nextCardId in the updated list
            if (nextCardId != null) {
                val updatedList = filteredWords.value
                val targetIdx = updatedList.indexOfFirst { it.id == nextCardId }
                if (targetIdx != -1) {
                    currentWordIndex.value = targetIdx
                } else {
                    currentWordIndex.value = currentIndex.coerceIn(0, (updatedList.size - 1).coerceAtLeast(0))
                }
            } else {
                currentWordIndex.value = 0
            }
        }
    }

    fun nextWord() {
        val currentList = filteredWords.value
        if (currentList.isNotEmpty()) {
            if (currentWordIndex.value < currentList.size - 1) {
                currentWordIndex.value += 1
            } else {
                currentWordIndex.value = 0 // loop
            }
        }
    }

    fun previousWord() {
        val currentList = filteredWords.value
        if (currentList.isNotEmpty()) {
            if (currentWordIndex.value > 0) {
                currentWordIndex.value -= 1
            } else {
                currentWordIndex.value = currentList.size - 1
            }
        }
    }

    // Backup & Sync
    fun triggerManualBackup() {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.backupManager.saveBackupFiles(uid)
            result.onSuccess {
                _statusMessage.value = "Backup files successfully updated in: ${repository.backupManager.getBackupPathString()}"
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Backup failed"
            }
        }
    }

    fun backupDirectlyToLinkedFolder() {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.backupManager.backupDirectlyToLinkedFolder(uid)
            result.onSuccess { msg ->
                _statusMessage.value = msg
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Drive backup failed"
            }
        }
    }

    fun restoreDirectlyFromLinkedFolder() {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.backupManager.restoreDirectlyFromLinkedFolder(uid)
            result.onSuccess { count ->
                _statusMessage.value = "Successfully restored $count items from linked Drive folder!"
                repository.refreshProgressAndSync(uid)
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                if (activeCourseId.value.isBlank() && courses.isNotEmpty()) {
                    activeCourseId.value = courses.first().id
                }
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Drive restore failed"
            }
        }
    }

    fun refreshWidget() {
        viewModelScope.launch {
            try {
                com.example.widget.DailyVocabWidgetProvider.updateAllWidgets(getApplication())
                _statusMessage.value = "Home screen widget refreshed!"
            } catch (_: Exception) {}
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.backupManager.exportBackupToUri(uri, uid)
            result.onSuccess { msg ->
                _statusMessage.value = "Backup successfully saved to Google Drive / Storage!"
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Export failed"
            }
        }
    }

    fun restoreFromUri(uri: Uri) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.backupManager.restoreFromUri(uri, uid)
            result.onSuccess { count ->
                _statusMessage.value = "Successfully restored $count items from Google Drive / Storage!"
                repository.refreshProgressAndSync(uid)
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                if (activeCourseId.value.isBlank() && courses.isNotEmpty()) {
                    activeCourseId.value = courses.first().id
                }
            }.onFailure { err ->
                _statusMessage.value = "Restore failed: ${err.message}"
            }
        }
    }

    suspend fun getBackupJsonText(): String {
        val uid = _currentUser.value?.userId ?: "1235"
        return repository.backupManager.generateBackupJsonString(uid)
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val progress = repository.getProgress(uid).firstOrNull()
            if (progress != null) {
                val res = repository.supabaseService.syncProgressRecord(progress)
                _statusMessage.value = res.getOrDefault("Sync completed")
            }
        }
    }

    fun restoreBackupContent(content: String, isJson: Boolean) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.backupManager.restoreFromFileContent(content, isJson, uid)
            result.onSuccess { count ->
                _statusMessage.value = "Successfully restored $count vocabulary words across courses!"
                repository.refreshProgressAndSync(uid)
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                if (activeCourseId.value.isBlank() && courses.isNotEmpty()) {
                    activeCourseId.value = courses.first().id
                }
            }.onFailure { err ->
                _statusMessage.value = "Restore failed: ${err.message}"
            }
        }
    }

    // Admin Panel Uploads
    fun importCourseFile(content: String, isJson: Boolean, courseId: String = activeCourseId.value, customTitle: String? = null) {
        viewModelScope.launch {
            if (!customTitle.isNullOrBlank() && courseId.isNotBlank()) {
                repository.updateCourseTitle(courseId, customTitle.trim())
            }
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.importCourseFile(content, isJson, courseId, uid)
            result.onSuccess { count ->
                _statusMessage.value = "Imported $count words into course!"
            }.onFailure { err ->
                _statusMessage.value = "Import failed: ${err.message}"
            }
        }
    }

    fun importGameItems(items: List<GamePracticeEntity>) {
        viewModelScope.launch {
            val result = repository.importGameItems(items)
            result.onSuccess { count ->
                _statusMessage.value = "Imported $count game questions successfully!"
            }.onFailure { err ->
                _statusMessage.value = "Game import failed: ${err.message}"
            }
        }
    }

    fun importGameFile(content: String, sheetType: String) {
        viewModelScope.launch {
            val result = repository.importGameFile(content, sheetType)
            result.onSuccess { count ->
                _statusMessage.value = "Imported $count game questions for $sheetType!"
            }.onFailure { err ->
                _statusMessage.value = "Game import failed: ${err.message}"
            }
        }
    }

    fun importQuestionBankFile(content: String) {
        viewModelScope.launch {
            val result = repository.importQuestionBankFile(content)
            result.onSuccess { count ->
                _statusMessage.value = "Imported $count Question Bank items!"
            }.onFailure { err ->
                _statusMessage.value = "QB import failed: ${err.message}"
            }
        }
    }

    fun resetToSample() {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.resetAllData(uid)
            _statusMessage.value = "Loaded default sample course, games, and question bank!"
        }
    }

    fun addCustomWord(word: VocabularyWordEntity) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.addWord(word, uid)
            _statusMessage.value = "Word '${word.word}' added!"
        }
    }

    fun deleteWord(id: String) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.deleteWord(id, uid)
            _statusMessage.value = "Word removed"
        }
    }

    fun deleteGameItem(id: String) {
        viewModelScope.launch {
            repository.deleteGameItem(id)
            _statusMessage.value = "Game item removed"
        }
    }

    fun deleteQuestionBankItem(id: String) {
        viewModelScope.launch {
            repository.deleteQuestionBankItem(id)
            _statusMessage.value = "Question bank item removed"
        }
    }

    fun recordQuizCompletion(score: Int, total: Int) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.recordQuizResult(score, total, uid)
            _statusMessage.value = "Practice completed! Score: $score / $total"
        }
    }
}
