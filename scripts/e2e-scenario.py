#!/usr/bin/env python3
"""Turn something captured on MCLabs into an in-game E2E scenario.

    scripts/e2e-scenario.py ~/Downloads/mines.jsonl casino-mines
    scripts/e2e-scenario.py latest.log bounty-hunt --grep 'Bounty »'
    scripts/e2e-scenario.py message-24.txt police-prestige --title 'Prestige Progress'
    scripts/e2e-scenario.py 2026-09-05-7.log session-0905 --local --fast

Sources: a gui-dumper capture (.jsonl), a Minecraft log (anything else with [CHAT]
lines), or a component dump (a JSON array of {slot, item}) opened as one menu.

Scenarios go to src/gametest/e2e/scenarios, which is committed to a public repo, so
player chat is dropped: only lines that read as the server's own are kept. --local
writes to the gitignored src/gametest/e2e/local instead and keeps everything.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOG_LINE = re.compile(r"^\[(\d\d):(\d\d):(\d\d)\] .*?\[CHAT\] (.*)$")
# Another client mod's chat timestamps, baked into some older logs.
CLIENT_STAMP = re.compile(r"^§e\[\d\d:\d\d:\d\d\] §r")
PLAYER_CHAT = re.compile(r"^\[|^[\w ]+ \| \w{3,16}: |^\w{3,16}: ")


def is_player_chat(plain):
    return bool(PLAYER_CHAT.match(plain))


def from_log(path, grep, fast):
    records = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = LOG_LINE.match(line)
        if not m:
            continue
        # Minecraft logs a message's line breaks as a literal \n.
        plain = CLIENT_STAMP.sub("", m.group(4)).replace("\\n", "\n")
        if grep and not re.search(grep, plain):
            continue
        record = {"type": "chat", "plain": plain}
        if not fast:
            h, mi, s = (int(x) for x in m.groups()[:3])
            record = {"type": "chat", "t": ((h * 60 + mi) * 60 + s) * 1000, "plain": plain}
        records.append(record)
    return records


def from_capture(path, grep, fast):
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        record.pop("tagged", None)
        if record["type"] in ("chat", "actionbar") and grep and not re.search(grep, record.get("plain", "")):
            continue
        if fast:
            record.pop("t", None)
        records.append(record)
    return records


def from_dump(path, title):
    slots = json.loads(path.read_text(encoding="utf-8"))
    full = {str(s["slot"]): s["item"] for s in slots}
    size = max(9, -(-(max(int(k) for k in full) + 1) // 9) * 9)
    return [
        {"type": "note", "text": f"{path.name}; menu title {'as given' if title else 'not captured'}."},
        {"type": "screen", "title": title or "Menu", "containerSlots": min(size, 54)},
        {"type": "full", "slots": full},
        {"type": "wait", "ticks": 10},
        {"type": "close"},
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path)
    parser.add_argument("name")
    parser.add_argument("--local", action="store_true", help="gitignored folder; keep player chat")
    parser.add_argument("--grep", help="keep only chat/actionbar lines matching this regex")
    parser.add_argument("--fast", action="store_true", help="drop timestamps; play everything back to back")
    parser.add_argument("--title", help="menu title for a component dump")
    args = parser.parse_args()

    text = args.source.read_text(encoding="utf-8", errors="replace")
    if text.lstrip().startswith("["):
        records = from_dump(args.source, args.title)
    elif text.lstrip().startswith("{"):
        records = from_capture(args.source, args.grep, args.fast)
    else:
        records = from_log(args.source, args.grep, args.fast)

    dropped = 0
    if not args.local:
        kept = [r for r in records if r["type"] not in ("chat", "actionbar") or not is_player_chat(r.get("plain", ""))]
        dropped = len(records) - len(kept)
        records = kept

    out = ROOT / "src/gametest/e2e" / ("local" if args.local else "scenarios") / f"{args.name}.jsonl"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in records), encoding="utf-8")
    print(f"{out.relative_to(ROOT)}: {len(records)} records" + (f", {dropped} player chat lines dropped" if dropped else ""))


if __name__ == "__main__":
    sys.exit(main())
