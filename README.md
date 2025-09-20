# EliteLogs - for admins, by admin.

<img width="1024" height="1024" alt="image" src="https://github.com/user-attachments/assets/0533417a-3d93-4737-a78c-8cb6886b173d" />


> ✨ **EliteLogs** — a Minecraft plugin built for server admins who are done with messy, unreadable logs.  
> It neatly records everything that matters: player activity, system events, errors, and warnings.  
> With structured folders and per-player logs, you’ll always know where to look when chaos strikes.  


---

## 📦 Installation
1. Download the [latest release](https://github.com/SsilensioCrafter/EliteLogs/releases).
2. Drop `EliteLogs.jar` into your `plugins/` folder.
3. Restart your server.
4. Pretend you wrote it yourself and flex on your admin friends.

---

## ✨ Features
- Comprehensive logging: chat, commands, economy, combat, inventory, info, stats, console, sessions, warnings, errors, and more.
- Per-player logs with dedicated folders (`logs/<module>/players/<uuid>`) and session histories (`logs/players/<playerName>/sessions`).
- Global daily logs (`logs/<module>/global-YYYY-MM-DD.log`) for quick server-wide insights.
- Configurable modules — enable or disable exactly what you need via `config.yml`.
- Session reports for both server and players, stored separately for better tracking.
- Discord integration: send errors, warnings, sessions, and watchdog alerts directly to your channel.
- Inspector, metrics, suppressor, and watchdog subsystems included out of the box.
- Legacy mode available for flat player log files, if you miss the old days.
- Built-in localization packs (EN, RU, DE, FR, ES) with graceful English fallback for missing keys.
- Written with more caffeine than code — but stable enough to trust your server with.

## 🧩 Version compatibility
| Диапазон | Что работает |
| --- | --- |
| 1.8.x – 1.12.x | Листенеры используют рефлексию для `getItemInMainHand` и старых событий подбора (`PlayerPickupItemEvent`), поэтому сборка сохраняет весь инвентарный функционал даже на старых ядрах. |
| 1.13.x – 1.20.6 | Современные события (`EntityPickupItemEvent`) и коллекционный `Bukkit.getOnlinePlayers()` используются, когда доступны, без ломки на старых версиях. |
| 1.21.x | Достаточно собрать проект с зависимостью `spigot-api:1.21.x` — совместимый слой продолжает работать без изменений, остаётся только прогнать тесты. |

---

## 🌐 Localization
- Ships with translated message packs for English, Russian, German, French, and Spanish.
- Set the `language` key in `config.yml` to swap bundles; the plugin falls back to English if a string is missing.
- Custom translations can be dropped into `plugins/EliteLogs/lang/<code>.yml` — they take precedence over the bundled files on reload.

---

## 🗺️ Roadmap
- [x] Add Warn & Reports logging  
- [ ] Database support  
- [ ] Fancy web panel (because who doesn’t love dashboards)  
- [ ] Maybe AI log summarizer (so ChatGPT can tell you who’s sus)  

---

## 🤝 Contributing
Wanna vibe-code with me?  

1. Fork this repo  
2. Create a new branch (`git checkout -b feature/your-idea`)  
3. Commit your changes (`git commit -m '✨ add cool stuff'`)  
4. Push the branch (`git push origin feature/your-idea`)  
5. Open a Pull Request  
6. Bonus points if your PR description includes a meme  

---

## 📜 License
This project uses the [MIT License](LICENSE).  

Basically:  

- ✅ You can use it  
- ✅ You can modify it  
- ✅ You can share it  
- ❌ Don’t blame me if your server catches fire 🔥  

If you want to treat it like a **"Do What The Heck You Want License"**, go for it.  

---

## ⚙️ Config
Yes, it has a config. Even your laziest admin can use it:

```yaml
# ============================
#  EliteLogs Configuration
#  vibe-coded © 2025
# ============================

# Main switches
enabled: true     # Enable/disable EliteLogs
debug: false      # Debug mode (prints extra info, very spammy in console)

# Plugin language
language: en      # Options: ru | en

# ANSI color codes for messages
ansi:
  enabled: true
  color-ok: "§a"       # Color for success messages
  color-warn: "§e"     # Color for warnings
  color-fail: "§c"     # Color for errors/fails
  reset: "§f"          # Reset color (usually white)

# ASCII banner on server startup
banner:
  enabled: true
  show-version: true
  style: block         # Options: block | mini
  color: default       # Options: default | green | cyan | magenta

# Discord webhook integration
discord:
  enabled: false
  webhook-url: ""             # Insert your Discord webhook URL
  rate-limit-seconds: 10      # Minimum delay between messages
  send:
    errors: true
    warns: true
    sessions: true
    watchdog: true
    inspector: true

# Logging system
logs:
  rotate: true                # Rotate logs (create new files)
  keep-days: 30               # Keep logs for X days (-1 = forever)
  archive: true               # Archive old logs (zip/tar)
  split-by-player: true       # Write per-player logs in module folders
  legacy:
    flat-player-files: false  # Old style: player-Name-YYYY-MM-DD.log (not recommended)
  types:
    info: true
    warns: true
    errors: true
    chat: true
    commands: true
    players: true             # Keep traditional logs/players/<name>/sessions
    combat: true
    inventory: true
    economy: true
    stats: true
    console: true
    suppressed: true

# Player sessions summary
sessions:
  enabled: true
  autosave-minutes: 10        # Auto-save session summary every N minutes
  save-global: true           # Write global session reports to logs/sessions/global
  save-players: true          # Write per-player session reports to logs/sessions/players/<uuid>

# Inspector — collects server info
inspector:
  enabled: true
  include-mods: true
  include-configs: true
  include-garbage: true
  include-server-info: true

# Metrics (server health monitoring)
metrics:
  enabled: true
  interval-seconds: 60

# Message suppressor / spam filter
suppressor:
  enabled: true
  mode: blacklist             # Options: blacklist | whitelist
  spam-limit: 1000
  filters: []                 # List of filters (regex or keywords)

# Watchdog — emergency watchdog
watchdog:
  enabled: true
  tps-threshold: 5.0          # Trigger if TPS falls below this value
  error-threshold: 50         # Trigger if errors exceed this number
  actions:
    run-inspector: true       # Run inspector on trigger
    create-crash-report: true # Generate crash report
    discord-alert: true       # Send alert to Discord
