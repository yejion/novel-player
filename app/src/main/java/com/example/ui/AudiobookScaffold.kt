package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.Book
import com.example.data.BookWithTracks
import com.example.data.Track
import com.example.player.PlayerState
import kotlinx.coroutines.launch
import java.util.Locale

// 1. Decorative Procedural Cover

@Composable
fun BookCover(
    title: String,
    coverColorHex: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 64.dp,
    isSelected: Boolean = false
) {
    val themeColor = remember(coverColorHex) {
        try {
            Color(android.graphics.Color.parseColor(coverColorHex))
        } catch (e: Exception) {
            Color(0xFF6200EE)
        }
    }
    
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(themeColor, themeColor.copy(alpha = 0.6f))
                )
            )
            .drawBehind {
                // Drawing dynamic visual ambient lines
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = size.minDimension / 1.5f,
                    center = androidx.compose.ui.geometry.Offset(size.width, 0f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension / 2.2f,
                    center = androidx.compose.ui.geometry.Offset(0f, size.height)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val initials = remember(title) {
            if (title.isNotEmpty()) {
                val p = title.filter { it.isLetterOrDigit() }
                if (p.length >= 2) p.substring(0, 2) else p.take(1)
            } else {
                "书"
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.MusicNote else Icons.Default.Book,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(sizeDp * 0.35f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = initials,
                color = Color.White,
                fontSize = (sizeDp.value * 0.22f).sp,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.4f),
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}

// 2. Main Entry Scaffold & Routing Layout

@Composable
fun AudiobookAppContent(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooksWithTracks.collectAsStateWithLifecycle()
    val currentNavBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentNavBackStack?.destination?.route

    // Professional Polish Lavender design theme
    val appBackground = Color(0xFFFDFBFF)
    val cardBackground = Color(0xFFF3EDF7)
    val accentOrange = Color(0xFF6750A4) // Deep Purple accent

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackground),
        containerColor = appBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // Elegant micro persistent mini-player bar at bottom of primary views
            if (playerState.bookId != null && currentRoute != "now_playing") {
                MiniPlayerBar(
                    state = playerState,
                    accentColor = accentOrange,
                    cardBg = cardBackground,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onClick = { navController.navigate("now_playing") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "shelf",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("shelf") {
                BooksShelfScreen(
                    viewModel = viewModel,
                    allBooks = allBooks,
                    activePlayerState = playerState,
                    navController = navController,
                    accentColor = accentOrange,
                    cardBg = cardBackground
                )
            }
            composable(
                route = "detail/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
                val bookWithTracks = allBooks.find { it.book.id == bookId }
                if (bookWithTracks != null) {
                    BookDetailScreen(
                        bookWithTracks = bookWithTracks,
                        playerState = playerState,
                        viewModel = viewModel,
                        navController = navController,
                        accentColor = accentOrange,
                        cardBg = cardBackground
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("寻找有声书失败", color = Color.White)
                    }
                }
            }
            composable("now_playing") {
                NowPlayingScreen(
                    viewModel = viewModel,
                    playerState = playerState,
                    allBooks = allBooks,
                    navController = navController,
                    accentColor = accentOrange
                )
            }
            composable("global_settings") {
                GlobalSettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    accentColor = accentOrange,
                    cardBg = cardBackground
                )
            }
        }
    }
}

// 3. Books Shelf Screen Component

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BooksShelfScreen(
    viewModel: MainViewModel,
    allBooks: List<BookWithTracks>,
    activePlayerState: PlayerState,
    navController: NavController,
    accentColor: Color,
    cardBg: Color
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    var showDeleteConfirmDialog by remember { mutableStateOf<Book?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "书听播放器",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1C1E),
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = "自动跳片头尾 · 本地有声书",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF49454F)
                    )
                )
            }
            
            // Settings and quick action icons
            Row {
                IconButton(
                    onClick = { navController.navigate("global_settings") },
                    modifier = Modifier.background(cardBg, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "全局设置",
                        tint = Color(0xFF6750A4)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scanner / Empty State Card
        if (allBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .padding(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "书架空空如也",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1C1E)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "您还未导入有声小说。立刻一键生成演示有声书并体验全部播放、切集和自动跳过片头尾功能！",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF49454F),
                            lineHeight = 18.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (isScanning) {
                        CircularProgressIndicator(color = accentColor)
                    } else {
                        Button(
                            onClick = {
                                viewModel.seedDemoBooks(context) {
                                    Toast.makeText(context, "已成功生成并装载演示有声书！", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seed_demo_button")
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("生成演示有声书 (带合成音轨)", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.scanLocalFolders(context) { count ->
                                    Toast.makeText(context, "完成设备扫描，成功导入 $count 本书籍！", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color(0xFFEADDFF), Color(0xFFD0BCFF)))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.YoutubeSearchedFor, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("扫描本地存储音频文件夹")
                        }
                    }
                }
            }
        } else {
            // Book List
            Text(
                text = "我的藏书 (${allBooks.size})",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1E),
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(allBooks) { item ->
                    val book = item.book
                    val sortedTracks = item.sortedTracks
                    val isCurrentBook = activePlayerState.bookId == book.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { navController.navigate("detail/${book.id}") },
                                onLongClick = { showDeleteConfirmDialog = book }
                            ),
                        colors = CardDefaults.cardColors(containerColor = if (isCurrentBook) cardBg.copy(alpha = 1.3f) else cardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BookCover(
                                title = book.title,
                                coverColorHex = book.coverColorHex,
                                sizeDp = 72.dp,
                                isSelected = isCurrentBook
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color(0xFF1A1C1E),
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrentBook && activePlayerState.isPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "正在播放",
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = "共 ${sortedTracks.size} 折/章 · 本地路径和分集",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF49454F)
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Playback Progress overview
                                val currentTrackNumber = if (book.currentTrackIndex < sortedTracks.size) book.currentTrackIndex + 1 else 1
                                val label = "待续：第 $currentTrackNumber 章"
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isCurrentBook) accentColor else Color(0xFF49454F),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    
                                    // Custom Skip badge
                                    val skipIntro = if (book.skipIntroSeconds == -1) "全局" else "${book.skipIntroSeconds}s"
                                    val skipOutro = if (book.skipOutroSeconds == -1) "全局" else "${book.skipOutroSeconds}s"
                                    Text(
                                        text = "跳过: $skipIntro / $skipOutro",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color(0xFF49454F))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick bottom scanner ribbon
            if (isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检索数据库 and 写入音频流...", color = Color(0xFF49454F), fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.seedDemoBooks(context) {
                                Toast.makeText(context, "重置或重新导入演示有声书成功！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("补充演示音频书", color = Color(0xFF49454F), fontSize = 13.sp)
                    }

                    TextButton(
                        onClick = {
                            viewModel.scanLocalFolders(context) { count ->
                                Toast.makeText(context, "扫描完成，新载入 $count 本书籍！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("检索本地存储", color = accentColor, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Delete confirm dialogue
    if (showDeleteConfirmDialog != null) {
        val target = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("确认要从书架下架吗？", color = Color(0xFF1A1C1E)) },
            text = { Text("此操作安全，仅会从书架清除小说 '${target.title}' 的历史播放和标签，若文件是演示文件将会自动清理。", color = Color(0xFF49454F)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(target.id)
                        showDeleteConfirmDialog = null
                        Toast.makeText(context, "已从书架清除该书", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("是的，下架", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("取消", color = Color(0xFF6750A4))
                }
            },
            containerColor = cardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// 4. Book Detail / Chapter Configurations Screen

@Composable
fun BookDetailScreen(
    bookWithTracks: BookWithTracks,
    playerState: PlayerState,
    viewModel: MainViewModel,
    navController: NavController,
    accentColor: Color,
    cardBg: Color
) {
    val book = bookWithTracks.book
    val tracks = bookWithTracks.sortedTracks
    val globalSettings by viewModel.globalSettings.collectAsStateWithLifecycle()

    var customIntroSelected by remember(book) { mutableStateOf(book.skipIntroSeconds != -1) }
    var customOutroSelected by remember(book) { mutableStateOf(book.skipOutroSeconds != -1) }

    var localSkipIntro by remember(book) { mutableStateOf(if (book.skipIntroSeconds == -1) globalSettings.defaultSkipIntroSeconds else book.skipIntroSeconds) }
    var localSkipOutro by remember(book) { mutableStateOf(if (book.skipOutroSeconds == -1) globalSettings.defaultSkipOutroSeconds else book.skipOutroSeconds) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color(0xFF1A1C1E))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("有声书详情及片头设置", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Book header card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BookCover(title = book.title, coverColorHex = book.coverColorHex, sizeDp = 84.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "路径: .../${book.folderPath.takeLast(24)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF49454F))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "格式: WAV 8k Mono PCM · 真实听得见的声音",
                            style = MaterialTheme.typography.labelSmall.copy(color = accentColor)
                        )
                    }
                }
            }

            // Segment skips modifiers (自动跳过片头和片尾配置)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Timeline, contentDescription = null, tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("喜马拉雅式 · 智能音轨跳过 (本小说独立设置)", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color(0xFFEADDFF))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Skip Intro Modifier row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("💡 自动跳过片头", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    text = if (customIntroSelected) "自定义: $localSkipIntro 秒" else "继承全局默认: ${globalSettings.defaultSkipIntroSeconds} 秒",
                                    color = Color(0xFF49454F),
                                    fontSize = 11.sp
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("自定义", color = Color(0xFF49454F), fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = customIntroSelected,
                                    onCheckedChange = { checked ->
                                        customIntroSelected = checked
                                        if (checked) {
                                            viewModel.updateBookSkipIntro(book.id, localSkipIntro)
                                        } else {
                                            viewModel.updateBookSkipIntro(book.id, -1)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor)
                                )
                            }
                        }

                        if (customIntroSelected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        localSkipIntro = (localSkipIntro - 1).coerceAtLeast(0)
                                        viewModel.updateBookSkipIntro(book.id, localSkipIntro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("-1s", color = accentColor, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        localSkipIntro = (localSkipIntro - 5).coerceAtLeast(0)
                                        viewModel.updateBookSkipIntro(book.id, localSkipIntro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("-5s", color = accentColor, fontSize = 11.sp)
                                }
                                Text(
                                    "$localSkipIntro 毫秒/秒",
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(70.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        localSkipIntro = (localSkipIntro + 1).coerceAtMost(300)
                                        viewModel.updateBookSkipIntro(book.id, localSkipIntro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("+1s", color = accentColor, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        localSkipIntro = (localSkipIntro + 5).coerceAtMost(300)
                                        viewModel.updateBookSkipIntro(book.id, localSkipIntro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("+5s", color = accentColor, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(color = Color(0xFFEADDFF))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Skip Outro Modifier row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("🏁 自动跳过片尾", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    text = if (customOutroSelected) "自定义: $localSkipOutro 秒" else "继承全局默认: ${globalSettings.defaultSkipOutroSeconds} 秒",
                                    color = Color(0xFF49454F),
                                    fontSize = 11.sp
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("自定义", color = Color(0xFF49454F), fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = customOutroSelected,
                                    onCheckedChange = { checked ->
                                        customOutroSelected = checked
                                        if (checked) {
                                            viewModel.updateBookSkipOutro(book.id, localSkipOutro)
                                        } else {
                                            viewModel.updateBookSkipOutro(book.id, -1)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor)
                                )
                            }
                        }

                        if (customOutroSelected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        localSkipOutro = (localSkipOutro - 1).coerceAtLeast(0)
                                        viewModel.updateBookSkipOutro(book.id, localSkipOutro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("-1s", color = accentColor, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        localSkipOutro = (localSkipOutro - 5).coerceAtLeast(0)
                                        viewModel.updateBookSkipOutro(book.id, localSkipOutro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("-5s", color = accentColor, fontSize = 11.sp)
                                }
                                Text(
                                    "$localSkipOutro 毫秒/秒",
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(70.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        localSkipOutro = (localSkipOutro + 1).coerceAtMost(300)
                                        viewModel.updateBookSkipOutro(book.id, localSkipOutro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("+1s", color = accentColor, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        localSkipOutro = (localSkipOutro + 5).coerceAtMost(300)
                                        viewModel.updateBookSkipOutro(book.id, localSkipOutro)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Text("+5s", color = accentColor, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Central Play Trigger Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "分集列表 (${tracks.size} 章)",
                        color = Color(0xFF1A1C1E),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = { viewModel.playBook(book, tracks, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("第一章起播", color = Color.White)
                    }
                }
            }

            // Chapters Grid list
            items(tracks) { track ->
                val isPlayingThisTrack = playerState.bookId == book.id && playerState.currentTrackIndex == (track.trackNumber - 1)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPlayingThisTrack) cardBg.copy(alpha = 0.95f) else cardBg.copy(alpha = 0.5f))
                        .clickable {
                            viewModel.playBook(book, tracks, track.trackNumber - 1)
                            navController.navigate("now_playing")
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Numerical dynamic accent
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isPlayingThisTrack) accentColor else Color(0xFFEADDFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlayingThisTrack && playerState.isPlaying) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else {
                            Text(
                                text = track.trackNumber.toString(),
                                color = if (isPlayingThisTrack) Color.White else Color(0xFF6750A4),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = if (isPlayingThisTrack) accentColor else Color(0xFF1A1C1E),
                            fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "时长: ${formatTime(track.durationMs)} · 本地文件路径",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF49454F), fontSize = 11.sp)
                        )
                    }

                    // Resume tag indicators
                    if (track.trackNumber - 1 == book.currentTrackIndex && book.lastPlayedPositionMs > 0) {
                        val percentage = ((book.lastPlayedPositionMs.toFloat() / track.durationMs) * 100).toInt().coerceIn(0, 100)
                        if (percentage in 1..99) {
                            Text(
                                "已播 $percentage%",
                                color = Color(0xFF6750A4),
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFEADDFF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        } else if (percentage >= 100) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "赞", tint = Color.Green, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// 5. Immersive Now Playing Dashboard Composable

@Composable
fun NowPlayingScreen(
    viewModel: MainViewModel,
    playerState: PlayerState,
    allBooks: List<BookWithTracks>,
    navController: NavController,
    accentColor: Color
) {
    val activeBook = allBooks.find { it.book.id == playerState.bookId } ?: return
    val currentTrack = activeBook.sortedTracks.getOrNull(playerState.currentTrackIndex) ?: return

    var showSpeedSliderDialog by remember { mutableStateOf(false) }
    var skipAdjustmentOpen by remember { mutableStateOf(false) }

    // Dynamic wave moving background gradient
    val containerBg = Color(0xFF141316)
    val cardBg = Color(0xFF1E1D22)

    val progressValue = if (playerState.durationMs > 0) playerState.currentPositionMs.toFloat() / playerState.durationMs else 0f
    
    // Auto slider drag position caching to enable dragging
    var sliderStateValue by remember { mutableStateOf<Float?>(null) }
    val effectiveProgress = sliderStateValue ?: progressValue

    BackHandler {
        // Simple back action slide
        navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(containerBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nav toolbar upper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "关闭", tint = Color(0xFF1A1C1E), modifier = Modifier.size(32.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("正在播讲", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF49454F)))
                Text(
                    text = playerState.bookTitle,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { navController.navigate("detail/${activeBook.book.id}") }) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "有声书设置", tint = Color(0xFF6750A4))
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))

        // Giant custom animated album turning disc
        Box(
            modifier = Modifier
                .padding(24.dp)
                .size(240.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(android.graphics.Color.parseColor(playerState.coverColorHex)),
                            Color(0xFFEADDFF)
                        )
                    )
                )
                .drawBehind {
                    // Vinyl grooves
                    for (i in 1..4) {
                        drawCircle(
                            color = Color(0xFF1A1C1E).copy(alpha = 0.05f),
                            radius = (size.width / 2f) * (i / 4.5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            BookCover(
                title = playerState.bookTitle,
                coverColorHex = playerState.coverColorHex,
                sizeDp = 110.dp,
                isSelected = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title text items
        Text(
            text = currentTrack.title,
            style = MaterialTheme.typography.headlineSmall.copy(color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "播放速度: ${String.format(Locale.US, "%.2f", playerState.playbackSpeed)}x  (有声变速)",
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showSpeedSliderDialog = true }
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.weight(0.4f))

        // Skip indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dynamic Skip Intro Active label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { skipAdjustmentOpen = !skipAdjustmentOpen }
                    .background(if (playerState.currentSkipIntroSec > 0) Color(0xFFEADDFF) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = if (playerState.currentSkipIntroSec > 0) accentColor else Color(0xFF49454F),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "跳片头: ${playerState.currentSkipIntroSec}s",
                    color = if (playerState.currentSkipIntroSec > 0) accentColor else Color(0xFF49454F),
                    fontSize = 11.sp
                )
            }

            // Dynamic Skip Outro Active label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { skipAdjustmentOpen = !skipAdjustmentOpen }
                    .background(if (playerState.currentSkipOutroSec > 0) Color(0xFFEADDFF) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = if (playerState.currentSkipOutroSec > 0) accentColor else Color(0xFF49454F),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "跳片尾: ${playerState.currentSkipOutroSec}s",
                    color = if (playerState.currentSkipOutroSec > 0) accentColor else Color(0xFF49454F),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress bar slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = effectiveProgress,
                onValueChange = { sliderStateValue = it },
                onValueChangeFinished = {
                    val targetMs = (effectiveProgress * playerState.durationMs).toLong()
                    viewModel.seekTo(targetMs)
                    sliderStateValue = null
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color(0xFFEADDFF),
                    thumbColor = accentColor
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (sliderStateValue != null) (effectiveProgress * playerState.durationMs).toLong() else playerState.currentPositionMs),
                    color = Color(0xFF49454F),
                    fontSize = 12.sp
                )
                Text(
                    text = formatTime(playerState.durationMs),
                    color = Color(0xFF49454F),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))

        // Primary Playback Buttons Row (Large circles, standard touch target sizes)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.playPrevious() },
                enabled = playerState.hasPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一集",
                    tint = if (playerState.hasPrevious) accentColor else Color(0xFFEADDFF),
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { viewModel.seekBackward() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RotateLeft,
                    contentDescription = "回退15秒",
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Central Core Play Pause FAB Node
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD0BCFF))
                    .clickable { viewModel.togglePlayPause() }
                    .testTag("play_pause_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color(0xFF21005D),
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = { viewModel.seekForward() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "快进15秒",
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = { viewModel.playNext() },
                enabled = playerState.hasNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一集",
                    tint = if (playerState.hasNext) accentColor else Color(0xFFEADDFF),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        // Sliding Panel for quick skip micro adjustments (快捷微调按钮如“-1s / +1s”)
        if (skipAdjustmentOpen) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("当前播讲微调 (实时生效)", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Skip intro adjuster line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("片头 (${playerState.currentSkipIntroSec}s)", color = Color(0xFF49454F), fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.updateBookSkipIntro(activeBook.book.id, (playerState.currentSkipIntroSec - 1).coerceAtLeast(0)) }) {
                                Text("-1s", color = accentColor, fontSize = 12.sp)
                            }
                            TextButton(onClick = { viewModel.updateBookSkipIntro(activeBook.book.id, (playerState.currentSkipIntroSec + 1).coerceAtMost(300)) }) {
                                Text("+1s", color = accentColor, fontSize = 12.sp)
                            }
                        }
                    }

                    Divider(color = Color(0xFFEADDFF), modifier = Modifier.padding(vertical = 4.dp))

                    // Skip outro adjuster line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("片尾 (${playerState.currentSkipOutroSec}s)", color = Color(0xFF49454F), fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.updateBookSkipOutro(activeBook.book.id, (playerState.currentSkipOutroSec - 1).coerceAtLeast(0)) }) {
                                Text("-1s", color = accentColor, fontSize = 12.sp)
                            }
                            TextButton(onClick = { viewModel.updateBookSkipOutro(activeBook.book.id, (playerState.currentSkipOutroSec + 1).coerceAtMost(300)) }) {
                                Text("+1s", color = accentColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            TextButton(
                onClick = { skipAdjustmentOpen = true },
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
            ) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("呼出片头/片尾秒数快捷微调控制器", fontSize = 11.sp)
            }
        }
    }

    // Interactive Speed Dial Selection Dialog
    if (showSpeedSliderDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedSliderDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("调整评书播音速度", color = Color(0xFF1A1C1E))
                }
            },
            text = {
                Column {
                    Text("针对有声书精编优化音质，变速不失真：", color = Color(0xFF49454F), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick buttons
                    val speeds = listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.8f, 2.0f, 2.5f, 3.0f)
                    val rows = speeds.chunked(4)
                    rows.forEach { rowIds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowIds.forEach { sp ->
                                val active = (playerState.playbackSpeed - sp).let { Math.abs(it) < 0.05f }
                                Button(
                                    onClick = { viewModel.setPlaybackSpeed(sp) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (active) accentColor else Color(0xFFEADDFF)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(3.dp)
                                ) {
                                    Text("${sp}x", color = if (active) Color.White else Color(0xFF6750A4), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Fine slider
                    Text("细致微调: ${String.format(Locale.US, "%.2f", playerState.playbackSpeed)}x", color = Color(0xFF1A1C1E), fontSize = 12.sp)
                    Slider(
                        value = playerState.playbackSpeed,
                        onValueChange = { viewModel.setPlaybackSpeed(it) },
                        valueRange = 0.5f..3.0f,
                        colors = SliderDefaults.colors(activeTrackColor = accentColor, inactiveTrackColor = Color(0xFFEADDFF), thumbColor = accentColor)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedSliderDialog = false }) {
                    Text("完成", color = accentColor)
                }
            },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// 6. Global Setup & Library Scanners Panel

@Composable
fun GlobalSettingsScreen(
    viewModel: MainViewModel,
    navController: NavController,
    accentColor: Color,
    cardBg: Color
) {
    val globalSettings by viewModel.globalSettings.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tempIntro by remember(globalSettings) { mutableStateOf(globalSettings.defaultSkipIntroSeconds) }
    var tempOutro by remember(globalSettings) { mutableStateOf(globalSettings.defaultSkipOutroSeconds) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color(0xFF1A1C1E))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("全局播放习惯设置", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        // Global Intro value card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.RotateRight, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("默认自动跳过片头", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "凡是选择‘使用全局设置’的小说，在开播新的一集或切集时将自动跳过片头 ${tempIntro} 秒。",
                    color = Color(0xFF49454F),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Button(
                            onClick = {
                                tempIntro = (tempIntro - 5).coerceAtLeast(0)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("-5s", color = accentColor)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                tempIntro = (tempIntro - 1).coerceAtLeast(0)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("-1s", color = accentColor)
                        }
                    }

                    Text(
                        text = "$tempIntro 秒",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row {
                        Button(
                            onClick = {
                                tempIntro = (tempIntro + 1).coerceAtMost(300)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+1s", color = accentColor)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                tempIntro = (tempIntro + 5).coerceAtMost(300)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+5s", color = accentColor)
                        }
                    }
                }
            }
        }

        // Global Outro value card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("默认自动跳过片尾 (触发播下一集)", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "当分集倒计时还剩 ${tempOutro} 秒时，播放器检测到触发条件，将自动切歌切换至下一折/集，解决累赘尾声。",
                    color = Color(0xFF49454F),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Button(
                            onClick = {
                                tempOutro = (tempOutro - 5).coerceAtLeast(0)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("-5s", color = accentColor)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                tempOutro = (tempOutro - 1).coerceAtLeast(0)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("-1s", color = accentColor)
                        }
                    }

                    Text(
                        text = "$tempOutro 秒",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row {
                        Button(
                            onClick = {
                                tempOutro = (tempOutro + 1).coerceAtMost(300)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+1s", color = accentColor)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                tempOutro = (tempOutro + 5).coerceAtMost(300)
                                viewModel.updateGlobalSettings(tempIntro, tempOutro)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+5s", color = accentColor)
                        }
                    }
                }
            }
        }

        // Scanning tools block
        Text("系统工具及有声书管理", color = Color(0xFF1A1C1E), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("正在处理中，请稍后...", color = Color(0xFF49454F))
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.scanLocalFolders(context) { count ->
                                Toast.makeText(context, "检索存储，成功增量导入 $count 部小说书籍", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(colors = listOf(Color(0xFF6750A4), Color(0xFF7F67BE))), RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("检索扫读系统音频存储 (Music/Download)", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.seedDemoBooks(context) {
                                Toast.makeText(context, "演示小说重置成功！随时可开始播放体验", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color(0xFFEADDFF), Color(0xFFD0BCFF)))),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("生成带有声声波的演示书（三体/西游记）", color = accentColor)
                    }
                }
            }
        }
    }
}

// 7. Micro bottom persistent controller bar (MiniPlayerBar)

@Composable
fun MiniPlayerBar(
    state: PlayerState,
    accentColor: Color,
    cardBg: Color,
    onPlayPause: () -> Unit,
    onClick: () -> Unit
) {
    val progressValue = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(cardBg)
    ) {
        // Continuous Progress Ribbon
        LinearProgressIndicator(
            progress = progressValue,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = accentColor,
            trackColor = Color(0xFFEADDFF)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover(
                title = state.bookTitle,
                coverColorHex = state.coverColorHex,
                sizeDp = 42.dp,
                isSelected = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentTrackTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color(0xFF1A1C1E),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "书: ${state.bookTitle} · (倍速 ${String.format(Locale.US, "%.1f", state.playbackSpeed)}x)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF49454F)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Simple Skip Indicator micro text
            Text(
                text = "${formatTime(state.currentPositionMs)}/${formatTime(state.durationMs)}",
                color = Color(0xFF49454F),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .background(accentColor, CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 8. Time formatter utility

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
