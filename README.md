# EliteLogs

> ✨ A Minecraft plugin I **vibe-coded at 3AM** for server admins tired of chaos.  
> Logs everything. Keeps it clean. Makes you feel like a pro (even if you’re just doomscrolling console).

---

## 📦 Installation
1. Download the [latest release](https://github.com/SsilensioCrafter/EliteLogs/releases).
2. Drop `EliteLogs.jar` into your `plugins/` folder.
3. Restart your server.
4. Pretend you wrote it yourself and flex on your admin friends.

---

## ✨ Features
- Logs everything an admin could dream of (except your bad decisions).
- Per-player folders so you can stalk… I mean **monitor** responsibly.
- Modules you can toggle, because freedom is cool.
- Built with more caffeine than code.

---

## 🗺️ Roadmap
- [ ] Add Warn & Reports logging  
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
  style: block         # block | mini (visual style for "LOGS")
  color: default       # default | green | cyan | magenta

# Discord webhook integration
discord:
  enabled: false
  webhook-url: ""             # Insert your Discord webhook URL
  rate-limit-seconds: 10      # Minimum delay between messages
  send:                       # What to send to Discord
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
  split-by-player: true       # Split logs into per-player folders
  types:                      # Types of logs to record
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
    suppressed: true

# Player sessions summary
sessions:
  enabled: true
  autosave-minutes: 10        # Auto-save session summary every N minutes

# Inspector — collects server info
inspector:
  enabled: true
  include-mods: true          # List of loaded mods
  include-configs: true       # Config files
  include-garbage: true       # Garbage collector info
  include-server-info: true   # Server version, plugins, etc.

# Metrics (server health monitoring)
metrics:
  enabled: true
  interval-seconds: 60        # Collection interval (seconds)

# Message suppressor / spam filter
suppressor:
  enabled: true
  mode: blacklist             # blacklist (block) | whitelist (allow only these)
  spam-limit: 1000            # Limit for repeated messages
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
