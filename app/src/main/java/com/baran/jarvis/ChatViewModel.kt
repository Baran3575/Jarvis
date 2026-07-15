package com.baran.jarvis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val file = File(TermuxSession.context.filesDir, "chat.json")

    init {
        _messages.value = load()
        if (!TermuxSession.isTermuxInstalled()) {
            _status.value = "Termux yüklü değil. Önce Termux ve Termux:API kur, sonra izin ver."
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isBlank() || _busy.value) return
        if (!TermuxSession.hasRunPermission()) {
            _messages.value = _messages.value + Message(
                "Termux RUN_COMMAND izni verilmedi. Üstteki 'İzni Aç (Ayarlar)' " +
                    "butonuyla izni etkinleştir, sonra tekrar dene.",
                false
            )
            return
        }
        // Komut arka planda calisir (EXTRA_BACKGROUND=true); Termux UI'su acilmaz.
        // Termux hizmeti kapali olsa bile RUN_COMMAND servisi uyanir.
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            _messages.value = _messages.value + Message(t, true)
            save()
            try {
                val res = TermuxSession.run(OpencodeCommand.build(t))
                val reply = when {
                    res.err != -1 ->
                        "Termux hatası: ${res.errmsg.ifBlank { res.stderr }.ifBlank { "bilinmeyen hata" }}"
                    res.stdout.isNotBlank() -> OutputParser.parse(res.stdout)
                    res.stderr.isNotBlank() -> "opencode: ${res.stderr}"
                    else -> "(boş yanıt)"
                }
                _messages.value = _messages.value + Message(reply, false)
            } catch (e: Exception) {
                _messages.value = _messages.value + Message("İstem hatası: ${e.message}", false)
            } finally {
                _busy.value = false
                save()
            }
        }
    }

    fun diagnose() {
        viewModelScope.launch(Dispatchers.IO) {
            _status.value = "Kurulum kontrol ediliyor…"
            val sb = StringBuilder()
            sb.appendLine("Termux kurulu: ${TermuxSession.isTermuxInstalled()}")
            sb.appendLine("RUN_COMMAND izni: ${TermuxSession.hasRunPermission()}")
            if (!TermuxSession.isTermuxInstalled()) {
                _status.value = sb.toString()
                return@launch
            }
            try {
                val res = TermuxSession.run(OpencodeCommand.diagnose(), 30_000)
                sb.appendLine("--- çıktı ---")
                sb.appendLine(res.stdout)
                if (res.err != -1) sb.appendLine("HATA: ${res.errmsg}")
            } catch (e: Exception) {
                sb.appendLine("Kontrol hatası: ${e.message}")
            }
            _status.value = sb.toString()
        }
    }

    fun clearStatus() {
        _status.value = null
    }

    private fun load(): List<Message> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Message(o.getString("t"), o.getBoolean("u"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            for (m in _messages.value) {
                arr.put(JSONObject().apply {
                    put("t", m.text)
                    put("u", m.isUser)
                })
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
            // best effort persistence
        }
    }
}
