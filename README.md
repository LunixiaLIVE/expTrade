# expTrade

A server-side Fabric mod that lets players give XP levels to each other using a command-based offer/accept system.

## Commands

| Command | Description |
|---|---|
| `/exptrade give <player> <levels>` | Offer to give levels to another player |
| `/exptrade give <player> all` | Offer to give all your XP (leaves you at exactly 0) |
| `/exptrade request <player> <levels>` | Request levels from another player |
| `/exptrade request <player> all` | Request all XP from another player |
| `/exptrade accept` | Accept a pending trade |
| `/exptrade decline` | Decline a pending trade |
| `/exptrade cancel` | Cancel your outgoing trade |
| `/exptrade threshold <levels>` | Set your personal minimum level floor |
| `/exptrade config timeout <seconds>` | (Admin) Set the trade timeout (10–300s) |
| `/exptrade config reload` | (Admin) Reload the config file |

## Config

Located at `config/exptrade.json`:

```json
{
  "timeoutSeconds": 60
}
```

## Details

- Two trade directions: **give** (giver initiates) or **request** (receiver initiates, target must approve)
- XP is calculated using Minecraft's actual per-level cost formula — giving 5 levels at level 30 transfers more raw XP than giving 5 levels at level 5
- The giver loses exactly N whole levels; the receiver gains the equivalent raw XP (which may level them up differently depending on their current level)
- Trades expire after the configured timeout (default 60 seconds)
- Players cannot trade below level 0 or below their personal threshold
- The `all` option transfers total raw XP, leaving the giver at exactly 0
- Per-player thresholds persist across restarts

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.18.2+
- Fabric API
