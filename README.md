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

## 📖 Usage

- `/elogs help` → show this help message  
- `/elogs reload` → reload the plugin configuration  
- `/elogs inspector` → run the Inspector for quick analysis of sessions, chat, commands, and errors  
- `/elogs metrics` → display logging metrics and statistics  
- `/elogs export` → export logs into external-friendly formats  
- `/elogs rotate [force]` → archive old logs (add `force` to rotate immediately)  
- `/elogs logs` → list available log categories (chat, commands, sessions, etc.)  
- `/elogs version` → show the current plugin version  

---

## ✨ Features
- Comprehensive logging: chat, commands, economy, combat, inventory, stats, console, sessions, warnings, errors, and more.
- Per-player logs with dedicated folders (`logs/<module>/players/<uuid>`) and session histories (`logs/players/<playerName>/sessions`).
- Global daily logs (`logs/<module>/global-YYYY-MM-DD.log`) for quick server-wide insights.
- Configurable modules — enable or disable exactly what you need via `config.yml`.
- Session reports for both server and players, stored separately for better tracking.
- Discord integration: send errors, warnings, sessions, and watchdog alerts directly to your channel.
- Inspector, metrics, suppressor, and watchdog subsystems included out of the box.
- Watchdog can auto-run the inspector, prepare crash reports, and now exposes its full runtime state via the HTTP API.
- Lightweight HTTP API exposes live metrics and recent logs for external dashboards.
- Legacy mode available for flat player log files, if you miss the old days.
- Built-in localization packs (EN, RU, DE, FR, ES) with graceful English fallback for missing keys.
- Written with more caffeine than code — but stable enough to trust your server with.

## 🧩 Version compatibility
| Range | What works |
| --- | --- |
| 1.8.x – 1.12.x | Listeners use reflection for `getItemInMainHand` and legacy pickup events (`PlayerPickupItemEvent`), so the build retains full inventory functionality even on older server cores. |
| 1.13.x – 1.20.6 | Modern events (`EntityPickupItemEvent`) and the collection-based `Bukkit.getOnlinePlayers()` are used when available, without breaking on older versions. |
| 1.21.x | Simply build the project with the `spigot-api:1.21.x` dependency — the compatibility layer continues to work without changes, you only need to run tests. |

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

## 🔌 HTTP API
EliteLogs ships with an optional HTTP server so your SsilensioWeb admin panel (or any other dashboard) can pull data straight from the plugin. Enable it by flipping the `api.enabled` flag in `config.yml` and adjusting the bind address/port if needed.

### Endpoints
- `GET /api/v1/status` — plugin version, enabled modules, and configuration flags.
- `GET /api/v1/metrics` — live TPS/CPU/memory plus the active session counters and watchdog thresholds.
- `GET /api/v1/watchdog` — watchdog thresholds, trigger timings, error counters, and the most recent trigger reason.
- `GET /api/v1/logs` — list of log categories currently buffered in memory.
- `GET /api/v1/logs/<category>?limit=100` — most recent lines for a category (limit defaults to the configured `log-history`).

### Authentication
- Leave `auth-token` empty for open access, or set a string and pass it as the `X-API-Key` header (or `token` query parameter).
- `log-history` controls how many lines are cached per category for instant API responses.
- Bind the server to `127.0.0.1` when using a reverse proxy; use `0.0.0.0` only if you really want to expose it publicly.

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
language: en      # Options: en | ru | de | fr | es

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
    warns: true
    errors: true
    chat: true
    commands: true
    players: true             # Includes join/quit events and per-player folders
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

# HTTP API (for dashboards)
api:
  enabled: false
  bind: "127.0.0.1"
  port: 9173
  auth-token: ""
  log-history: 250

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

