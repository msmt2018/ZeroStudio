package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared visual language for the ten mobile-first Git system mock screens. */
internal object GitMockColor {
    val background = Color(0xFF101318)
    val panel = Color(0xFF161A22)
    val card = Color(0xFF252B36)
    val cardSoft = Color(0xFF202632)
    val stroke = Color(0xFF303744)
    val text = Color(0xFFF2F5FA)
    val secondary = Color(0xFF9BA7B7)
    val muted = Color(0xFF687486)
    val blue = Color(0xFF2F80ED)
    val blueDark = Color(0xFF173A69)
    val green = Color(0xFF33C481)
    val greenBg = Color(0xFF143A2B)
    val red = Color(0xFFFF5C6C)
    val redBg = Color(0xFF4A2028)
    val yellow = Color(0xFFFFC857)
    val purple = Color(0xFF8A63D2)
}

internal object GitMockText {
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = GitMockColor.text)
    val top = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GitMockColor.text)
    val body = TextStyle(fontSize = 13.sp, color = GitMockColor.text)
    val small = TextStyle(fontSize = 10.sp, color = GitMockColor.secondary)
    val mono = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = GitMockColor.secondary)
}

@Composable
internal fun GitPhoneScreen(content: @Composable Column.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GitMockColor.background)
            .padding(12.dp),
        content = content,
    )
}

@Composable
internal fun GitTopBar(title: String, back: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back) {
            Text("‹", style = GitMockText.title)
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = GitMockText.top, modifier = Modifier.weight(1f))
        Text("⋮", style = GitMockText.top.copy(color = GitMockColor.secondary))
    }
}

@Composable
internal fun GitCard(modifier: Modifier = Modifier, radius: Dp = 10.dp, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(GitMockColor.card, RoundedCornerShape(radius))
            .border(1.dp, GitMockColor.stroke, RoundedCornerShape(radius)),
    ) {
        content()
    }
}

@Composable
internal fun GitPill(text: String, color: Color = GitMockColor.blue, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(color, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = GitMockText.body.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
internal fun GitDividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GitMockColor.stroke))
}

@Composable
internal fun GitTimelineDot(active: Boolean = true) {
    Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(GitMockColor.blueDark))
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(if (active) GitMockColor.blue else GitMockColor.stroke),
        )
    }
}

@Composable
internal fun GitFileIcon(label: String, tint: Color) {
    Box(Modifier.size(26.dp).background(tint.copy(alpha = .18f), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
        Text(label, style = GitMockText.small.copy(color = tint, fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun GitBottomHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(56.dp).height(3.dp).background(GitMockColor.text, RoundedCornerShape(3.dp)))
    }
}
