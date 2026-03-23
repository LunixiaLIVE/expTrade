# expTrade

A server-side Fabric mod that lets players give XP levels to each other using a command-based offer/accept system.

## Commands

| Command | Description |
|---|---|
| `/exptrade give <player> <levels>` | Offer a number of levels to another player |
| `/exptrade give <player> all` | Offer all your XP (leaves you at exactly 0) |
| `/exptrade accept` | Accept a pending trade offer |
| `/exptrade decline` | Decline a pending trade offer |
| `/exptrade cancel` | Cancel your outgoing trade offer |
| `/exptrade threshold <levels>` | Set your personal minimum level floor |
| `/exptrade config timeout <seconds>` | (Admin) Set the offer timeout (10–300s) |
| `/exptrade config reload` | (Admin) Reload the config file |

## Config

Located at `config/exptrade.json`:

```json
{
  "timeoutSeconds": 60
}
```

## Details

- Trades are one-way gifts — the giver offers, the receiver accepts or declines
- Offers expire after the configured timeout (default 60 seconds)
- Players cannot trade below level 0 or below their personal threshold
- The `all` option transfers total raw XP, leaving the giver at exactly 0
- Per-player thresholds persist across restarts

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.18.2+
- Fabric API
