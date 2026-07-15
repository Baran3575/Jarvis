#!/usr/bin/env python3
"""
Jarvis test harness  (APK gerektirmez)
---------------------------------------
Uygulamanin iki cekirdek fonksiyonunu birebir mirror eder ve calistirir:

  * OpencodeCommand.build()  -> shell komutu uretimi + guvenli quote
  * OutputParser.parse()     -> opencode cikisini (JSON veya ANSI'li text) parse

Testler:
  1) shQuote kacis testi  : curluk, yeni satir, $, backtick iceren mesajlar
                             bash uzerinden geri alininca ozuna esit mi?
  2) Entegrasyon          : uretilen komutun IC kismi gercek `bash -lc`
                             ile, sahte opencode beyni uezerinden calistirilir.
  3) Hafiza / devam       : iki cagri; ikincisi ilkinde ogrenilen adi hatirlar.
  4) JSON fixture         : opencode JSONL ornegi dogru parse ediliyor mu?
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


# ---------- app kodunun birebir mirror'i ----------
def sh_quote(s: str) -> str:
    return "'" + s.replace("'", "'\\''") + "'"


# app'in OpencodeCommand.MODEL'i ile ayni deger olmali
MODEL = "opencode/hy3-free"


def build_inner(msg: str) -> str:
    return f"cd ~/jarvis && opencode run --continue -m {MODEL} -- " + sh_quote(msg)


def build_command(msg: str) -> str:
    inner = build_inner(msg)
    return "mkdir -p ~/jarvis && proot-distro login ubuntu -- bash -lc " + sh_quote(inner)


def parse_output(raw: str) -> str:
    texts = []
    for line in raw.split("\n"):
        t = line.strip()
        if not t.startswith("{"):
            continue
        try:
            o = json.loads(t)
            if o.get("type") == "text":
                p = o.get("part") or {}
                txt = p.get("text")
                if txt:
                    texts.append(txt)
        except Exception:
            pass
    joined = "".join(texts)
    return joined if joined.strip() else ANSI.sub("", raw).strip()


# ---------- yardimcilar ----------
PASS = 0
FAIL = 0


def check(name: str, cond: bool, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [OK]   {name}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name}  {detail}")


def run_bash_lc(inner: str, env: dict) -> str:
    r = subprocess.run(
        ["bash", "-lc", inner],
        capture_output=True, text=True, env=env, timeout=30,
    )
    return r.stdout


# ---------- 1) kacis testi ----------
def test_escape():
    print("\n[1] shQuote kacis testi")
    cases = [
        "merhaba",
        "ali'nin arabasi",
        "satir1\nsatir2",
        "fiyat $10 ve `cmd`",
        "cift '' tirnak ve \\ ters",
        "opencode run -- 'x' && rm -rf /",
        "özel Türkçe karakter: çğıöşü",
    ]
    for c in cases:
        out = subprocess.run(
            ["bash", "-c", "printf '%s' " + sh_quote(c)],
            capture_output=True, text=True,
        ).stdout
        check(f"escape: {c!r}", out == c, f"-> {out!r}")


# ---------- 2+3) entegrasyon + hafiza ----------
def test_integration():
    print("\n[2/3] Entegrasyon + hafiza (sahte opencode beyni)")
    tmp = tempfile.mkdtemp(prefix="jarvis_test_")
    bindir = os.path.join(tmp, "bin")
    os.makedirs(bindir)
    mock = os.path.join(bindir, "opencode")
    shutil.copy(os.path.join(os.path.dirname(__file__), "opencode_mock"), mock)
    os.chmod(mock, 0o755)
    jarvis = os.path.join(tmp, "jarvis")
    os.makedirs(jarvis)

    env = os.environ.copy()
    env["HOME"] = tmp
    env["PATH"] = bindir + ":" + env.get("PATH", "")

    # ilk mesaj: ismini soyler
    inner1 = build_inner("Merhaba, benim adım Baran ve sahibin sensin.")
    out1 = run_bash_lc(inner1, env)
    parsed1 = parse_output(out1)
    check("ANSI kodlari temizlendi", "\x1b" not in parsed1 and "Merhaba" in parsed1,
          f"-> {parsed1!r}")
    check("hafiza dosyasi yazildi", os.path.exists(os.path.join(jarvis, "memory.md")))

    # ikinci mesaj: adi hatirlamali (--continue)
    inner2 = build_inner("Adım ne?")
    out2 = run_bash_lc(inner2, env)
    parsed2 = parse_output(out2)
    check("devam eden oturum adi hatirladi", "Baran" in parsed2,
          f"-> {parsed2!r}")

    # uretilen TAM komut (app'in Termux'a yolladigi sey) dogru mu?
    full = build_command("Adım ne?")
    expected = ("mkdir -p ~/jarvis && proot-distro login ubuntu -- bash -lc "
                "'cd ~/jarvis && opencode run --continue -m opencode/hy3-free -- '\\''Adım ne?'\\'''")
    check("uretilen komut beklenen sekilde", full == expected, f"-> {full!r}")


# ---------- 4) JSON fixture ----------
def test_json_fixture():
    print("\n[4] opencode JSONL fixture parse")
    fixture = (
        '{"type":"step_start","timestamp":1,"sessionID":"s1","part":{"type":"step-start"}}\n'
        '{"type":"text","timestamp":2,"sessionID":"s1","part":{"type":"text","text":"Merhaba "}}\n'
        '{"type":"text","timestamp":3,"sessionID":"s1","part":{"type":"text","text":"dunya"}}\n'
        '{"type":"step_finish","timestamp":4,"sessionID":"s1","part":{"type":"step-finish","reason":"stop"}}\n'
    )
    got = parse_output(fixture)
    check("JSON metni birlestirdi", got == "Merhaba dunya", f"-> {got!r}")


if __name__ == "__main__":
    test_escape()
    test_integration()
    test_json_fixture()
    print(f"\n=== SONUC: {PASS} gecti, {FAIL} kaldi ===")
    sys.exit(1 if FAIL else 0)
