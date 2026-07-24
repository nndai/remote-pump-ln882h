package com.nndai.remotepump.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import com.nndai.remotepump.R

@Composable
fun LogScreen(
    viewModel: LogViewModel = viewModel(),
    bottomPadding: Dp = 0.dp
) {
    val isEnabled by viewModel.isLogEnabled.collectAsStateWithLifecycle()
    val logs = viewModel.logs
    val listState = rememberLazyListState()
    
    // Auto-scroll logic: only auto scroll if we are already at the bottom
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) {
                true
            } else {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex >= totalItems - 2
            }
        }
    }

    LaunchedEffect(logs.size) {
        if (isAtBottom && logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.log_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (logs.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${logs.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logs.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.log_clear_logs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = if (isEnabled) stringResource(R.string.log_listening) else stringResource(R.string.log_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { viewModel.setLogEnabled(it) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val horizontalScrollState = rememberScrollState()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                    .padding(8.dp)
            ) {
                items(logs) { msg ->
                    LogLine(msg)
                }
            }
        }

        // ── Raw JSON Command Input Row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var rawInput by remember { mutableStateOf("") }

            OutlinedTextField(
                value = rawInput,
                onValueChange = { rawInput = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(R.string.log_raw_json_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (rawInput.isNotBlank()) {
                            viewModel.sendRawJson(rawInput)
                            rawInput = ""
                        }
                    }
                )
            )

            Button(
                onClick = {
                    if (rawInput.isNotBlank()) {
                        viewModel.sendRawJson(rawInput)
                        rawInput = ""
                    }
                },
                modifier = Modifier.height(50.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = stringResource(R.string.log_send),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.log_send), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Dynamic IME Spacer to sit right on top of soft keyboard ──
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val extraImePadding = (imeBottom - bottomPadding).coerceAtLeast(0.dp)
        if (extraImePadding > 0.dp) {
            Spacer(modifier = Modifier.height(extraImePadding))
        }
    }
}

@Composable
private fun LogLine(msg: String) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val color = when (msg.firstOrNull()) {
        'V', 'T' -> if (isDark) Color(0xFFAAAAAA) else Color(0xFF757575)
        'D' -> if (isDark) Color(0xFF4FC3F7) else Color(0xFF0288D1)
        'I' -> if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)
        'W' -> if (isDark) Color(0xFFFFD54F) else Color(0xFFF57C00)
        'E', 'F' -> if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = msg,
        color = color,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        softWrap = false,
        lineHeight = 10.sp
    )
}
