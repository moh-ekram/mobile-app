package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.model.ArticleBookGroupEntity
import com.example.data.model.ArticleEntity
import com.example.data.pdf.ArticlePdfBookGenerator
import com.example.data.pdf.ArticleRichContentParser
import com.example.data.pdf.DownloadedPdfInfo
import com.example.data.pdf.GeneratedBookPdfResult
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArticleBookStudioDialog(
    articles: List<ArticleEntity>,
    bookGroups: List<ArticleBookGroupEntity>,
    initialSelectedIds: List<String> = emptyList(),
    onSaveBookGroup: (title: String, subtitle: String, articleIds: List<String>, existingId: String?) -> Unit,
    onDeleteBookGroup: (String) -> Unit,
    onOpenPdfPreview: (bookTitle: String, bookSubtitle: String, orderedArticles: List<ArticleEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val articleById = remember(articles) { articles.associateBy { it.id } }

    // Tab 0 = Saved Groups, Tab 1 = Build / Edit Book
    var activeTab by remember {
        mutableIntStateOf(if (initialSelectedIds.isNotEmpty() || bookGroups.isEmpty()) 1 else 0)
    }

    var editingGroupId by remember { mutableStateOf<String?>(null) }
    var bookTitle by remember {
        mutableStateOf(
            if (initialSelectedIds.size > 1) "Selected Reading Collection"
            else articles.firstOrNull { it.id in initialSelectedIds }?.title?.take(42) ?: ""
        )
    }
    var bookSubtitle by remember { mutableStateOf("") }
    var orderedSelectedIds by remember {
        mutableStateOf(
            if (initialSelectedIds.isNotEmpty()) initialSelectedIds.filter { articleById.containsKey(it) }
            else emptyList()
        )
    }
    var searchQuery by remember { mutableStateOf("") }

    fun loadGroupIntoEditor(group: ArticleBookGroupEntity) {
        editingGroupId = group.id
        bookTitle = group.title
        bookSubtitle = group.subtitle
        orderedSelectedIds = ArticleRichContentParser.parseArticleIdsJson(group.articleIdsJson)
            .filter { articleById.containsKey(it) }
        activeTab = 1
    }

    fun startNewBook() {
        editingGroupId = null
        bookTitle = ""
        bookSubtitle = ""
        orderedSelectedIds = emptyList()
        activeTab = 1
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Book & PDF Studio",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SlateText
                            )
                            Text(
                                text = "Group articles & compile A4 eBook PDFs",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_book_studio_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0 to "Saved Groups (${bookGroups.size})",
                        1 to if (editingGroupId != null) "Edit Book Group" else "Create / Export"
                    ).forEach { (tabIdx, label) ->
                        val selected = activeTab == tabIdx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { activeTab = tabIdx }
                                .testTag("book_studio_tab_$tabIdx"),
                            color = if (selected) Color.White else Color.Transparent,
                            shadowElevation = if (selected) 1.5.dp else 0.dp,
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text(
                                text = label,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) IndigoPrimary else SlateMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (activeTab == 0) {
                    // SAVED BOOK GROUPS TAB
                    if (bookGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.LibraryBooks,
                                    contentDescription = null,
                                    tint = SlateMuted.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "No Book Groups Yet",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = SlateText
                                )
                                Text(
                                    text = "Select multiple articles to create a group and export a formatted A4 PDF book with Table of Contents.",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    color = SlateMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Button(
                                    onClick = { startNewBook() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                    modifier = Modifier.testTag("new_book_group_empty_cta")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create Book Group", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(bookGroups, key = { it.id }) { group ->
                                val groupArticleIds = remember(group.articleIdsJson, articleById) {
                                    ArticleRichContentParser.parseArticleIdsJson(group.articleIdsJson)
                                }
                                val groupArticles = remember(groupArticleIds, articleById) {
                                    groupArticleIds.mapNotNull { articleById[it] }
                                }
                                val updatedLabel = remember(group.updatedAt) {
                                    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(group.updatedAt))
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFFAFAFC),
                                    border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = group.title,
                                                    fontFamily = selectArticleFontForText(group.title),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.5.sp,
                                                    color = SlateText,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (group.subtitle.isNotBlank()) {
                                                    Text(
                                                        text = group.subtitle,
                                                        fontFamily = selectArticleFontForText(group.subtitle),
                                                        fontSize = 11.5.sp,
                                                        color = SlateMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${groupArticles.size} articles • Updated $updatedLabel",
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 11.sp,
                                                    color = SlateMuted
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { loadGroupIntoEditor(group) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "Edit Book Group",
                                                        tint = IndigoPrimary,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteBookGroup(group.id) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete Book Group",
                                                        tint = RoseError,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (groupArticles.isNotEmpty()) {
                                            Text(
                                                text = groupArticles.take(3).mapIndexed { i, art -> "${i + 1}. ${art.title}" }.joinToString("  •  ") +
                                                    if (groupArticles.size > 3) " (+${groupArticles.size - 3} more)" else "",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 11.sp,
                                                color = SlateText.copy(alpha = 0.75f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (groupArticles.isNotEmpty()) {
                                                    onOpenPdfPreview(group.title, group.subtitle, groupArticles)
                                                }
                                            },
                                            enabled = groupArticles.isNotEmpty(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("export_group_pdf_${group.id}")
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (groupArticles.isEmpty()) "No Available Articles" else "Preview & Export A4 PDF",
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { startNewBook() },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_new_book_group_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create New Book Group", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = IndigoPrimary)
                        }
                    }
                } else {
                    // BUILD / EDIT BOOK GROUP TAB
                    val selectedArticles = remember(orderedSelectedIds, articleById) {
                        orderedSelectedIds.mapNotNull { articleById[it] }
                    }
                    val unselectedArticles = remember(articles, orderedSelectedIds, searchQuery) {
                        val selectedSet = orderedSelectedIds.toSet()
                        articles.filter { art ->
                            art.id !in selectedSet &&
                                (searchQuery.isBlank() ||
                                    art.title.contains(searchQuery, ignoreCase = true) ||
                                    art.websiteName.contains(searchQuery, ignoreCase = true) ||
                                    art.author.contains(searchQuery, ignoreCase = true))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bookTitle,
                            onValueChange = { bookTitle = it },
                            label = { Text("Book Title *", fontSize = 12.sp) },
                            placeholder = { Text("e.g., Weekly Editorial Anthology", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_title_input")
                        )

                        OutlinedTextField(
                            value = bookSubtitle,
                            onValueChange = { bookSubtitle = it },
                            label = { Text("Subtitle (Optional)", fontSize = 12.sp) },
                            placeholder = { Text("e.g., Vocabulary & Current Affairs", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter articles to include...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateMuted) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick select / clear bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedArticles.size} selected in book order",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedArticles.isNotEmpty()) IndigoPrimary else SlateMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val addIds = unselectedArticles.map { it.id }
                                        orderedSelectedIds = (orderedSelectedIds + addIds).distinct()
                                    },
                                    enabled = unselectedArticles.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Select All", fontSize = 11.sp, color = IndigoPrimary)
                                }
                                if (orderedSelectedIds.isNotEmpty()) {
                                    TextButton(
                                        onClick = { orderedSelectedIds = emptyList() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear", fontSize = 11.sp, color = SlateMuted)
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedArticles.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "INCLUDED CHAPTERS (ORDERED)",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                                itemsIndexed(selectedArticles, key = { _, art -> "sel_${art.id}" }) { idx, art ->
                                    val sourceHost = remember(art) { ArticleRichContentParser.deriveWebsiteName(art) }
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        color = IndigoLight.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(IndigoPrimary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = art.title,
                                                    fontFamily = selectArticleFontForText(art.title),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.5.sp,
                                                    color = SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$sourceHost • ${art.wordCount} words",
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 10.5.sp,
                                                    color = SlateMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (idx > 0) {
                                                IconButton(
                                                    onClick = {
                                                        val mutable = orderedSelectedIds.toMutableList()
                                                        val tmp = mutable[idx - 1]
                                                        mutable[idx - 1] = mutable[idx]
                                                        mutable[idx] = tmp
                                                        orderedSelectedIds = mutable
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            if (idx < selectedArticles.size - 1) {
                                                IconButton(
                                                    onClick = {
                                                        val mutable = orderedSelectedIds.toMutableList()
                                                        val tmp = mutable[idx + 1]
                                                        mutable[idx + 1] = mutable[idx]
                                                        mutable[idx] = tmp
                                                        orderedSelectedIds = mutable
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            IconButton(
                                                onClick = { orderedSelectedIds = orderedSelectedIds.filterNot { it == art.id } },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = RoseError, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            if (unselectedArticles.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "AVAILABLE ARTICLES",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateMuted,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                                items(unselectedArticles, key = { "avail_${it.id}" }) { art ->
                                    val sourceHost = remember(art) { ArticleRichContentParser.deriveWebsiteName(art) }
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { orderedSelectedIds = orderedSelectedIds + art.id }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AddCircleOutline,
                                                contentDescription = "Add article to book",
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = art.title,
                                                    fontFamily = selectArticleFontForText(art.title),
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 12.5.sp,
                                                    color = SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$sourceHost • ${art.wordCount} words",
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 10.5.sp,
                                                    color = SlateMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Action Footer: Save Group & Preview PDF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val effectiveTitle = bookTitle.trim().ifBlank {
                                if (selectedArticles.size == 1) selectedArticles.first().title else "Compiled Reading Book"
                            }
                            OutlinedButton(
                                onClick = {
                                    onSaveBookGroup(effectiveTitle, bookSubtitle.trim(), orderedSelectedIds, editingGroupId)
                                    activeTab = 0
                                },
                                enabled = selectedArticles.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_book_group_button")
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (editingGroupId != null) "Update Group" else "Save Group",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = IndigoPrimary
                                )
                            }

                            Button(
                                onClick = {
                                    if (bookTitle.isNotBlank()) {
                                        onSaveBookGroup(effectiveTitle, bookSubtitle.trim(), orderedSelectedIds, editingGroupId)
                                    }
                                    onOpenPdfPreview(effectiveTitle, bookSubtitle.trim(), selectedArticles)
                                },
                                enabled = selectedArticles.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .testTag("preview_compiled_pdf_button")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Preview PDF",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfBookPreviewDialog(
    bookTitle: String,
    bookSubtitle: String,
    articles: List<ArticleEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isGenerating by remember { mutableStateOf(true) }
    var generationStage by remember { mutableStateOf("Preparing articles...") }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generatedResult by remember { mutableStateOf<GeneratedBookPdfResult?>(null) }

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var renderedPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPage by remember { mutableStateOf(false) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadedInfo by remember { mutableStateOf<DownloadedPdfInfo?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // Compile the real PDF on launch
    LaunchedEffect(bookTitle, bookSubtitle, articles) {
        isGenerating = true
        generationError = null
        downloadedInfo = null
        downloadError = null
        currentPageIndex = 0
        zoomScale = 1f
        panOffset = Offset.Zero

        val result = ArticlePdfBookGenerator.generateBookPdf(
            context = context,
            bookTitle = bookTitle,
            bookSubtitle = bookSubtitle,
            articles = articles,
            onProgress = { stage -> generationStage = stage }
        )
        isGenerating = false
        result.fold(
            onSuccess = { generatedResult = it },
            onFailure = { err -> generationError = err.message ?: "Failed to build PDF preview." }
        )
    }

    // Open PdfRenderer safely for the compiled file
    var rendererHolder by remember { mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null) }
    DisposableEffect(generatedResult?.pdfFile?.absolutePath) {
        val file = generatedResult?.pdfFile
        val opened = if (file != null && file.exists()) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pfd to renderer
            } catch (_: Exception) {
                null
            }
        } else null
        rendererHolder = opened

        onDispose {
            try {
                opened?.second?.close()
                opened?.first?.close()
            } catch (_: Exception) {}
            rendererHolder = null
        }
    }

    // Render current page bitmap off the UI thread whenever page changes
    LaunchedEffect(rendererHolder, currentPageIndex) {
        val holder = rendererHolder ?: return@LaunchedEffect
        val total = holder.second.pageCount
        if (currentPageIndex !in 0 until total) return@LaunchedEffect

        isRenderingPage = true
        val bmp = withContext(Dispatchers.IO) {
            try {
                synchronized(holder.second) {
                    val page = holder.second.openPage(currentPageIndex)
                    // 2x A4 resolution (1190 x 1684) gives crisp serif & Bengali glyphs in preview
                    val width = 1190
                    val height = 1684
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                }
            } catch (_: Exception) {
                null
            }
        }
        renderedPageBitmap = bmp
        isRenderingPage = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFF1F5F9),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookTitle.ifBlank { "Live A4 Book Preview" },
                                fontFamily = selectArticleFontForText(bookTitle),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SlateText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitleInfo = generatedResult?.let { res ->
                                "Page ${currentPageIndex + 1} of ${res.pageCount} • A4 (${res.tocEntries.size} articles)"
                            } ?: generationStage
                            Text(
                                text = subtitleInfo,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (generatedResult != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (zoomScale > 1.05f) IndigoLight else SlateLight,
                                    modifier = Modifier.clickable {
                                        zoomScale = if (zoomScale > 1.05f) 1f else 1.75f
                                        panOffset = Offset.Zero
                                    }
                                ) {
                                    Text(
                                        text = "${(zoomScale * 100).toInt()}%",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (zoomScale > 1.05f) IndigoPrimary else SlateText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("pdf_preview_close_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Preview", tint = SlateText, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Quick Jump TOC Strip
                val tocEntries = generatedResult?.tocEntries ?: emptyList()
                if (tocEntries.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            val isTocActive = currentPageIndex < (generatedResult?.tocPageCount ?: 1)
                            FilterChip(
                                selected = isTocActive,
                                onClick = { currentPageIndex = 0 },
                                label = {
                                    Text(
                                        "TOC (p.1)",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            )
                        }
                        items(tocEntries) { entry ->
                            val targetPageIdx = (entry.startPage - 1).coerceIn(0, (generatedResult?.pageCount ?: 1) - 1)
                            val isActive = currentPageIndex == targetPageIdx
                            FilterChip(
                                selected = isActive,
                                onClick = { currentPageIndex = targetPageIdx },
                                label = {
                                    Text(
                                        "${entry.index}. ${entry.title.take(18)}${if (entry.title.length > 18) "…" else ""} • p.${entry.startPage}",
                                        fontFamily = selectArticleFontForText(entry.title),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
                }

                // Live Page Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isGenerating -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = IndigoPrimary)
                                Text(
                                    text = generationStage,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SlateText
                                )
                            }
                        }

                        generationError != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RoseError, modifier = Modifier.size(36.dp))
                                Text(
                                    text = generationError ?: "Error generating PDF",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 13.sp,
                                    color = SlateText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        renderedPageBitmap != null -> {
                            val bmp = renderedPageBitmap!!
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (zoomScale > 1.05f) {
                                                    zoomScale = 1f
                                                    panOffset = Offset.Zero
                                                } else {
                                                    zoomScale = 1.85f
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newZoom = (zoomScale * zoom).coerceIn(1f, 3f)
                                            zoomScale = newZoom
                                            if (newZoom > 1.02f) {
                                                panOffset = Offset(
                                                    x = (panOffset.x + pan.x).coerceIn(-600f, 600f),
                                                    y = (panOffset.y + pan.y).coerceIn(-800f, 800f)
                                                )
                                            } else {
                                                panOffset = Offset.Zero
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(ArticlePdfBookGenerator.A4_WIDTH.toFloat() / ArticlePdfBookGenerator.A4_HEIGHT.toFloat())
                                        .fillMaxHeight()
                                        .shadow(6.dp, RoundedCornerShape(4.dp))
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "A4 PDF Page ${currentPageIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = zoomScale,
                                                scaleY = zoomScale,
                                                translationX = panOffset.x,
                                                translationY = panOffset.y
                                            )
                                    )
                                }
                            }
                        }

                        isRenderingPage -> {
                            CircularProgressIndicator(color = IndigoPrimary, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // Download Confirmation Banner (if saved)
                if (downloadedInfo != null) {
                    val info = downloadedInfo!!
                    Surface(
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = "Saved to Downloads",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        text = info.fileName,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF047857),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        try {
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(info.uri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(viewIntent, "Open PDF"))
                                        } catch (_: Exception) {}
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Open", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                }
                                TextButton(
                                    onClick = {
                                        try {
                                            val shareUri = generatedResult?.pdfFile?.let { f ->
                                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                            } ?: info.uri
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF Book"))
                                        } catch (_: Exception) {}
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Share", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                }
                            }
                        }
                    }
                } else if (downloadError != null) {
                    Text(
                        text = downloadError ?: "",
                        color = RoseError,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Bottom Control Bar: Page Prev/Next + Download PDF Button
                Surface(
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val totalPages = generatedResult?.pageCount ?: 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentPageIndex > 0) {
                                        currentPageIndex--
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                enabled = currentPageIndex > 0 && !isGenerating,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("pdf_preview_prev_page")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                            }

                            Text(
                                text = if (totalPages > 0) "${currentPageIndex + 1} / $totalPages" else "--",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = SlateText
                            )

                            IconButton(
                                onClick = {
                                    if (currentPageIndex < totalPages - 1) {
                                        currentPageIndex++
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                enabled = currentPageIndex < totalPages - 1 && !isGenerating,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("pdf_preview_next_page")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                            }
                        }

                        Button(
                            onClick = {
                                val res = generatedResult ?: return@Button
                                isDownloading = true
                                downloadError = null
                                scope.launch {
                                    val saveRes = ArticlePdfBookGenerator.savePdfToDownloads(
                                        context = context,
                                        sourceFile = res.pdfFile,
                                        bookTitle = res.bookTitle
                                    )
                                    isDownloading = false
                                    saveRes.fold(
                                        onSuccess = { downloadedInfo = it },
                                        onFailure = { err -> downloadError = err.message ?: "Failed to save PDF." }
                                    )
                                }
                            },
                            enabled = generatedResult != null && !isGenerating && !isDownloading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("download_pdf_book_button")
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving...", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download PDF", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
