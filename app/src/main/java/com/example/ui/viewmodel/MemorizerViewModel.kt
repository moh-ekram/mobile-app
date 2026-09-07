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

    fun setRoute(route: String) {
        _currentRoute.value = route
    }

    // Courses State
    val allCourses: StateFlow<List<CourseEntity>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCourseId = MutableStateFlow("course_default")

    fun selectCourse(id: String) {
        activeCourseId.value = id
        currentWordIndex.value = 0
    }

    fun createCourse(title: String, description: String? = null) {
        viewModelScope.launch {
            val course = repository.createCourse(title, description)
            activeCourseId.value = course.id
            _statusMessage.value = "Course '${course.title}' created & selected!"
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.deleteCourse(courseId, uid)
            if (activeCourseId.value == courseId) {
                activeCourseId.value = "course_default"
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

    fun saveArticle(title: String, content: String) {
        viewModelScope.launch {
            val saved = repository.saveArticle(title, content, activeCourseId.value)
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

    val distinctGroups: StateFlow<List<Int>> = repository.distinctGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(1, 2, 3))

    // Flashcard Screen Filters & Index
    val selectedGroup = MutableStateFlow<Int?>(null) // null = all groups
    val selectedStatusFilter = MutableStateFlow("all") // "all", "unrated", "dont_know", "confusion", "know"
    val currentWordIndex = MutableStateFlow(0)

    val filteredWords: StateFlow<List<VocabularyWordEntity>> = combine(
        allWords,
        activeCourseId,
        selectedGroup,
        selectedStatusFilter
    ) { words, courseId, group, status ->
        words.filter { w ->
            (courseId == "all" || w.courseId == courseId || courseId.isBlank()) &&
            (group == null || w.group == group) &&
            (status == "all" || w.status == status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Flashcard Actions
    fun rateWord(wordId: String, status: String) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.updateWordStatus(wordId, status, uid)
            // advance to next word automatically
            val currentList = filteredWords.value
            if (currentList.isNotEmpty() && currentWordIndex.value < currentList.size - 1) {
                currentWordIndex.value += 1
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
                _statusMessage.value = "Backup failed: ${err.message}"
            }
        }
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
            val result = repository.backupManager.restoreFromFileContent(content, isJson)
            result.onSuccess { count ->
                _statusMessage.value = "Successfully restored $count vocabulary words!"
                val uid = _currentUser.value?.userId ?: "1235"
                repository.refreshProgressAndSync(uid)
            }.onFailure { err ->
                _statusMessage.value = "Restore failed: ${err.message}"
            }
        }
    }

    fun setCustomBackupTreeUri(uriString: String) {
        repository.backupManager.setCustomTreeUri(uriString)
        _statusMessage.value = "Custom backup folder configured!"
    }

    // Admin Panel Uploads
    fun importCourseFile(content: String, isJson: Boolean, courseId: String = activeCourseId.value) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.importCourseFile(content, isJson, courseId, uid)
            result.onSuccess { count ->
                _statusMessage.value = "Imported $count words from course file!"
            }.onFailure { err ->
                _statusMessage.value = "Import failed: ${err.message}"
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
