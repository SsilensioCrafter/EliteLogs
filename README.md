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

## 🛠️ Compatibility
- ✅ **Spigot** 1.20 – 1.21.x  
- ✅ **Paper** 1.20 – 1.21.x  
- ☕ Requires **Java 17+** (recommended: Java 21)  
- ❓ Other versions **might work but are not tested**  

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
- Written with more caffeine than code — but stable enough to trust your server with.

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
#  Version: 1.0.0
#  (All comments are English only)
# ============================
# Encoding: UTF-8 (no BOM)
# Indentation: 2 spaces (tabs are NOT allowed)

# --- Core settings ---
enabled: true        # Enable/disable the plugin
debug: false         # Enable debug mode (verbose logs)
language: en         # Language of messages (en, ru, etc.)

# --- ANSI colors in console ---
ansi:
  enabled: true       # Enable colored console output
  color-ok: "§a"      # Success messages (green)
  color-warn: "§e"    # Warnings (yellow)
  color-fail: "§c"    # Errors (red)
  reset: "§f"         # Reset color (white/default)

# --- Startup banner ---
banner:
  enabled: true        # Show banner on server startup
  show-version: true   # Show plugin version in banner
  style: block         # Banner style: block / mini / none
  color: default       # Banner color theme

# --- Discord webhook integration ---
discord:
  enabled: false                 # Enable Discord notifications
  webhook-url: ""                # Your Discord webhook URL
  rate-limit-seconds: 10         # Rate-limit for messages
  send:                          # Which events to send to Discord
    errors: true
    warns: true
    sessions: true
    watchdog: true
    inspector: true

# --- Logs settings ---
logs:
  rotate: true                   # Rotate logs daily
  keep-days: 30                  # Keep logs (days)
  archive: true                  # Archive old logs
  split-by-player: true          # Separate player logs
  legacy:
    flat-player-files: false     # Old flat format for player logs
  types:                         # Enable/disable specific log types
    info: true
    warns: true
    errors: true
    chat: true
    commands: true
    players: true
    combat: true
    inventory: true
    economy: true
    stats: true
    console: true
    rcon: true
    suppressed: true

# --- Session tracker ---
sessions:
  enabled: true                  # Enable session tracking
  autosave-minutes: 10           # Interval for auto-saving sessions
  save-global: true              # Save global session logs
  save-players: true             # Save individual player sessions

# --- Inspector tool ---
inspector:
  enabled: true                  # Enable inspector (system reports)
  include-mods: true             # Include mods/plugins list
  include-configs: true          # Include configs
  include-garbage: true          # Include memory/GC info
  include-server-info: true      # Include system/server info

# --- Metrics ---
metrics:
  enabled: true                  # Enable metrics collection
  interval-seconds: 60           # Interval for metrics saving

# --- Message suppressor ---
suppressor:
  enabled: true                  # Enable suppressor (spam filter)
  mode: blacklist                # Mode: blacklist / whitelist
  spam-limit: 1000               # Max messages before suppression
  cache-max-entries: 10000       # Max cache size
  cache-ttl-seconds: 300         # Cache time-to-live
  filters: []                    # Add regex filters if needed

# --- Watchdog ---
watchdog:
  enabled: true                  # Enable watchdog (server health check)
  tps-threshold: 5.0             # TPS threshold to trigger actions
  error-threshold: 50            # Error threshold to trigger actions
  actions:                       # Actions to perform on trigger
    run-inspector: true          # Run inspector automatically
    create-crash-report: true    # Generate crash report
    discord-alert: true          # Send Discord alert
