package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.service.ArticleSitemapService
import com.example.data.service.SitemapArticleItem
import com.example.data.service.SitemapChildIndex
import com.example.data.service.SitemapFetchResult
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleSitemapExtractorBottomSheet(
    onDismiss: () -> Unit,
    onArticlesImported: (Int) -> Unit = {},
    onFlashcardsGenerated: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sitemapService = remember { ArticleSitemapService.getInstance() }

    var urlInput by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var fetchResult by remember { mutableStateOf<SitemapFetchResult?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeSubSitemapUrl by remember { mutableStateOf<String?>(null) }

    // Batch processing states
    var isBatchProcessing by remember { mutableStateOf(false) }
    var processingActionType by remember { mutableStateOf<String?>(null) } // "IMPORT" or "FLASHCARDS"
    var batchProgress by remember { mutableStateOf(0 to 0) }
    var currentProcessingTitle by remember { mutableStateOf("") }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    val sitemapPresets = listOf(
        "TechCrunch" to "https://techcrunch.com/sitemap.xml",
        "The Verge" to "https://www.theverge.com/sitemap.xml",
        "The Guardian" to "https://www.theguardian.com/sitemap.xml",
        "BBC News" to "https://www.bbc.com/sitemap.xml",
        "Android Dev" to "https://android-developers.googleblog.com/sitemap.xml"
    )

    fun doFetchSitemap(targetUrl: String) {
        val cleanUrl = targetUrl.trim()
        if (cleanUrl.isBlank()) {
            errorMessage = "Please enter a website or sitemap URL."
            return
        }

        errorMessage = null
        isFetching = true
        fetchResult = null
        selectedUrls = emptySet()
        searchQuery = ""
        activeSubSitemapUrl = null

        coroutineScope.launch {
            val result = sitemapService.fetchSitemap(cleanUrl)
            isFetching = false
            result.fold(
                onSuccess = { res ->
                    if (res.articles.isEmpty() && res.subSitemaps.isEmpty()) {
                        errorMessage = "No article URLs found in this sitemap. Try another sitemap URL."
                    } else {
                        fetchResult = res
                        // Default: preselect top 5 articles for user convenience
                        selectedUrls = res.articles.take(5).map { it.url }.toSet()
                        if (res.subSitemaps.isNotEmpty()) {
                            activeSubSitemapUrl = res.subSitemaps.firstOrNull()?.url
                        }
                    }
                },
                onFailure = { error ->
                    errorMessage = error.message ?: "Failed to fetch sitemap."
                }
            )
        }
    }

    fun doFetchSubSitemap(subUrl: String, domain: String) {
        isFetching = true
        errorMessage = null
        activeSubSitemapUrl = subUrl
        coroutineScope.launch {
            val result = sitemapService.parseSitemapFromDirectUrl(subUrl, domain)
            isFetching = false
            result.fold(
                onSuccess = { res ->
                    fetchResult = res.copy(
                        subSitemaps = fetchResult?.subSitemaps ?: emptyList()
                    )
                    selectedUrls = res.articles.take(5).map { it.url }.toSet()
                },
                onFailure = { error ->
                    errorMessage = "Error loading sub-sitemap: ${error.message}"
                }
            )
        }
    }

    // Filtered articles list based on search query
    val displayedArticles = remember(fetchResult, searchQuery) {
        val all = fetchResult?.articles ?: emptyList()
        if (searchQuery.isBlank()) all
        else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            all.filter {
                it.title.lowercase(Locale.ROOT).contains(q) ||
                it.url.lowercase(Locale.ROOT).contains(q) ||
                it.section?.lowercase(Locale.ROOT)?.contains(q) == true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(SlateBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(IndigoLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sitemap.xml Article Extractor",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = SlateText
                    )
                    Text(
                        text = "Extract and batch process articles from any website sitemap",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = SlateMuted
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // URL Input Row
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    errorMessage = null
                },
                label = { Text("Website or Sitemap URL", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                placeholder = { Text("https://example.com/sitemap.xml", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (urlInput.isNotBlank()) {
                            IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(
                            onClick = {
                                val clip = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    urlInput = clip.trim()
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IndigoPrimary,
                    unfocusedBorderColor = SlateBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Popular Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Presets:",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp,
                    color = SlateMuted,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                sitemapPresets.forEach { (name, link) ->
                    AssistChip(
                        onClick = {
                            urlInput = link
                            doFetchSitemap(link)
                        },
                        label = { Text(name, fontFamily = PoppinsFontFamily, fontSize = 10.5.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = SlateLight),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fetch Button
            Button(
                onClick = { doFetchSitemap(urlInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isFetching && !isBatchProcessing && urlInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetching & Parsing Sitemap...", fontFamily = PoppinsFontFamily, fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetch Sitemap Articles", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = RoseError
                )
            }

            // Results Section
            if (fetchResult != null) {
                Spacer(modifier = Modifier.height(10.dp))

                // Sub-sitemaps switcher if present
                if (fetchResult!!.subSitemaps.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Child Sitemaps (${fetchResult!!.subSitemaps.size}):",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = SlateText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fetchResult!!.subSitemaps.forEach { child ->
                                val isSelected = activeSubSitemapUrl == child.url
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected && !isFetching) {
                                            doFetchSubSitemap(child.url, fetchResult!!.websiteDomain)
                                        }
                                    },
                                    label = { Text(child.name, fontFamily = PoppinsFontFamily, fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Filter & Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter articles by keyword...", fontFamily = PoppinsFontFamily, fontSize = 11.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = SlateMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = SlateBorder
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Fast selection controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${displayedArticles.size} URLs (${selectedUrls.size} selected)",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SlateText
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                selectedUrls = displayedArticles.map { it.url }.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("All", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = IndigoPrimary)
                        }

                        TextButton(
                            onClick = {
                                selectedUrls = displayedArticles.take(5).map { it.url }.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Top 5", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = IndigoPrimary)
                        }

                        TextButton(
                            onClick = {
                                selectedUrls = displayedArticles.take(10).map { it.url }.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Top 10", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = IndigoPrimary)
                        }

                        TextButton(
                            onClick = { selectedUrls = emptySet() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Clear", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = RoseError)
                        }
                    }
                }

                // Article URLs List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(displayedArticles) { _, article ->
                        val isSelected = selectedUrls.contains(article.url)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedUrls = if (isSelected) selectedUrls - article.url else selectedUrls + article.url
                                },
                            color = if (isSelected) IndigoLight.copy(alpha = 0.35f) else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else SlateBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { chk ->
                                        selectedUrls = if (chk) selectedUrls + article.url else selectedUrls - article.url
                                    },
                                    modifier = Modifier.size(22.dp),
                                    colors = CheckboxDefaults.colors(checkedColor = IndigoPrimary)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = article.title,
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = SlateText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = article.url.substringAfter("://"),
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.sp,
                                        color = SlateMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (article.lastMod != null) {
                                            Surface(
                                                color = SlateLight,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "📅 ${article.lastMod}",
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 9.5.sp,
                                                    color = SlateText,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        if (article.section != null) {
                                            Surface(
                                                color = IndigoLight,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = article.section,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 9.5.sp,
                                                    color = IndigoPrimary,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        if (article.priority != null) {
                                            Text(
                                                text = "p:${article.priority}",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 9.5.sp,
                                                color = SlateMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Batch Progress Indicator
                AnimatedVisibility(visible = isBatchProcessing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Processing ${batchProgress.first} of ${batchProgress.second}...",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = currentProcessingTitle.take(30),
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.5.sp,
                                color = SlateMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (batchProgress.second > 0) batchProgress.first.toFloat() / batchProgress.second.toFloat() else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = IndigoPrimary,
                            trackColor = IndigoLight
                        )
                    }
                }

                // Batch Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Batch Import to Reader
                    Button(
                        onClick = {
                            val chosen = displayedArticles.filter { selectedUrls.contains(it.url) }
                            if (chosen.isNotEmpty()) {
                                isBatchProcessing = true
                                processingActionType = "IMPORT"
                                batchProgress = 0 to chosen.size
                                coroutineScope.launch {
                                    val res = sitemapService.batchImportArticlesToReader(
                                        context = context,
                                        selectedArticles = chosen,
                                        onProgress = { cur, tot, title ->
                                            batchProgress = cur to tot
                                            currentProcessingTitle = title
                                        }
                                    )
                                    isBatchProcessing = false
                                    processingActionType = null
                                    res.fold(
                                        onSuccess = { saved ->
                                            Toast.makeText(context, "Imported ${saved.size} articles to reader!", Toast.LENGTH_SHORT).show()
                                            onArticlesImported(saved.size)
                                            onDismiss()
                                        },
                                        onFailure = { err ->
                                            errorMessage = "Import error: ${err.message}"
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isBatchProcessing && selectedUrls.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBatchProcessing && processingActionType == "IMPORT") "Importing..." else "Import (${selectedUrls.size})",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Action 2: Batch Generate Flashcards
                    Button(
                        onClick = {
                            val chosen = displayedArticles.filter { selectedUrls.contains(it.url) }
                            if (chosen.isNotEmpty()) {
                                isBatchProcessing = true
                                processingActionType = "FLASHCARDS"
                                batchProgress = 0 to chosen.size
                                coroutineScope.launch {
                                    val res = sitemapService.batchGenerateFlashcardsFromArticles(
                                        context = context,
                                        selectedArticles = chosen,
                                        onProgress = { cur, tot, title ->
                                            batchProgress = cur to tot
                                            currentProcessingTitle = title
                                        }
                                    )
                                    isBatchProcessing = false
                                    processingActionType = null
                                    res.fold(
                                        onSuccess = { count ->
                                            Toast.makeText(context, "Generated and saved $count flashcards!", Toast.LENGTH_SHORT).show()
                                            onFlashcardsGenerated(count)
                                            onDismiss()
                                        },
                                        onFailure = { err ->
                                            errorMessage = "Flashcard error: ${err.message}"
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isBatchProcessing && selectedUrls.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBatchProcessing && processingActionType == "FLASHCARDS") "Generating..." else "Flashcards (${selectedUrls.size})",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
