#!/usr/bin/env python3
"""UBEOD001 Before/After ブラウザ E2E の録画スクリプト (UNT6-10 / s4.2)

使い方: python3 docs/migration/e2e/record.py before|after
  before: main (COBOL 版) のチェックアウト  ($BEFORE_REPO, 既定 ~/repos/Usagi-Bank)
  after : 移行ブランチ (Java 版) のチェックアウト ($AFTER_REPO, 既定 = このファイルのリポジトリ)
仮想ディスプレイ :99 (1920x1080) に Chromium (左) と xterm (右) を並べ、ffmpeg x11grab で録画する。
アプリは録画前に起動しておき (H2 は起動ごとに初期化)、終わったら止める。
出力: ~/e2e/<leg>/ (作業ファイル・録画 mp4) と docs/migration/e2e/screenshots/ (スクショ)
必要なもの: JDK 8, Maven, cobc, Xvfb, xterm, xdotool, xclip, ffmpeg, curl, pip の playwright,
            Playwright の Chromium (~/.cache/ms-playwright/chromium-*), Noto Sans CJK
"""
import glob
import os
import signal
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright

LEG = sys.argv[1] if len(sys.argv) > 1 else ""
if LEG not in ("before", "after"):
    sys.exit("使い方: record.py before|after")

HERE = Path(__file__).resolve().parent
AFTER_REPO = Path(os.environ.get("AFTER_REPO", HERE.parents[2]))
BEFORE_REPO = Path(os.environ.get("BEFORE_REPO", Path.home() / "repos/Usagi-Bank"))
REPO = BEFORE_REPO if LEG == "before" else AFTER_REPO
WORK = Path.home() / "e2e" / LEG
SHOTS = HERE / "screenshots"
DISPLAY = ":99"
W, H, SPLIT = 1920, 1080, 1060
BASE = "http://localhost:8080/usagi"
JAVA_HOME = "/usr/lib/jvm/java-8-openjdk-amd64"
LABEL = "Before: main（COBOL 版）" if LEG == "before" else "After: cobol-to-java（Java 版）"
ACCOUNTS = [("001", "1000001"), ("005", "5000002")]
GREP = "grep -E '^D(0011000001|0055000002)' {}"

env = dict(os.environ, DISPLAY=DISPLAY, JAVA_HOME=JAVA_HOME, LANG="C.UTF-8")
procs = []


def sh(cmd, **kw):
    return subprocess.run(cmd, shell=True, env=env, check=True, **kw)


def up(url):
    try:
        return urllib.request.urlopen(url, timeout=2).status == 200
    except Exception:
        return False


def start_display():
    if subprocess.run(["xdpyinfo", "-display", DISPLAY], capture_output=True).returncode != 0:
        procs.append(subprocess.Popen(["Xvfb", DISPLAY, "-screen", "0", f"{W}x{H}x24", "-nolisten", "tcp"]))
        time.sleep(2)


