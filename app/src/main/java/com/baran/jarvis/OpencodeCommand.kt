package com.baran.jarvis

/**
 * Builds the shell command that is sent to Termux.
 *
 * Flow: Termux shell -> proot-distro login ubuntu -> bash -lc '<opencode run ...>'
 * The message is passed as a single-quoted argument; shQuote() makes it safe
 * to embed inside both the inner and outer single-quoted shell strings.
 *
 * Model: opencode/hy3-free (ucretsiz). Farkli bir model istersen MODEL'i degistir.
 *
 * NOTE: opencode >= 1.17 dropped the `--format json` / `-q` flags on `run`,
 * so we rely on the default text output and let OutputParser fall back to
 * ANSI-stripped stdout. The JSON branch is kept for older versions.
 */
object OpencodeCommand {

    const val MODEL = "opencode/hy3-free"

    fun build(message: String): String {
        val msgQuoted = shQuote(message)
        val inner = "cd ~/jarvis && opencode run --continue -m $MODEL -- $msgQuoted"
        return "mkdir -p ~/jarvis && proot-distro login ubuntu -- bash -lc ${shQuote(inner)}"
    }

    /** Diagnostics command used by the "Durum" (status) button. */
    fun diagnose(): String =
        "proot-distro list; echo '---'; proot-distro login ubuntu -- bash -lc " +
            shQuote("echo OC=\$(command -v opencode || echo YOK)")

    private fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
