package com.example.whatsapp.presentation.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.R
import com.example.whatsapp.presentation.chat_box.ChatListModel

private val AppBg = Color(0xFF0B0F11)
private val TopBar = Color(0xFF0B0F11)
private val SurfaceDark = Color(0xFF171C1F)
private val SurfaceMuted = Color(0xFF20272A)
private val Primary = Color(0xFFE7EAEC)
private val Secondary = Color(0xFF9CA5A9)
private val Green = Color(0xFF21C063)
private val GreenSoft = Color(0xFF173C2A)
private val Pink = Color(0xFFD44368)
private val Divider = Color(0xFF20272A)
private val Missed = Color(0xFFFF4B62)

enum class MainTab(val label: String) {
    Chats("Chats"),
    Updates("Library"),
    Communities("Communities"),
    Calls("Calls"),
    You("You"),
}

@Composable
fun ModernAppScaffold(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    title: String,
    onSettingsClick: () -> Unit = {},
    showCamera: Boolean = true,
    showSearch: Boolean = true,
    showMenu: Boolean = true,
    accentColor: Color = Pink,
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ModernTopBar(
                title = title,
                onSettingsClick = onSettingsClick,
                showCamera = showCamera,
                showSearch = showSearch,
                showMenu = showMenu,
                accentColor = accentColor,
            )

            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues(0.dp))
            }

            ModernBottomBar(
                selected = selectedTab,
                onSelected = onTabSelected,
                accentColor = accentColor,
            )
        }

        if (floatingAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 116.dp),
            ) {
                floatingAction()
            }
        }
    }
}

@Composable
private fun ModernTopBar(
    title: String,
    onSettingsClick: () -> Unit,
    showCamera: Boolean,
    showSearch: Boolean,
    showMenu: Boolean,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 4.dp)
            .height(64.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = if (title == "Night") accentColor else Primary,
            fontSize = if (title == "Night") 26.sp else 24.sp,
            fontWeight = if (title == "Night") FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )

        if (showCamera) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = Primary,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        if (showSearch) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Primary,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        if (showMenu) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ModernBottomBar(
    selected: MainTab,
    onSelected: (MainTab) -> Unit,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBar)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        BottomItem(
            tab = MainTab.Chats,
            selected = selected == MainTab.Chats,
            icon = Icons.Default.Chat,
            onClick = onSelected,
            accentColor = accentColor,
        )
        BottomItem(
            tab = MainTab.Updates,
            selected = selected == MainTab.Updates,
            icon = Icons.Default.Folder,
            onClick = onSelected,
            accentColor = accentColor,
        )
        BottomItem(
            tab = MainTab.You,
            selected = selected == MainTab.You,
            icon = Icons.Default.Person,
            onClick = onSelected,
            accentColor = accentColor,
        )
    }
}