def start_app():
    if up(BASE + "/login"):
        sys.exit("ポート 8080 でアプリが動いている。先に止める")
    log = open(WORK / "app.log", "w")
    p = subprocess.Popen(["mvn", "-B", "-q", "spring-boot:run"], cwd=REPO, env=env,
                         stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(300):
        if up(BASE + "/login"):
            return p
        if p.poll() is not None:
            sys.exit(f"アプリが起動しなかった ({WORK}/app.log)")
        time.sleep(1)
    sys.exit("アプリが 300 秒で起動しなかった")


def start_xterm():
    rc = WORK / ".bashrc"
    color = "33" if LEG == "before" else "36"
    rc.write_text(
        f"cd {WORK}\nexport JAVA_HOME={JAVA_HOME} LANG=C.UTF-8\n"
        f"PS1='\\[\\e[1;{color}m\\][{LEG}: {REPO.name}]\\[\\e[0m\\] \\W $ '\n"
        f"PROMPT_COMMAND='echo $((++__n)) > {WORK}/.prompt'\n")
    (WORK / ".prompt").write_text("")
    cols = (W - SPLIT) // 9
    p = subprocess.Popen(
        ["xterm", "-u8", "-fa", "DejaVu Sans Mono", "-fd", "Noto Sans Mono CJK JP", "-fs", "11", "-bg", "#1e1e1e", "-fg", "#e8e8e8",
         "-geometry", f"{cols}x60+{SPLIT}+0", "-T", "terminal", "-e", "bash", "--rcfile", str(rc), "-i"],
        env=env)
    procs.append(p)
    wait_prompt(0)
    return p


def prompt_count():
    t = (WORK / ".prompt").read_text().strip()
    return int(t) if t else 0


def wait_prompt(prev, timeout=180):
    for _ in range(timeout * 10):
        if prompt_count() > prev:
            return
        time.sleep(0.1)
    raise RuntimeError("コマンドが終わらない")


def term(line, pause=1.2):
    """xterm に 1 行入力して実行し、プロンプトが戻るまで待つ。ASCII 以外はクリップボード貼付け"""
    sh("xdotool search --name '^terminal$' windowfocus --sync")
    n = prompt_count()
    if line.isascii():
        subprocess.run(["xdotool", "type", "--delay", "18", line], env=env, check=True)
    else:
        subprocess.run(["xclip", "-selection", "primary"], input=line.encode(), env=env, check=True)
        sh("xdotool key shift+Insert")
    time.sleep(0.3)
    sh("xdotool key Return")
    wait_prompt(n)
    time.sleep(pause)


CAPTION_JS = """
(() => {
  const draw = () => {
    let d = document.getElementById('__e2e_cap');
    if (!d) { d = document.createElement('div'); d.id = '__e2e_cap'; document.body.appendChild(d); }
    d.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;padding:10px 16px;' +
      'background:rgba(20,20,20,.88);color:#fff;font:bold 20px "Noto Sans CJK JP",sans-serif;';
    d.textContent = sessionStorage.getItem('__e2e_cap') || '';
  };
  window.__e2eCap = (t) => { sessionStorage.setItem('__e2e_cap', t); draw(); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', draw); else draw();
})();
"""

HIGHLIGHT_JS = """
() => {
  const th = [...document.querySelectorAll('th')].find(e => e.textContent.trim() === '未払利息');
  const tr = th.closest('tr');
  tr.style.outline = '4px solid #e53935'; tr.style.background = '#fff3cd';
  tr.scrollIntoView({block: 'center'});
  return tr.querySelector('td').textContent.trim();
}
"""


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    for f in WORK.glob("*.DAT"):
        f.unlink()
    for f in ("UBEOD001.LOG", "ubeod001", "import.json"):
        (WORK / f).unlink(missing_ok=True)
    SHOTS.mkdir(exist_ok=True)
    start_display()
    print("アプリ起動中:", REPO)
    app = start_app()
    start_xterm()
    chrome = sorted(glob.glob(str(Path.home() / ".cache/ms-playwright/chromium-*/chrome-linux*/chrome")))[-1]
    values = {}
    with sync_playwright() as pw:
        browser = pw.chromium.launch(
            executable_path=chrome, headless=False, env=env,
            args=[f"--window-position=0,0", f"--window-size={SPLIT},{H}", "--lang=ja-JP",
                  "--disable-features=PasswordLeakDetection,Translate", "--no-first-run"])
        ctx = browser.new_context(no_viewport=True, locale="ja-JP")
        ctx.add_init_script(CAPTION_JS)
        page = ctx.new_page()
        page.goto(BASE + "/login")
        time.sleep(1)
        rec = subprocess.Popen(
            ["ffmpeg", "-y", "-loglevel", "error", "-f", "x11grab", "-draw_mouse", "0", "-framerate", "15",
             "-video_size", f"{W}x{H}", "-i", DISPLAY, "-c:v", "libx264", "-preset", "veryfast",
             "-pix_fmt", "yuv420p", str(WORK / f"{LEG}.mp4")], stdin=subprocess.PIPE, env=env)
        time.sleep(1)

        def cap(t):
            page.evaluate("t => window.__e2eCap(t)", f"{LABEL} ─ {t}")
            time.sleep(1.5)

        def open_account(branch, no, phase, shot):
            page.click("#nav >> text=口座検索")
            page.wait_for_load_state()
            time.sleep(0.8)
            page.click(f"a[href$='/accounts/{branch}/{no}']")
            page.wait_for_load_state()
            v = page.evaluate(HIGHLIGHT_JS)
            values[(phase, branch, no)] = v
            time.sleep(1.5)
            page.screenshot(path=str(SHOTS / f"{LEG}-{shot}-{branch}-{no}.png"))
            time.sleep(1)

        def desktop_shot(name):
            sh(f"ffmpeg -y -loglevel error -f x11grab -draw_mouse 0 -video_size {W}x{H} -i {DISPLAY} "
               f"-frames:v 1 {SHOTS}/{LEG}-{name}.png")

        cap("1. admin でログイン")
        page.type("input[name=username]", "admin", delay=80)
        page.type("input[name=password]", "admin123", delay=80)
        time.sleep(0.5)
        page.click("button:has-text('ログイン')")
        page.wait_for_load_state()
        cap("2. 口座照会: バッチ前の未払利息を確認")
        for b, n in ACCOUNTS:
            open_account(b, n, "前", "1-pre")

        cap("3. 端末: アプリから ACCOUNTS.DAT を取得")
        term("# 手順 3: アプリから ACCOUNTS.DAT を取得 (GET /api/batch/accounts-file)", 0.5)
        term(f"curl -s -u admin:admin123 -o ACCOUNTS.DAT {BASE}/api/batch/accounts-file && wc -l ACCOUNTS.DAT")
        term(GREP.format("ACCOUNTS.DAT"), 2)
        if LEG == "before":
            cap("4. 端末: COBOL 版 UBEOD001 を実行")
            term("# 手順 4: COBOL 版 UBEOD001 (GnuCOBOL) をビルドして実行", 0.5)
            term(f"cobc -x -o ubeod001 {REPO}/batch/cobol/UBEOD001.cbl && ls -l ubeod001")
            term("./ubeod001; echo \"RC=$?\"")
        else:
            cap("4. 端末: Java 版 UBEOD001 を実行")
            term("# 手順 4: Java 版 UBEOD001 を実行 (batch/java/ubeod001.sh)", 0.5)
            term(f"{REPO}/batch/java/ubeod001.sh; echo \"RC=$?\"")
        term("cat UBEOD001.LOG")
        term(GREP.format("ACCRUED.DAT") + "; tail -1 ACCRUED.DAT", 2.5)
        cap("5. 端末: ACCRUED.DAT をアプリに取込")
        term("# 手順 5: ACCRUED.DAT を取込 (POST /api/batch/accrued-file)", 0.5)
        term(f"curl -s -u admin:admin123 -F file=@ACCRUED.DAT {BASE}/api/batch/accrued-file | tee import.json; echo", 2)
        if LEG == "after":
            term("# Before (COBOL 版) の出力とバイト比較", 0.5)
            term("for f in ACCRUED.DAT UBEOD001.LOG import.json; do cmp $f ../before/$f && echo \"$f: COBOL 版と一致\"; done", 2.5)
        desktop_shot("2-terminal")

        cap("6. 口座照会: バッチ後の未払利息を確認")
        for b, n in ACCOUNTS:
            open_account(b, n, "後", "3-post")
        desktop_shot("4-final")
        summary = " / ".join(f"{b}-{n}: {values[('前', b, n)]} → {values[('後', b, n)]}" for b, n in ACCOUNTS)
        cap("未払利息 " + summary)
        time.sleep(3)
        rec.communicate(b"q", timeout=30)
        browser.close()
    (WORK / "values.txt").write_text(summary + "\n")
    print(summary)
    os.killpg(app.pid, signal.SIGTERM)
    app.wait(timeout=60)


try:
    main()
finally:
    for p in procs:
        p.terminate()
