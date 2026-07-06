<div align="center">

# 💱 expTrade

### Trade XP between players, with a chest-GUI request queue.

![](https://img.shields.io/badge/Fabric-DBA463?style=for-the-badge&logoColor=white)&nbsp;![](https://img.shields.io/badge/NeoForge-F16436?style=for-the-badge&logoColor=white)&nbsp;![](https://img.shields.io/badge/Paper-2A9DF4?style=for-the-badge&logoColor=white)&nbsp;

[![](https://img.shields.io/badge/Download_on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/project/exptrade)&nbsp;[![](https://img.shields.io/badge/Download_on-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/exptrade-mod)

![](https://img.shields.io/badge/Minecraft-26.x_%7C_1.21.x-62B47A?style=flat-square) ![](https://img.shields.io/badge/Side-Single_Player_%26_Server-8E44AD?style=flat-square) ![](https://img.shields.io/badge/Fabric_API-required_on_Fabric-4A90D9?style=flat-square) ![](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

</div>

---

Ever finished a mob grinder with 40 spare levels while a friend is scraping to enchant their first pickaxe? **expTrade** lets players hand XP back and forth like any other resource — give it, ask for it, and confirm the exchange through a clean chest-GUI queue. No dropping bottles o' enchanting on the floor, no trust games: every transfer is explicit, previewed before it happens, and time-boxed so stale offers clean themselves up.

It's fully **server-side** — a **vanilla client** can join a modded server and it just works. Runs in single-player too. No new blocks, no new items, no library mods: just `/exptrade` and a virtual chest menu built from vanilla items.

## ✨ Features

- **Give or request, either direction.** Push your own XP to someone with `give`, or ask them for some with `request`. The other player always gets the final say.
- **Levels or raw XP.** Trade a round number of **levels** or an exact **raw XP** amount — or `all` to empty your bar in one go. The mod does the vanilla XP-curve math for you.
- **A live chest-GUI queue.** `/exptrade pending` opens a virtual chest showing every incoming offer as its own row: a **clock** ticking down the time left, the sender's **player head**, an **offer/request** marker, a **details** page, and one-click **Accept** / **Decline** buttons.
- **Per-player queue with TTL.** Each player has their own incoming queue (default cap **10**). Offers expire automatically after a timeout (default **60s**), notifying both sides — nothing lingers forever.
- **Threshold protection.** Set a personal level floor with `/exptrade threshold` and the mod refuses any trade that would drop you below it. Great insurance against fat-fingering `give … all`.
- **Balance preview before you commit.** Every offer shows a **before → after** readout of both players' levels and XP, so nobody agrees blind.
- **Clickable chat controls.** Offers come with `[View Queue]`, `[Cancel]`, and (for admins) `[Confirm]` / `[Cancel]` buttons wired straight to the commands.
- **Double-checked at execution.** Amounts are validated when the offer is made **and re-validated the instant it's accepted** — if the giver spent their XP in the meantime, the trade fails cleanly instead of going negative.
- **Admin transfers.** Operators can move XP between any two online players with a two-step confirm, bypassing thresholds — handy for events, refunds, and prizes.
- **No item risk.** The GUI slots are display-only; you can't pull items out or drop your own in. The only interactive slots are Accept and Decline.

## 🔧 How it works

### The trade flow

1. **Someone starts a trade.** `A` runs `/exptrade give B levels 10` (offer) or `/exptrade request B levels 10` (ask). The mod validates that the giver actually has the XP and won't dip below their threshold, then drops a **pending trade** into the responder's queue.
2. **Both players see a preview.** The initiator gets a confirmation with a `[Cancel]` button; the responder gets a message with a `[View Queue]` button — each showing the **before → after** levels and XP for both sides.
3. **The responder decides.** They `/exptrade accept` / `decline`, or open `/exptrade pending` and click the buttons. On accept, the giver's XP is deducted using the real vanilla level curve and the receiver is credited the equivalent raw XP.
4. **Requests convert fairly.** When you `request` a number of **levels**, the mod converts it to the raw XP those levels are worth **at your current level** and checks the target actually has that much — so "10 levels" always means the same amount of XP regardless of who's paying.

### The chest-GUI queue

`/exptrade pending` opens a generic chest sized to your queue (one row per pending trade, up to 6). Each row is laid out with vanilla items:

| Slot | Item | Meaning |
|:---:|:---|:---|
| 0 | 🕐 **Clock** | Time remaining on the offer, counting down live (refreshes every second) |
| 1 | 🧑 **Player head** | Who sent the trade |
| 2 | 🪣 **Bucket** | **Water bucket = Offer** (they're giving you XP) · **Empty bucket = Request** (they want yours) |
| 3 | 📄 **Paper** | Trade details — sender, type, and amount |
| 4–6 | — | Empty spacers |
| 7 | 🧭 **Recovery compass** | **Accept** the trade |
| 8 | 🧭 **Compass** | **Decline** the trade |

Clicking Accept or Decline processes that row and immediately reopens the menu with your remaining trades — or closes it when the queue is empty. Every other slot is inert, and your own inventory is locked while the menu is open, so there's zero risk of losing items.

### The queue &amp; TTL

Each player owns an **incoming** queue keyed to them. Multiple people can have offers waiting for you at once, up to the configured cap (`maxQueueSize`, default 10) — try to pile on past that and you're told the queue is full. Every offer carries an expiry timestamp (`timeoutSeconds` from now, default 60s); a server tick sweeps the queues and, when an offer times out, removes it and notifies both the sender and the recipient. Accepting a trade re-checks the giver's balance at that exact moment, so an offer that was valid a minute ago but no longer is simply fails with a clear message instead of paying out.

## ⌨️ Commands

Everything lives under `/exptrade`. `<player>` targets are online players; amounts are positive integers, or the literal `all` to move your entire bar.

| Command | What it does |
|:---|:---|
| `/exptrade give <player> levels <amount>` | Offer that many **levels** to a player |
| `/exptrade give <player> levels all` | Offer **all** your XP (expressed as levels) |
| `/exptrade give <player> exp <amount>` | Offer that many points of **raw XP** |
| `/exptrade give <player> exp all` | Offer **all** your raw XP |
| `/exptrade request <player> levels <amount>` | Ask a player for that many **levels** (converted to raw XP at your level) |
| `/exptrade request <player> levels all` | Ask a player for **all** their XP |
| `/exptrade request <player> exp <amount>` | Ask a player for that much **raw XP** |
| `/exptrade request <player> exp all` | Ask a player for **all** their XP |
| `/exptrade accept [<player>]` | Accept the first offer in your queue — or the one from a specific `<player>` |
| `/exptrade decline [<player>]` | Decline the first offer — or the one from a specific `<player>` |
| `/exptrade cancel` | Cancel your own outgoing offer |
| `/exptrade pending` | Open the chest-GUI trade queue |
| `/exptrade threshold <levels>` | Set your personal minimum level floor (0 = no floor) |

### Admin commands

Require operator / gamemaster-level permission.

| Command | What it does |
|:---|:---|
| `/exptrade admin transfer <from> <to> levels\|exp <amount>` | Stage an XP transfer between two players (bypasses thresholds) |
| `/exptrade admin transfer <from> <to> levels\|exp all` | Stage a transfer of **all** the source player's XP |
| `/exptrade admin confirm` | Confirm your staged transfer |
| `/exptrade admin cancel` | Discard your staged transfer |
| `/exptrade config timeout <seconds>` | Set the offer timeout (10–300s) and save |
| `/exptrade config maxqueue <size>` | Set the per-player queue cap (1–50) and save |
| `/exptrade config reload` | Reload the config file from disk |

## 💡 Use cases

- **Bankroll a friend.** Sit on a mob farm and top up a teammate who's grinding enchants — `/exptrade give Steve levels 30`.
- **Pay for a service.** Trade XP as currency for builds, loot, or map-art commissions: the recipient runs `/exptrade request Buyer levels 15` and gets paid on accept.
- **Even out after a death.** Split a big haul so nobody's stuck at level 0 after a lava incident.
- **Run events &amp; prizes.** Admins hand out XP rewards to winners with `/exptrade admin transfer`, or reclaim it for do-overs.
- **Protect a hoard.** Set `/exptrade threshold 30` before offering `all`, so a mistaken trade can never drop you below your enchant stash.
- **Skill-share economies.** On SMPs, let players who love grinding sell XP to players who'd rather build — no bottle-throwing, no floor-drops.

## ⚙️ Configuration

Settings live in **`config/exptrade.json`** (created on first launch). Both values are also editable live in-game via `/exptrade config …`, which writes the file for you; `/exptrade config reload` re-reads it from disk.

| Key | Default | Range | Meaning |
|:---|:---:|:---:|:---|
| `timeoutSeconds` | `60` | 10–300 | How long a pending offer stays in the queue before it auto-expires |
| `maxQueueSize` | `10` | 1–50 | Maximum number of pending offers a single player can have waiting at once |

Per-player thresholds set with `/exptrade threshold` are stored separately in **`config/exptrade/playerdata.json`** and persist across restarts.

## 📦 Versions &amp; downloads

> [!NOTE]
> This repo uses a **branch-per-version** layout. This `main` branch is **documentation only** — the code for each Minecraft version lives on its own branch, each with an independent history and its own `CHANGELOG.md`.

| Branch | Minecraft | Loaders | Dependencies | Log |
|:------:|:---------:|:-------:|:------------:|:---:|
| [`multi_26.2`](https://github.com/LunixiaLIVE/expTrade/tree/multi_26.2) | 26.2.x | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expTrade/blob/multi_26.2/CHANGELOG.md) |
| [`multi_26.1`](https://github.com/LunixiaLIVE/expTrade/tree/multi_26.1) | 26.1, 26.1.1, 26.1.2 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expTrade/blob/multi_26.1/CHANGELOG.md) |
| [`multi_1.21.11`](https://github.com/LunixiaLIVE/expTrade/tree/multi_1.21.11) | 1.21.11 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expTrade/blob/multi_1.21.11/CHANGELOG.md) |
| [`multi_1.21.9`](https://github.com/LunixiaLIVE/expTrade/tree/multi_1.21.9) | 1.21.9–1.21.10 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expTrade/blob/multi_1.21.9/CHANGELOG.md) |
| [`plugin_1.21.11`](https://github.com/LunixiaLIVE/expTrade/tree/plugin_1.21.11) | 1.21.11 | Paper | Paper (no extra deps) | — |

> [!TIP]
> Every `multi_*` branch builds **one jar that runs on both Fabric and NeoForge**. On 26.x that's a shared universal jar (Minecraft is unobfuscated there); on 1.21.x it's a jar-in-jar bundle (`-multi.jar`) with the Fabric and NeoForge builds nested inside, each loader picking its own. Per-loader `-fabric` / `-neoforge` jars are produced too (`build/staging/`). Fully self-contained — **no extra library mods to install**.

<details>
<summary>🛠️ <b>Building from source</b></summary>

Each code branch is a self-contained Gradle project. Grab the branch for your Minecraft version:

```bash
git clone -b multi_26.2 https://github.com/LunixiaLIVE/expTrade.git
cd expTrade
./gradlew build
```

The universal jar lands in `build/libs/` — drop it into your `mods/` folder on either loader.
</details>

## 📄 License

Released under the **MIT License**.

<div align="center"><sub>⛏️ Part of <a href="https://github.com/LunixiaLIVE/Lunixia-Minecraft-QOL-Mods">Lunixia's Minecraft QOL Mods</a>.</sub></div>