@Composable
private fun BottomItem(
    tab: MainTab,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (MainTab) -> Unit,
    accentColor: Color,
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clickable { onClick(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color =
                if (selected) {
                    accentColor.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tab.label,
                tint = if (selected) accentColor else Secondary,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 5.dp)
                    .size(22.dp),
            )
        }
        Text(
            text = tab.label,
            color = if (selected) accentColor else Secondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

data class ChatPreviewRow(
    val name: String,
    val message: String,
    val time: String,
    @DrawableRes val avatar: Int,
    val unread: Int = 0,
    val muted: Boolean = false,
    val pinned: Boolean = false,
)

private fun fallbackChats() = listOf(
    ChatPreviewRow("Night UI", "Summary synced • chat, library and memory", "Now", R.drawable.ic_night, unread = 2, pinned = true),
    ChatPreviewRow("Sora architecture", "Summary ready • extensions and media flow", "Yesterday", R.drawable.ic_night),
    ChatPreviewRow("Azure models", "Astra pricing and model routing", "Yesterday", R.drawable.ic_night, unread = 1),
    ChatPreviewRow("Research notes", "6 messages • 2 Library references", "Thursday", R.drawable.ic_night),
    ChatPreviewRow("App planning", "Summary ready • Android implementation", "Wednesday", R.drawable.ic_night),
)

@Composable
fun ModernChatsTab(
    chats: List<ChatListModel>,
    onTabSelected: (MainTab) -> Unit,
    onChatClick: (ChatListModel) -> Unit,
    onNewChat: () -> Unit = {},
    onSettingsClick: () -> Unit,
    accentColor: Color = Pink,
) {
    val rows = remember(chats) {
        if (chats.isEmpty()) {
            emptyList()
        } else {
            val avatars = listOf(
                R.drawable.bilal,
                R.drawable.harib,
                R.drawable.taimoor,
                R.drawable.hannan_ahmad,
                R.drawable.abdussalam,
                R.drawable.salleh,
            )
            chats.mapIndexed { index, item ->
                ChatPreviewRow(
                    name = item.name ?: "Contact",
                    message = item.message ?: "Tap to open chat",
                    time = item.time ?: "",
                    avatar = avatars[index % avatars.size],
                )
            }
        }
    }

    ModernAppScaffold(
        selectedTab = MainTab.Chats,
        onTabSelected = onTabSelected,
        title = "Night",
        onSettingsClick = onSettingsClick,
        accentColor = accentColor,
        floatingAction = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = accentColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .size(56.dp)
                    .semantics {
                        contentDescription = "New chat FAB"
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchPill(
                placeholder = "Search your chats",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FilterChip("All", selected = true, accentColor = accentColor)
                FilterChip("Unread", accentColor = accentColor)
                FilterChip("Favorites", accentColor = accentColor)
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 84.dp),
            ) {
                items(rows) { row ->
                    ChatRow(
                        row = row,
                        accentColor = accentColor,
                        onClick = {
                            val match = chats.firstOrNull { (it.name ?: "Contact") == row.name }
                            if (match != null) onChatClick(match)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill(
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(45.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceMuted)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = placeholder,
            color = Secondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean = false,
    accentColor: Color = Pink,
) {
    Surface(
        color =
            if (selected) {
                accentColor.copy(alpha = 0.18f)
            } else {
                SurfaceDark
            },
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else Secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ChatRow(
    row: ChatPreviewRow,
    accentColor: Color = Pink,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(row.avatar),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    color = Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.time,
                    color = if (row.unread > 0) accentColor else Secondary,
                    fontSize = 10.sp,
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.message,
                    color = Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (row.unread > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = row.unread.toString(),
                            color = Color(0xFF07110B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernUpdatesTab(
    onTabSelected: (MainTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    ModernAppScaffold(
        selectedTab = MainTab.Updates,
        onTabSelected = onTabSelected,
        title = "Updates",
        onSettingsClick = onSettingsClick,
        floatingAction = {
            FloatingActionButton(
                onClick = {},
                containerColor = Green,
                contentColor = Color(0xFF08110C),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(24.dp))
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 86.dp),
        ) {
            item {
                Text(
                    text = "Status",
                    color = Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 8.dp),
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        StatusStory(
                            name = "My status",
                            avatar = R.drawable.bilal,
                            mine = true,
                        )
                    }
                    items(
                        listOf(
                            "Shayan" to R.drawable.shahyan,
                            "Saleh" to R.drawable.salleh,
                            "Haider" to R.drawable.haider,
                            "Mr Beast" to R.drawable.mrbeast,
                            "Taimoor" to R.drawable.taimoor,
                        )
                    ) { pair ->
                        StatusStory(
                            name = pair.first,
                            avatar = pair.second,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Divider)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 14.dp, end = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Channels",
                        color = Primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Explore",
                        color = Green,
                        fontSize = 12.sp,
                    )
                }
            }

            items(
                listOf(
                    Triple("Night", "New build notes and product updates", R.drawable.whatsapp_icon),
                    Triple("Mr Beast", "Latest video just dropped", R.drawable.mrbeast),
                    Triple("Butt Brothers", "New post • 14 min ago", R.drawable.taimoor),
                    Triple("Talal Vines", "Today’s upload", R.drawable.talal),
                )
            ) { item ->
                ChannelRow(
                    name = item.first,
                    subtitle = item.second,
                    avatar = item.third,
                )
            }
        }
    }
}

@Composable
private fun StatusStory(
    name: String,
    @DrawableRes avatar: Int,
    mine: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp),
    ) {
        Box {
            Surface(
                color = if (mine) SurfaceDark else Green,
                shape = CircleShape,
                modifier = Modifier.size(62.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(avatar),
                        contentDescription = null,
                        modifier = Modifier
                            .size(55.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (mine) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(21.dp)
                        .clip(CircleShape)
                        .background(Green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF07110B),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Text(
            text = name,
            color = Primary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ChannelRow(
    name: String,
    subtitle: String,
    @DrawableRes avatar: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(avatar),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = Secondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Surface(
            color = GreenSoft,
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = "Follow",
                color = Green,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun ModernCommunitiesTab(
    onTabSelected: (MainTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    ModernAppScaffold(
        selectedTab = MainTab.Communities,
        onTabSelected = onTabSelected,
        title = "Communities",
        onSettingsClick = onSettingsClick,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 86.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {}
                        .padding(horizontal = 14.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(SurfaceMuted),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(27.dp),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Green),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF07110B),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "New community",
                        color = Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .background(Color(0xFF111618))
                )
            }

            items(
                listOf(
                    CommunityPreview(
                        "Night Builders",
                        "3 groups",
                        R.drawable.img,
                        "Announcements",
                        "Dawson: Android build is ready",
                        "07:02",
                    ),
                    CommunityPreview(
                        "Anime & Manga",
                        "5 groups",
                        R.drawable.mrbeast,
                        "Sora Extension Lab",
                        "Episode resolver updated",
                        "Yesterday",
                    ),
                    CommunityPreview(
                        "Friends",
                        "2 groups",
                        R.drawable.harib,
                        "General",
                        "Second Child: 😂😂",
                        "Tuesday",
                    ),
                )
            ) { item ->
                CommunityCard(item)
            }
        }
    }
}

data class CommunityPreview(
    val name: String,
    val groups: String,
    @DrawableRes val image: Int,
    val groupName: String,
    val message: String,
    val time: String,
)

@Composable
private fun CommunityCard(item: CommunityPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(AppBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(item.image),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.groups,
                    color = Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Secondary,
                modifier = Modifier.size(22.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 76.dp, end = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(GreenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.groupName,
                    color = Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.message,
                    color = Secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.time,
                color = Secondary,
                fontSize = 9.sp,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(Color(0xFF111618))
        )
    }
}

@Composable
fun ModernCallsTab(
    onTabSelected: (MainTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    ModernAppScaffold(
        selectedTab = MainTab.Calls,
        onTabSelected = onTabSelected,
        title = "Calls",
        onSettingsClick = onSettingsClick,
        floatingAction = {
            FloatingActionButton(
                onClick = {},
                containerColor = Green,
                contentColor = Color(0xFF07110B),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(26.dp))
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 86.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Green),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = Color(0xFF07110B),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Create call link",
                            color = Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Share a link for your WhatsApp call",
                            color = Secondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                Text(
                    text = "Favorites",
                    color = Primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        listOf(
                            "Second Child" to R.drawable.harib,
                            "Muhammad" to R.drawable.bilal,
                            "Hannan" to R.drawable.hannan_ahmad,
                            "Saleh" to R.drawable.salleh,
                        )
                    ) { pair ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(66.dp),
                        ) {
                            Image(
                                painter = painterResource(pair.second),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                text = pair.first,
                                color = Primary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "Recent",
                    color = Primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 4.dp),
                )
            }

            items(
                listOf(
                    CallPreview("Second Child", "Today, 06:43", R.drawable.harib, missed = false, video = false),
                    CallPreview("Mr Beast", "Yesterday, 22:18", R.drawable.mrbeast, missed = true, video = true),
                    CallPreview("Shahyan Ahmad", "Yesterday, 17:02", R.drawable.shahyan, missed = false, video = false),
                    CallPreview("Hannan Ahmad", "Thursday, 11:31", R.drawable.hannan_ahmad, missed = true, video = false),
                    CallPreview("Taimoor Arshad", "Wednesday, 09:10", R.drawable.taimoor, missed = false, video = true),
                )
            ) { item ->
                CallRow(item)
            }
        }
    }
}

data class CallPreview(
    val name: String,
    val time: String,
    @DrawableRes val avatar: Int,
    val missed: Boolean,
    val video: Boolean,
)

@Composable
private fun CallRow(item: CallPreview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(item.avatar),
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (item.missed) Missed else Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = if (item.missed) "↙" else "↗",
                    color = if (item.missed) Missed else Green,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.time,
                    color = Secondary,
                    fontSize = 11.sp,
                )
            }
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = if (item.video) Icons.Default.VideoCall else Icons.Default.Call,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun ModernSettingsScreen(
    onBack: () -> Unit,
    displayName: String = "Dawson",
    onProfileClick: () -> Unit = {},
    onProvidersClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onMemoryClick: () -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onScriptsClick: () -> Unit = {},
    onMediaLibraryClick: () -> Unit = {},
    onBrowserClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
) {
    val rows = listOf(
        SettingsRow("providers", Icons.Default.AutoAwesome, "AI & providers", "Models, API keys and capability routing"),
        SettingsRow("appearance", Icons.Default.Palette, "Appearance", "Theme, accent, wallpaper and chat text"),
        SettingsRow("memory", Icons.Default.Memory, "Memory", "Summaries, checkpoints and cross-chat context"),
        SettingsRow("scheduled", Icons.Default.Schedule, "Scheduled", "Tasks Night can run later"),
        SettingsRow("scripts", Icons.Default.Code, "Scripts & projects", "JavaScript commands, utilities and web projects"),
        SettingsRow("media", Icons.Default.Movie, "Media library", "Anime, manga, music and saved extension media"),
        SettingsRow("browser", Icons.Default.Language, "Browser", "Night browser sessions and verification"),
        SettingsRow("privacy", Icons.Default.Lock, "Privacy", "Local data, permissions and provider credentials"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(58.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Primary,
                )
            }
            Text(
                text = "Night settings",
                color = Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onProfileClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = SurfaceDark,
                shape = CircleShape,
                modifier = Modifier.size(58.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = Primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Night profile • tap to edit",
                    color = Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Secondary,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(Color(0xFF111618))
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            items(rows, key = { it.id }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (row.id) {
                                "providers" -> onProvidersClick()
                                "appearance" -> onAppearanceClick()
                                "memory" -> onMemoryClick()
                                "scheduled" -> onSchedulesClick()
                                "scripts" -> onScriptsClick()
                                "media" -> onMediaLibraryClick()
                                "browser" -> onBrowserClick()
                                "privacy" -> onPrivacyClick()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(23.dp),
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.title,
                            color = Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = row.subtitle,
                            color = Secondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

data class SettingsRow(
    val id: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
)
