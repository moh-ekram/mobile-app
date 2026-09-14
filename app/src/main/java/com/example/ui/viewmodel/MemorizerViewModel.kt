package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemorizerViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MemorizerRepository(application)
    private val profilePrefs = application.getSharedPreferences("memorizer_user_profile", android.content.Context.MODE_PRIVATE)

    // Auth State
    private val _currentUser = MutableStateFlow<UserSession?>(loadSavedUserSession())
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    private fun loadSavedUserSession(): UserSession {
        val savedName = profilePrefs.getString("display_name", "User #1235") ?: "User #1235"
        val savedAvatar = profilePrefs.getString("avatar_uri", null)
        val savedExam = profilePrefs.getString("target_exam", "GRE / IELTS") ?: "GRE / IELTS"
        val savedGoal = profilePrefs.getInt("daily_goal", 20)
        val savedBio = profilePrefs.getString("bio", "Aiming for GRE 330+ and IELTS 8.0") ?: "Aiming for GRE 330+ and IELTS 8.0"
        return UserSession(
            userId = "1235",
            email = "user1235@memorizer.app",
            displayName = savedName,
            isGuest = false,
            isGoogleUser = false,
            avatarUri = savedAvatar,
            targetExam = savedExam,
            dailyWordGoal = savedGoal,
            bio = savedBio
        )
    }

    fun reloadProfileFromStorage() {
        _currentUser.value = loadSavedUserSession()
    }

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

    fun handleBackPress(): Boolean {
        return navigateBack()
    }

    fun hasBackStack(): Boolean {
        return routeBackStack.isNotEmpty() || _currentRoute.value != "home" || activeArticle.value != null
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

    // Courses State & Persistent Active Course
    val allCourses: StateFlow<List<CourseEntity>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val coursePrefs = application.getSharedPreferences("memorizer_course_prefs", android.content.Context.MODE_PRIVATE)
    val activeCourseId = MutableStateFlow(coursePrefs.getString("saved_active_course_id", "") ?: "")

    // Google Drive Sync State & Safe Progress Preservation
    private val _isSyncingDrive = MutableStateFlow(false)
    val isSyncingDrive: StateFlow<Boolean> = _isSyncingDrive.asStateFlow()

    private val _driveSyncSummary = MutableStateFlow<DriveSyncSummary?>(null)
    val driveSyncSummary: StateFlow<DriveSyncSummary?> = _driveSyncSummary.asStateFlow()

    private val syncPrefs = application.getSharedPreferences("memorizer_sync_prefs", android.content.Context.MODE_PRIVATE)
    val driveSyncUrl = MutableStateFlow(
        syncPrefs.getString("saved_drive_sync_url", "https://drive.google.com/drive/folders/1OBqSlB21FD_-0tpRZE8H6R5VFzDkeX2n") ?: ""
    )

    fun setDriveSyncUrl(url: String) {
        driveSyncUrl.value = url
        syncPrefs.edit().putString("saved_drive_sync_url", url).apply()
    }

    fun clearDriveSyncSummary() {
        _driveSyncSummary.value = null
    }

    fun syncCoursesFromDrive(url: String = driveSyncUrl.value, preserveProgress: Boolean = true) {
        val targetUrl = url.trim()
        if (targetUrl.isBlank()) {
            _statusMessage.value = "Please enter a valid Google Drive folder or file link."
            return
        }
        setDriveSyncUrl(targetUrl)
        viewModelScope.launch {
            _isSyncingDrive.value = true
            _statusMessage.value = "Connecting to Google Drive and scanning files..."
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.syncCoursesFromDrive(targetUrl, preserveProgress, uid)
            _isSyncingDrive.value = false
            result.onSuccess { summary ->
                _driveSyncSummary.value = summary
                _statusMessage.value = "Sync complete: ${summary.totalCourses} course(s) processed (${summary.totalUpdatedWords} updated, ${summary.totalAddedWords} new words)!"
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                val savedId = coursePrefs.getString("saved_active_course_id", "") ?: ""
                if (courses.isNotEmpty()) {
                    if (savedId.isNotBlank() && courses.any { it.id == savedId }) {
                        selectCourse(savedId)
                    } else if (activeCourseId.value.isBlank() || courses.none { it.id == activeCourseId.value }) {
                        selectCourse(courses.first().id)
                    }
                }
            }.onFailure { err ->
                _statusMessage.value = "Sync failed: ${err.message}"
            }
        }
    }

    fun importMultipleCourseFiles(files: List<LocalCourseFileInput>, preserveProgress: Boolean = true) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            _isSyncingDrive.value = true
            _statusMessage.value = "Processing ${files.size} course file(s)..."
            val uid = _currentUser.value?.userId ?: "1235"
            val result = repository.importMultipleCourseFiles(files, preserveProgress, uid)
            _isSyncingDrive.value = false
            result.onSuccess { summary ->
                _driveSyncSummary.value = summary
                _statusMessage.value = "Batch import complete: ${summary.totalCourses} course(s) processed (${summary.totalUpdatedWords} updated, ${summary.totalAddedWords} new words)!"
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                val savedId = coursePrefs.getString("saved_active_course_id", "") ?: ""
                if (courses.isNotEmpty()) {
                    if (savedId.isNotBlank() && courses.any { it.id == savedId }) {
                        selectCourse(savedId)
                    } else if (activeCourseId.value.isBlank() || courses.none { it.id == activeCourseId.value }) {
                        selectCourse(courses.first().id)
                    }
                }
            }.onFailure { err ->
                _statusMessage.value = "Batch import failed: ${err.message}"
            }
        }
    }

    fun selectCourse(id: String) {
        if (id.isNotBlank()) {
            coursePrefs.edit().putString("saved_active_course_id", id).apply()
        }
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
        val filtered = when (cat) {
            "all" -> all
            "know" -> all.filter { it.status.equals("know", ignoreCase = true) }
            "confusion" -> all.filter { it.status.equals("confusion", ignoreCase = true) }
            "dont_know" -> all.filter { it.status.equals("dont_know", ignoreCase = true) }
            "unrated" -> all.filter { it.status.equals("unrated", ignoreCase = true) }
            else -> all
        }
        val candidates = if (filtered.isNotEmpty()) filtered else all
        if (candidates.isNotEmpty()) {
            val currentId = currentWidgetWord.value?.id
            val pool = if (candidates.size > 1) candidates.filter { it.id != currentId } else candidates
            currentWidgetWord.value = pool.randomOrNull() ?: candidates.first()
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
    val distinctGroups: StateFlow<List<String>> = activeCourseId.flatMapLatest { cId ->
        if (cId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else repository.getDistinctGroupsForCourse(cId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flashcard Screen Filters, Sorting & Navigation (Persisted across sessions)
    private val savedGroups = prefs.getStringSet("selected_card_groups", emptySet()) ?: emptySet()
    private val savedStatuses = prefs.getStringSet("selected_card_statuses", emptySet()) ?: emptySet()
    private val savedSortOrder = prefs.getString("card_sort_order", "default") ?: "default"
    val hasUserExplicitlySetStatus = MutableStateFlow(prefs.getBoolean("has_user_set_status", false))

    val selectedGroups = MutableStateFlow<Set<String>>(savedGroups) // empty = all groups
    val selectedStatuses = MutableStateFlow<Set<String>>(
        if (savedStatuses.isNotEmpty() && prefs.getBoolean("has_user_set_status", false)) savedStatuses
        else setOf("unrated")
    )
    val cardSortOrder = MutableStateFlow(savedSortOrder) // "default", "a_z", "z_a", "random"
    private val randomOrderMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val currentWordIndex = MutableStateFlow(0)

    // Legacy compatibility accessors
    val selectedGroup = MutableStateFlow<String?>(null)
    val selectedStatusFilter = MutableStateFlow("all")

    fun computeDefaultStatuses(
        words: List<VocabularyWordEntity> = allWords.value,
        courseId: String = activeCourseId.value,
        groups: Set<String> = selectedGroups.value
    ): Set<String> {
        val pool = words.filter { w ->
            (courseId.isBlank() || w.courseId == courseId) &&
            (groups.isEmpty() || groups.contains(w.group))
        }
        if (pool.isEmpty()) return setOf("unrated")

        val hasUnrated = pool.any { it.status.equals("unrated", ignoreCase = true) }
        if (hasUnrated) {
            return setOf("unrated")
        }

        val hasDontKnow = pool.any { it.status.equals("dont_know", ignoreCase = true) }
        val hasConfusion = pool.any { it.status.equals("confusion", ignoreCase = true) }
        if (hasDontKnow || hasConfusion) {
            return setOf("dont_know", "confusion")
        }

        return setOf("know")
    }

    fun applyDefaultStatusFilter(
        words: List<VocabularyWordEntity> = allWords.value,
        courseId: String = activeCourseId.value,
        groups: Set<String> = selectedGroups.value
    ) {
        hasUserExplicitlySetStatus.value = false
        prefs.edit().putBoolean("has_user_set_status", false).apply()
        val computed = computeDefaultStatuses(words, courseId, groups)
        selectedStatuses.value = computed
        persistFilters()
    }

    private fun persistFilters() {
        prefs.edit()
            .putStringSet("selected_card_groups", selectedGroups.value)
            .putStringSet("selected_card_statuses", selectedStatuses.value)
            .putString("card_sort_order", cardSortOrder.value)
            .putBoolean("has_user_set_status", hasUserExplicitlySetStatus.value)
            .apply()
    }

    fun toggleGroup(group: String) {
        val current = selectedGroups.value
        val newGroups = if (current.contains(group)) current - group else current + group
        selectedGroups.value = newGroups
        selectedGroup.value = if (newGroups.size == 1) newGroups.first() else null
        currentWordIndex.value = 0
        if (!hasUserExplicitlySetStatus.value) {
            selectedStatuses.value = computeDefaultStatuses(allWords.value, activeCourseId.value, newGroups)
        }
        persistFilters()
    }

    fun clearGroups() {
        selectedGroups.value = emptySet()
        selectedGroup.value = null
        currentWordIndex.value = 0
        if (!hasUserExplicitlySetStatus.value) {
            selectedStatuses.value = computeDefaultStatuses(allWords.value, activeCourseId.value, emptySet())
        }
        persistFilters()
    }

    fun selectGroup(group: String?) {
        val newGroups = if (group == null) emptySet() else setOf(group)
        selectedGroups.value = newGroups
        selectedGroup.value = group
        currentWordIndex.value = 0
        if (!hasUserExplicitlySetStatus.value) {
            selectedStatuses.value = computeDefaultStatuses(allWords.value, activeCourseId.value, newGroups)
        }
        persistFilters()
    }

    fun toggleStatus(status: String) {
        hasUserExplicitlySetStatus.value = true
        val current = selectedStatuses.value
        selectedStatuses.value = if (current.contains(status)) current - status else current + status
        currentWordIndex.value = 0
        persistFilters()
    }

    fun clearStatuses() {
        hasUserExplicitlySetStatus.value = true
        selectedStatuses.value = emptySet()
        currentWordIndex.value = 0
        persistFilters()
    }

    fun selectStatusFilter(status: String) {
        hasUserExplicitlySetStatus.value = true
        selectedStatuses.value = if (status == "all") emptySet() else setOf(status)
        selectedStatusFilter.value = status
        currentWordIndex.value = 0
        persistFilters()
    }

    private fun regenerateRandomOrder() {
        val words = allWords.value
        val shuffledIds = words.map { it.id }.shuffled()
        randomOrderMap.value = shuffledIds.mapIndexed { index, id -> id to index }.toMap()
    }

    fun setCardSortOrder(order: String) {
        if (order == "random" && randomOrderMap.value.isEmpty()) {
            regenerateRandomOrder()
        }
        cardSortOrder.value = order
        currentWordIndex.value = 0
        persistFilters()
    }

    fun reshuffleCards() {
        regenerateRandomOrder()
        currentWordIndex.value = 0
    }

    fun resetAllCardFilters() {
        hasUserExplicitlySetStatus.value = false
        selectedGroups.value = emptySet()
        selectedGroup.value = null
        cardSortOrder.value = "default"
        selectedStatuses.value = computeDefaultStatuses(allWords.value, activeCourseId.value, emptySet())
        currentWordIndex.value = 0
        persistFilters()
    }

    val filteredWords: StateFlow<List<VocabularyWordEntity>> = combine(
        combine(allWords, activeCourseId, selectedGroups) { words, courseId, groups ->
            Triple(words, courseId, groups)
        },
        selectedStatuses,
        cardSortOrder,
        randomOrderMap
    ) { (words, courseId, groups), statuses, sortOrder, orderMap ->
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
                    filtered.sortedBy { orderMap[it.id] ?: (it.id.hashCode() and 0x7FFFFFFF) }
                }
                else -> filtered // Default course/insertion order
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allCourses.collect { list ->
                if (list.isEmpty()) {
                    activeCourseId.value = ""
                    return@collect
                }
                val savedId = coursePrefs.getString("saved_active_course_id", "") ?: ""
                val current = activeCourseId.value
                if (savedId.isNotBlank() && list.any { it.id == savedId }) {
                    if (current != savedId) {
                        activeCourseId.value = savedId
                    }
                } else if (current.isNotBlank() && list.any { it.id == current }) {
                    coursePrefs.edit().putString("saved_active_course_id", current).apply()
                } else {
                    val fallbackId = list.first().id
                    activeCourseId.value = fallbackId
                    coursePrefs.edit().putString("saved_active_course_id", fallbackId).apply()
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
                if (words.isNotEmpty()) {
                    if (currentWidgetWord.value == null) {
                        cycleNextWidgetWord()
                    }
                    if (!hasUserExplicitlySetStatus.value) {
                        val computed = computeDefaultStatuses(words, activeCourseId.value, selectedGroups.value)
                        if (selectedStatuses.value != computed) {
                            selectedStatuses.value = computed
                        }
                    }
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
        var finalAvatarUri = avatarUri
        // If avatarUri is an external content:// or file URI, copy it to app internal storage permanently
        if (!avatarUri.isNullOrBlank() && !avatarUri.contains("profile_avatar.jpg")) {
            try {
                val inputUri = Uri.parse(avatarUri)
                val targetFile = java.io.File(getApplication<Application>().filesDir, "profile_avatar.jpg")
                getApplication<Application>().contentResolver.openInputStream(inputUri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    finalAvatarUri = Uri.fromFile(targetFile).toString()
                }
            } catch (e: Exception) {
                android.util.Log.e("MemorizerVM", "Error saving profile avatar: ${e.message}")
            }
        }

        profilePrefs.edit()
            .putString("display_name", displayName)
            .putString("avatar_uri", finalAvatarUri)
            .putString("target_exam", targetExam)
            .putInt("daily_goal", dailyGoal)
            .putString("bio", bio)
            .apply()

        val current = _currentUser.value ?: UserSession(userId = "1235", displayName = displayName)
        _currentUser.value = current.copy(
            displayName = displayName,
            avatarUri = finalAvatarUri,
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

    // Flashcard Actions: Smooth transition without double-skipping
    fun rateWord(wordId: String, status: String) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            val currentList = filteredWords.value
            if (currentList.isEmpty()) return@launch

            val currentIndex = currentWordIndex.value.coerceIn(0, currentList.size - 1)

            val currentActiveStatuses = selectedStatuses.value
            val willLeaveFilter = currentActiveStatuses.isNotEmpty() && !currentActiveStatuses.contains(status)

            val nextCardId = if (currentList.size > 1) {
                if (currentIndex < currentList.size - 1) {
                    currentList[currentIndex + 1].id
                } else {
                    currentList[0].id // wrap around to first card
                }
            } else null

            // Update status in repository
            repository.updateWordStatus(wordId, status, uid)

            if (!hasUserExplicitlySetStatus.value) {
                val updatedWords = allWords.value.map { if (it.id == wordId) it.copy(status = status) else it }
                val currentPool = updatedWords.filter { w ->
                    (activeCourseId.value.isBlank() || w.courseId == activeCourseId.value) &&
                    (selectedGroups.value.isEmpty() || selectedGroups.value.contains(w.group))
                }
                val matchingCount = currentPool.count { selectedStatuses.value.contains(it.status) }
                if (matchingCount == 0) {
                    val fallback = computeDefaultStatuses(updatedWords, activeCourseId.value, selectedGroups.value)
                    selectedStatuses.value = fallback
                    currentWordIndex.value = 0
                    return@launch
                }
            }

            if (nextCardId != null) {
                if (willLeaveFilter) {
                    // Since the current card is removed from the filtered list,
                    // the next card naturally slides into currentIndex.
                    val newSize = currentList.size - 1
                    if (currentIndex >= newSize) {
                        currentWordIndex.value = 0
                    } else {
                        currentWordIndex.value = currentIndex
                    }
                } else {
                    // The card remains in the list, advance forward by 1
                    currentWordIndex.value = (currentIndex + 1) % currentList.size
                }
            } else {
                currentWordIndex.value = 0
            }
        }
    }

    fun reportWord(wordId: String, isReported: Boolean = true, reason: String? = null) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.reportWord(wordId, isReported, reason, uid)
            _statusMessage.value = if (isReported) "Word reported. Review it in Control panel." else "Report cleared."
        }
    }

    fun updateWord(word: VocabularyWordEntity) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.updateWord(word, uid)
            _statusMessage.value = "Word '${word.word}' updated!"
        }
    }

    fun updateCourse(courseId: String, newTitle: String, newDescription: String? = null) {
        viewModelScope.launch {
            repository.updateCourseTitle(courseId, newTitle.trim())
            _statusMessage.value = "Course updated!"
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
                reloadProfileFromStorage()
                _statusMessage.value = "Successfully restored $count items from linked Drive folder!"
                repository.refreshProgressAndSync(uid)
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                val savedId = coursePrefs.getString("saved_active_course_id", "") ?: ""
                if (courses.isNotEmpty()) {
                    if (savedId.isNotBlank() && courses.any { it.id == savedId }) {
                        selectCourse(savedId)
                    } else if (activeCourseId.value.isBlank() || courses.none { it.id == activeCourseId.value }) {
                        selectCourse(courses.first().id)
                    }
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
                reloadProfileFromStorage()
                _statusMessage.value = "Successfully restored $count items from Google Drive / Storage!"
                repository.refreshProgressAndSync(uid)
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                val savedId = coursePrefs.getString("saved_active_course_id", "") ?: ""
                if (courses.isNotEmpty()) {
                    if (savedId.isNotBlank() && courses.any { it.id == savedId }) {
                        selectCourse(savedId)
                    } else if (activeCourseId.value.isBlank() || courses.none { it.id == activeCourseId.value }) {
                        selectCourse(courses.first().id)
                    }
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
                reloadProfileFromStorage()
                _statusMessage.value = "Successfully restored $count vocabulary words across courses!"
                repository.refreshProgressAndSync(uid)
                val courses = repository.allCourses.firstOrNull() ?: emptyList()
                val savedId = coursePrefs.getString("saved_active_course_id", "") ?: ""
                if (courses.isNotEmpty()) {
                    if (savedId.isNotBlank() && courses.any { it.id == savedId }) {
                        selectCourse(savedId)
                    } else if (activeCourseId.value.isBlank() || courses.none { it.id == activeCourseId.value }) {
                        selectCourse(courses.first().id)
                    }
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

    fun deleteGamesBySection(section: String) {
        viewModelScope.launch {
            repository.deleteGamesBySection(section)
            _statusMessage.value = "Game items deleted"
        }
    }

    fun clearAllGames() {
        viewModelScope.launch {
            repository.clearAllGames()
            _statusMessage.value = "All game items cleared"
        }
    }

    fun recordGameAnswer(questionId: String, isCorrect: Boolean) {
        viewModelScope.launch {
            repository.recordGameAnswer(questionId, isCorrect)
        }
    }

    fun recordWordQuizAnswer(wordId: String, isCorrect: Boolean) {
        viewModelScope.launch {
            repository.recordWordQuizAnswer(wordId, isCorrect)
        }
    }

    fun deleteQuestionBankItem(id: String) {
        viewModelScope.launch {
            repository.deleteQuestionBankItem(id)
            _statusMessage.value = "Question bank item removed"
        }
    }

    fun clearAllQuestionBank() {
        viewModelScope.launch {
            repository.clearAllQuestionBank()
            _statusMessage.value = "Question bank cleared"
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
