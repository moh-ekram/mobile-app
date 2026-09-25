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

    private val prefs = application.getSharedPreferences("memorizer_prefs", android.content.Context.MODE_PRIVATE)
    private val profilePrefs = application.getSharedPreferences("memorizer_user_profile", android.content.Context.MODE_PRIVATE)
    val repository = MemorizerRepository(application)

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

    private fun loadSavedSelectedCourseIds(): Set<String>? {
        val csv = coursePrefs.getString("selected_course_ids_csv", null)
        if (csv != null) {
            return if (csv.isBlank()) emptySet() else csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        val set = coursePrefs.getStringSet("selected_course_ids", null)
        return set?.toSet()
    }

    private fun saveSelectedCoursesToPrefs(ids: Set<String>) {
        coursePrefs.edit()
            .putString("selected_course_ids_csv", ids.joinToString(","))
            .putStringSet("selected_course_ids", HashSet(ids))
            .putBoolean("has_configured_course_selection", true)
            .commit()
    }

    // Selected Courses State (Multi-Course Selection for Study & Backup)
    private val _selectedCourseIds = MutableStateFlow<Set<String>>(
        loadSavedSelectedCourseIds() ?: emptySet()
    )
    val selectedCourseIds: StateFlow<Set<String>> = _selectedCourseIds.asStateFlow()

    init {
        viewModelScope.launch {
            allCourses.collect { courses ->
                if (courses.isNotEmpty()) {
                    val hasConfigured = coursePrefs.getBoolean("has_configured_course_selection", false)
                    if (!hasConfigured) {
                        // First run initialization: select all available courses by default
                        val allIds = courses.map { it.id }.toSet()
                        _selectedCourseIds.value = allIds
                        saveSelectedCoursesToPrefs(allIds)
                        com.example.widget.DailyVocabWidgetProvider.saveSelectedCourseIds(getApplication(), allIds)
                    } else {
                        // Restore previously saved user selection
                        val saved = loadSavedSelectedCourseIds()
                        if (saved != null) {
                            _selectedCourseIds.value = saved
                        }
                    }

                    // Active course validation: keep user's saved course if present
                    val savedActive = coursePrefs.getString("saved_active_course_id", "") ?: ""
                    if (savedActive.isNotBlank() && courses.any { it.id == savedActive }) {
                        if (activeCourseId.value != savedActive) {
                            activeCourseId.value = savedActive
                        }
                    } else if (activeCourseId.value.isNotBlank() && courses.any { it.id == activeCourseId.value }) {
                        coursePrefs.edit().putString("saved_active_course_id", activeCourseId.value).commit()
                    } else {
                        val fallback = _selectedCourseIds.value.firstOrNull { selId -> courses.any { it.id == selId } }
                            ?: courses.first().id
                        activeCourseId.value = fallback
                        coursePrefs.edit().putString("saved_active_course_id", fallback).commit()
                    }
                }
            }
        }
    }

    fun toggleCourseSelection(courseId: String) {
        val current = _selectedCourseIds.value.toMutableSet()
        if (current.contains(courseId)) {
            current.remove(courseId)
        } else {
            current.add(courseId)
        }
        _selectedCourseIds.value = current
        saveSelectedCoursesToPrefs(current)

        // Sync with widget preferences & update widgets immediately
        com.example.widget.DailyVocabWidgetProvider.saveSelectedCourseIds(getApplication(), current)
        com.example.widget.DailyVocabWidgetProvider.updateAllWidgets(getApplication())

        // If current active course was deselected, switch to another selected course
        if (activeCourseId.value.isNotBlank() && !current.contains(activeCourseId.value)) {
            val fallback = current.firstOrNull() ?: ""
            selectCourse(fallback)
        } else if (activeCourseId.value.isBlank() && current.isNotEmpty()) {
            selectCourse(current.first())
        }

        // Trigger backup refresh so only selected course data is backed up
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.backupManager.saveBackupFiles(uid)
        }
    }

    fun selectAllCourses() {
        val allIds = allCourses.value.map { it.id }.toSet()
        _selectedCourseIds.value = allIds
        saveSelectedCoursesToPrefs(allIds)

        com.example.widget.DailyVocabWidgetProvider.saveSelectedCourseIds(getApplication(), allIds)
        com.example.widget.DailyVocabWidgetProvider.updateAllWidgets(getApplication())

        if (activeCourseId.value.isBlank() && allIds.isNotEmpty()) {
            selectCourse(allIds.first())
        }
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.backupManager.saveBackupFiles(uid)
        }
    }

    fun deselectAllCourses() {
        _selectedCourseIds.value = emptySet()
        saveSelectedCoursesToPrefs(emptySet())

        com.example.widget.DailyVocabWidgetProvider.saveSelectedCourseIds(getApplication(), emptySet())
        com.example.widget.DailyVocabWidgetProvider.updateAllWidgets(getApplication())

        activeCourseId.value = ""
        coursePrefs.edit().putString("saved_active_course_id", "").apply()

        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.backupManager.saveBackupFiles(uid)
        }
    }

    fun setCourseSelected(courseId: String, selected: Boolean) {
        val current = _selectedCourseIds.value.toMutableSet()
        if (selected) {
            current.add(courseId)
        } else {
            current.remove(courseId)
        }
        _selectedCourseIds.value = current
        saveSelectedCoursesToPrefs(current)

        com.example.widget.DailyVocabWidgetProvider.saveSelectedCourseIds(getApplication(), current)
        com.example.widget.DailyVocabWidgetProvider.updateAllWidgets(getApplication())

        if (activeCourseId.value.isNotBlank() && !current.contains(activeCourseId.value)) {
            val fallback = current.firstOrNull() ?: ""
            selectCourse(fallback)
        } else if (activeCourseId.value.isBlank() && current.isNotEmpty()) {
            selectCourse(current.first())
        }
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.backupManager.saveBackupFiles(uid)
        }
    }

    // Google Drive Sync State & Safe Progress Preservation
    private val _isSyncingDrive = MutableStateFlow(false)
    val isSyncingDrive: StateFlow<Boolean> = _isSyncingDrive.asStateFlow()

    private val _driveSyncSummary = MutableStateFlow<DriveSyncSummary?>(null)
    val driveSyncSummary: StateFlow<DriveSyncSummary?> = _driveSyncSummary.asStateFlow()

    private val syncPrefs = application.getSharedPreferences("memorizer_sync_prefs", android.content.Context.MODE_PRIVATE)
    val driveSyncUrl = MutableStateFlow(
        syncPrefs.getString("saved_drive_sync_url", "https://drive.google.com/drive/folders/1OBqSlB21FD_-0tpRZE8H6R5VFzDkeX2n") ?: ""
    )

    val qbSyncUrl = MutableStateFlow(
        syncPrefs.getString("saved_qb_sync_url", "") ?: ""
    )
    val isSyncingQB = MutableStateFlow(false)

    fun setDriveSyncUrl(url: String) {
        driveSyncUrl.value = url
        syncPrefs.edit().putString("saved_drive_sync_url", url).apply()
    }

    fun setQbSyncUrl(url: String) {
        qbSyncUrl.value = url
        syncPrefs.edit().putString("saved_qb_sync_url", url).apply()
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
            coursePrefs.edit().putString("saved_active_course_id", id).commit()
            val curSel = _selectedCourseIds.value.toMutableSet()
            if (!curSel.contains(id)) {
                curSel.add(id)
                _selectedCourseIds.value = curSel
                saveSelectedCoursesToPrefs(curSel)
            }
        }
        activeCourseId.value = id
        currentWordIndex.value = 0
    }

    fun createCourse(title: String, description: String? = null, initialContent: String? = null, isJson: Boolean = false) {
        viewModelScope.launch {
            val course = repository.createCourse(title, description)
            activeCourseId.value = course.id
            val curSel = _selectedCourseIds.value.toMutableSet()
            curSel.add(course.id)
            _selectedCourseIds.value = curSel
            coursePrefs.edit().putStringSet("selected_course_ids", curSel).apply()

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

    fun deleteCourse(courseId: String, keepProgress: Boolean = true) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            repository.deleteCourse(courseId, keepProgress, uid)
            val curSel = _selectedCourseIds.value.toMutableSet()
            curSel.remove(courseId)
            _selectedCourseIds.value = curSel
            coursePrefs.edit().putStringSet("selected_course_ids", curSel).apply()

            if (activeCourseId.value == courseId) {
                val remaining = allCourses.value.filter { it.id != courseId && curSel.contains(it.id) }
                activeCourseId.value = remaining.firstOrNull()?.id ?: curSel.firstOrNull() ?: ""
            }
            _statusMessage.value = if (keepProgress) "Course deleted (progress saved)" else "Course & progress deleted"
        }
    }

    // Articles State (Read Article Feature)
    val allArticles: StateFlow<List<ArticleEntity>> = repository.allArticles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeArticle = MutableStateFlow<ArticleEntity?>(null)

    fun selectArticle(article: ArticleEntity?) {
        activeArticle.value = article
    }

    fun addSampleData(force: Boolean = true) {
        viewModelScope.launch {
            _statusMessage.value = "Adding sample courses & articles..."
            repository.seedSampleData(force)
            val courses = repository.allCourses.firstOrNull() ?: emptyList()
            if (courses.isNotEmpty()) {
                val curSel = _selectedCourseIds.value.toMutableSet()
                if (curSel.isEmpty()) {
                    val allIds = courses.map { it.id }.toSet()
                    _selectedCourseIds.value = allIds
                    coursePrefs.edit().putStringSet("selected_course_ids", allIds).apply()
                }
                if (activeCourseId.value.isBlank() || courses.none { it.id == activeCourseId.value }) {
                    selectCourse(courses.first().id)
                }
            }
            _statusMessage.value = "Sample courses & articles added successfully!"
        }
    }

    // Dark / Night Theme State
    val isDarkTheme = MutableStateFlow(prefs.getBoolean("is_dark_theme", false))

    fun setDarkTheme(enabled: Boolean) {
        isDarkTheme.value = enabled
        prefs.edit().putBoolean("is_dark_theme", enabled).commit()
    }

    fun toggleDarkTheme() {
        setDarkTheme(!isDarkTheme.value)
    }

    // Flashcard Flip Animation Setting (Toggle in Profile: On/Off)
    val isFlipAnimationEnabled = MutableStateFlow(prefs.getBoolean("is_flip_animation_enabled", true))

    fun setFlipAnimationEnabled(enabled: Boolean) {
        isFlipAnimationEnabled.value = enabled
        prefs.edit().putBoolean("is_flip_animation_enabled", enabled).commit()
        _statusMessage.value = if (enabled) "Card flip animation enabled" else "Card flip animation disabled"
    }

    // Flashcard Haptic Feedback Setting (Toggle in Profile: On/Off)
    val isHapticEnabled = MutableStateFlow(prefs.getBoolean("is_haptic_enabled", true))

    fun setHapticEnabled(enabled: Boolean) {
        isHapticEnabled.value = enabled
        prefs.edit().putBoolean("is_haptic_enabled", enabled).commit()
        _statusMessage.value = if (enabled) "Haptic feedback enabled" else "Haptic feedback disabled"
    }

    // Flashcard Focus Mode (Hides bottom nav, enlarges card, positions tag buttons at bottom)
    val isFocusMode = MutableStateFlow(prefs.getBoolean("is_focus_mode", false))

    fun toggleFocusMode() {
        setFocusMode(!isFocusMode.value)
    }

    fun setFocusMode(enabled: Boolean) {
        isFocusMode.value = enabled
        prefs.edit().putBoolean("is_focus_mode", enabled).commit()
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

    fun saveArticlesBatch(articles: List<Triple<String, String, String>>) {
        viewModelScope.launch {
            val entities = articles.mapIndexed { index, (title, content, author) ->
                val id = "art_${System.currentTimeMillis()}_$index"
                val count = content.split("\\s+".toRegex()).count { it.isNotBlank() }
                com.example.data.model.ArticleEntity(
                    id = id,
                    title = title.ifBlank { "Article ${index + 1}" },
                    content = content,
                    author = author.ifBlank { "Anonymous Author" },
                    courseId = activeCourseId.value,
                    createdAt = System.currentTimeMillis() + (articles.size - index),
                    wordCount = count
                )
            }
            if (entities.isNotEmpty()) {
                repository.saveArticles(entities)
                activeArticle.value = entities.first()
                _statusMessage.value = "${entities.size} articles imported successfully!"
            }
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

    val isSyncingArticles = MutableStateFlow(false)
    val articleSyncUrl = MutableStateFlow(repository.getArticleSyncUrl())

    val pendingSharedTextOrUrl = MutableStateFlow<String?>(null)

    fun setPendingSharedTextOrUrl(textOrUrl: String) {
        pendingSharedTextOrUrl.value = textOrUrl
        setRoute("article_reader")
    }

    fun clearPendingSharedTextOrUrl() {
        pendingSharedTextOrUrl.value = null
    }

    fun setArticleSyncUrl(url: String) {
        articleSyncUrl.value = url
        repository.setArticleSyncUrl(url)
    }

    fun syncArticles(customUrlOrText: String? = null, onComplete: ((String) -> Unit)? = null) {
        val source = (customUrlOrText ?: articleSyncUrl.value).trim()
        if (source.isBlank()) {
            _statusMessage.value = "Please provide a Google Doc link or text source."
            onComplete?.invoke("No source provided")
            return
        }
        viewModelScope.launch {
            isSyncingArticles.value = true
            val result = repository.syncArticlesFromSource(source)
            isSyncingArticles.value = false
            result.onSuccess { summary ->
                if (source.startsWith("http")) {
                    setArticleSyncUrl(source)
                }
                val msg = "Sync complete: ${summary.updatedCount} updated, ${summary.addedCount} new added" +
                        if (summary.skippedDeletedCount > 0) " (${summary.skippedDeletedCount} previously deleted skipped)" else ""
                _statusMessage.value = msg
                onComplete?.invoke(msg)
            }.onFailure { err ->
                val errorMsg = "Sync failed: ${err.message}"
                _statusMessage.value = errorMsg
                onComplete?.invoke(errorMsg)
            }
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

    // Scraped Flashcard Generation States
    val isScrapingFlashcards = MutableStateFlow(false)
    val lastGeneratedCardsCount = MutableStateFlow<Int?>(null)

    // Sitemap XML Extraction & Batch Processing States
    val isFetchingSitemap = MutableStateFlow(false)
    val sitemapResult = MutableStateFlow<com.example.data.service.SitemapFetchResult?>(null)
    val isBatchProcessingSitemap = MutableStateFlow(false)
    val sitemapBatchProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val sitemapCurrentProcessingTitle = MutableStateFlow("")

    fun fetchWebsiteSitemap(
        urlOrDomain: String,
        onComplete: (Result<com.example.data.service.SitemapFetchResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            isFetchingSitemap.value = true
            val result = repository.fetchWebsiteSitemap(urlOrDomain)
            isFetchingSitemap.value = false
            result.onSuccess {
                sitemapResult.value = it
                _statusMessage.value = "Found ${it.articles.size} article URLs in sitemap!"
            }.onFailure {
                _statusMessage.value = "Sitemap error: ${it.message}"
            }
            onComplete(result)
        }
    }

    fun batchImportSitemapArticles(
        selectedArticles: List<com.example.data.service.SitemapArticleItem>,
        courseId: String = "course_default",
        onComplete: (Int, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isBatchProcessingSitemap.value = true
            sitemapBatchProgress.value = 0 to selectedArticles.size
            val result = repository.batchImportSitemapArticles(
                selectedArticles = selectedArticles,
                courseId = courseId,
                onProgress = { cur, tot, currentTitle ->
                    sitemapBatchProgress.value = cur to tot
                    sitemapCurrentProcessingTitle.value = currentTitle
                }
            )
            isBatchProcessingSitemap.value = false
            sitemapBatchProgress.value = null
            sitemapCurrentProcessingTitle.value = ""

            result.fold(
                onSuccess = { saved ->
                    _statusMessage.value = "Imported ${saved.size} articles to library!"
                    onComplete(saved.size, null)
                },
                onFailure = { error ->
                    _statusMessage.value = "Import error: ${error.message}"
                    onComplete(0, error.message ?: "Failed to import articles")
                }
            )
        }
    }

    fun batchGenerateSitemapFlashcards(
        selectedArticles: List<com.example.data.service.SitemapArticleItem>,
        onComplete: (Int, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isBatchProcessingSitemap.value = true
            sitemapBatchProgress.value = 0 to selectedArticles.size
            val result = repository.batchGenerateSitemapFlashcards(
                selectedArticles = selectedArticles,
                onProgress = { cur, tot, currentTitle ->
                    sitemapBatchProgress.value = cur to tot
                    sitemapCurrentProcessingTitle.value = currentTitle
                }
            )
            isBatchProcessingSitemap.value = false
            sitemapBatchProgress.value = null
            sitemapCurrentProcessingTitle.value = ""

            result.fold(
                onSuccess = { count ->
                    _statusMessage.value = "Generated and saved $count flashcards!"
                    onComplete(count, null)
                },
                onFailure = { error ->
                    _statusMessage.value = "Flashcard error: ${error.message}"
                    onComplete(0, error.message ?: "Failed to generate flashcards")
                }
            )
        }
    }

    fun scrapeAndGenerateFlashcardsFromUrls(
        urls: List<String>,
        onComplete: (Int, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isScrapingFlashcards.value = true
            val result = repository.scrapeAndGenerateFlashcards(urls, saveToDb = true)
            isScrapingFlashcards.value = false
            result.fold(
                onSuccess = { cards ->
                    lastGeneratedCardsCount.value = cards.size
                    _statusMessage.value = "Generated and saved ${cards.size} flashcards!"
                    onComplete(cards.size, null)
                },
                onFailure = { error ->
                    _statusMessage.value = "Flashcard generation error: ${error.message}"
                    onComplete(0, error.message ?: "Failed to scrape and generate flashcards")
                }
            )
        }
    }

    suspend fun extractMainBodyText(url: String): Result<String> {
        return repository.scrapeArticleAndExtractBody(url)
    }

    // Vocabulary State
    val allWords: StateFlow<List<VocabularyWordEntity>> = repository.allWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctGroups: StateFlow<List<String>> = combine(allWords, activeCourseId) { words, actId ->
        val relevantWords = if (actId.isNotBlank()) words.filter { it.courseId == actId } else words
        relevantWords.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flashcard Screen Filters, Sorting & Navigation (Persisted across sessions)
    private val savedGroups: Set<String> = prefs.getString("selected_card_groups_csv", null)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        ?: prefs.getStringSet("selected_card_groups", emptySet())?.toSet()
        ?: emptySet()
    private val savedStatuses: Set<String> = prefs.getString("selected_card_statuses_csv", null)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        ?: prefs.getStringSet("selected_card_statuses", emptySet())?.toSet()
        ?: emptySet()
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
            .putString("selected_card_groups_csv", selectedGroups.value.joinToString(","))
            .putString("selected_card_statuses_csv", selectedStatuses.value.joinToString(","))
            .putStringSet("selected_card_groups", HashSet(selectedGroups.value))
            .putStringSet("selected_card_statuses", HashSet(selectedStatuses.value))
            .putString("card_sort_order", cardSortOrder.value)
            .putBoolean("has_user_set_status", hasUserExplicitlySetStatus.value)
            .commit()
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
        val shuffledIds = words.map { it.id }.shuffled(kotlin.random.Random(System.currentTimeMillis()))
        randomOrderMap.value = shuffledIds.mapIndexed { index, id -> id to index }.toMap()
    }

    fun setCardSortOrder(order: String) {
        if (order == "random") {
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

    val showOnlyFlagged = MutableStateFlow(false)

    fun toggleShowOnlyFlagged() {
        showOnlyFlagged.value = !showOnlyFlagged.value
        currentWordIndex.value = 0
    }

    fun setShowOnlyFlagged(enabled: Boolean) {
        showOnlyFlagged.value = enabled
        currentWordIndex.value = 0
    }

    fun resetAllCardFilters() {
        hasUserExplicitlySetStatus.value = false
        selectedGroups.value = emptySet()
        selectedGroup.value = null
        cardSortOrder.value = "default"
        showOnlyFlagged.value = false
        selectedStatuses.value = computeDefaultStatuses(allWords.value, activeCourseId.value, emptySet())
        currentWordIndex.value = 0
        persistFilters()
    }

    val filteredWords: StateFlow<List<VocabularyWordEntity>> = combine(
        combine(allWords, activeCourseId, selectedGroups) { words, courseId, groups ->
            Triple(words, courseId, groups)
        },
        selectedStatuses,
        showOnlyFlagged,
        cardSortOrder,
        randomOrderMap
    ) { (words, courseId, groups), statuses, onlyFlagged, sortOrder, orderMap ->
        if (courseId.isBlank() && words.isNotEmpty()) {
            emptyList()
        } else {
            val filtered = words.filter { w ->
                (courseId.isBlank() || w.courseId == courseId) &&
                (!onlyFlagged || w.isReported) &&
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

    fun reloadAllPreferences() {
        isDarkTheme.value = prefs.getBoolean("is_dark_theme", false)
        isFlipAnimationEnabled.value = prefs.getBoolean("is_flip_animation_enabled", true)
        isFocusMode.value = prefs.getBoolean("is_focus_mode", false)
        isHapticEnabled.value = prefs.getBoolean("is_haptic_enabled", true)
        cardSortOrder.value = prefs.getString("card_sort_order", "default") ?: "default"

        val savedG = prefs.getString("selected_card_groups_csv", null)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: prefs.getStringSet("selected_card_groups", emptySet())?.toSet()
            ?: emptySet()
        selectedGroups.value = savedG

        val savedS = prefs.getString("selected_card_statuses_csv", null)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: prefs.getStringSet("selected_card_statuses", emptySet())?.toSet()
            ?: emptySet()
        selectedStatuses.value = savedS

        val savedCourses = loadSavedSelectedCourseIds()
        if (savedCourses != null) {
            _selectedCourseIds.value = savedCourses
        }
        val savedActive = coursePrefs.getString("saved_active_course_id", "") ?: ""
        if (savedActive.isNotBlank()) {
            activeCourseId.value = savedActive
        }
    }

    init {
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
                    if (cardSortOrder.value == "random" && randomOrderMap.value.isEmpty()) {
                        regenerateRandomOrder()
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

    // Flashcard & Word Actions: Persist status across all screens (Reader, Flashcards, Quiz)
    fun rateWord(wordId: String, status: String) {
        viewModelScope.launch {
            val uid = _currentUser.value?.userId ?: "1235"
            // Always persist status to database immediately
            repository.updateWordStatus(wordId, status, uid)

            val currentList = filteredWords.value
            if (currentList.isEmpty() || !currentList.any { it.id == wordId }) return@launch

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
                reloadAllPreferences()
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
                reloadAllPreferences()
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
                reloadAllPreferences()
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

    fun importQuestionBankFile(content: String, clearExisting: Boolean = false) {
        viewModelScope.launch {
            val result = repository.importQuestionBankFile(content, clearExisting)
            result.onSuccess { count ->
                _statusMessage.value = "Imported $count Question Bank items!"
            }.onFailure { err ->
                _statusMessage.value = "QB import failed: ${err.message}"
            }
        }
    }

    fun importQuestionBankBytes(bytes: ByteArray, fileName: String, clearExisting: Boolean = false) {
        viewModelScope.launch {
            val result = repository.importQuestionBankFromBytes(bytes, fileName, clearExisting)
            result.onSuccess { count ->
                _statusMessage.value = "Imported $count Question Bank items from $fileName!"
            }.onFailure { err ->
                _statusMessage.value = "QB import failed: ${err.message}"
            }
        }
    }

    fun importQuestionBankFromUrl(
        url: String = qbSyncUrl.value,
        clearExisting: Boolean = false,
        onComplete: ((Result<Int>) -> Unit)? = null
    ) {
        val targetUrl = url.trim()
        if (targetUrl.isBlank()) {
            _statusMessage.value = "Please enter a valid Question Bank link."
            onComplete?.invoke(Result.failure(Exception("Please enter a valid link.")))
            return
        }
        setQbSyncUrl(targetUrl)
        viewModelScope.launch {
            isSyncingQB.value = true
            _statusMessage.value = "Connecting and importing Question Bank from link..."
            val result = repository.importQuestionBankFromUrl(targetUrl, clearExisting)
            isSyncingQB.value = false
            result.onSuccess { count ->
                _statusMessage.value = "Successfully imported $count Question Bank items from link!"
            }.onFailure { err ->
                _statusMessage.value = "QB import failed: ${err.message}"
            }
            onComplete?.invoke(result)
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
            try {
                val uid = _currentUser.value?.userId ?: "1235"
                repository.recordWordQuizAnswer(wordId, isCorrect, uid)
            } catch (_: Exception) {}
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
