package com.baran.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Izni kullanici Ayarlar'dan dondugunda yeniden kontrol et
    var permOk by remember { mutableStateOf(TermuxSession.hasRunPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) permOk = TermuxSession.hasRunPermission()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jarvis") },
                actions = {
                    TextButton(onClick = { vm.diagnose() }) { Text("Durum") }
                    TextButton(onClick = { TermuxSession.openTermux() }) { Text("Termux") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!permOk) {
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Termux RUN_COMMAND izni verilmedi.",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Ayarlar'dan 'Termux'da komut çalıştır' iznini etkinleştir. " +
                                "Termux'da da 'allow-external-apps = true' olmalı.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { openAppSettings(context) }) {
                            Text("İzni Aç (Ayarlar)")
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { m ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(12.dp),
                            color = if (m.isUser)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(m.text, Modifier.padding(12.dp))
                        }
                    }
                }
                if (busy) {
                    item { Text("Jarvis düşünüyor…", Modifier.padding(12.dp)) }
                }
            }

            status?.let {
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        it,
                        Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Jarvis'e bir şey söyle…") },
                    enabled = !busy
                )
                Button(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                    enabled = !busy && input.isNotBlank()
                ) { Text("Gönder") }
            }
        }
    }
}

fun openAppSettings(ctx: Context) {
    val i = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", ctx.packageName, null)
    )
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}
