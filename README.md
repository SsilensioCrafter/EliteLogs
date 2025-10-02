# EliteLogs - for admins, by admin.

<img width="1024" height="1024" alt="image" src="https://github.com/user-attachments/assets/4e99e7ca-c281-4ad8-a2bc-80145ab9deb2" />


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

## 📖 Usage (TL;DR)

Once the jar is installed you only need two steps:

1. Run `/elogs help` in-game or from console to check command permissions.
2. Tail the `/plugins/EliteLogs/logs/` folder; a subdirectory is created per module automatically.

---

## 🛠️ Command Reference

Each subcommand is safe to execute live — no restarts required.

### `/elogs help`
Prints the localized help page with every subcommand, including any modules that have been disabled in `config.yml` so staff know what to expect.

### `/elogs reload`
Reloads `config.yml`, reinitialises module flags inside the live `LogRouter`, and revalidates the HTTP API token. Use this after editing the config from disk.

### `/elogs inspector`
Captures a full diagnostic snapshot: plugin list, mod list (for modded cores), JVM flags, config hashes, and dumps the report into `logs/inspector/`.

### `/elogs metrics`
Shows live TPS, memory usage, player count, watchdog thresholds, and the status of collectors. Helpful for spotting lag spikes without leaving the game.

### `/elogs export`
Packages the latest logs (or a requested range) into a zip file under `/exports/` for easy sharing. Ideal for sending evidence to other staff.

### `/elogs rotate [force]`
Forces log rotation immediately; with `force` it ignores the minimum interval, otherwise it respects the configured schedule. Archives land in `/logs/archive/` when `logs.archive` is true.

### `/elogs apikey` (alias `/elogs token`)
Admin-only command for the HTTP API. Supports `show`, `status`, and `regenerate` arguments so you can retrieve or rotate the secret without opening the YAML file.

### `/elogs logs [toggle <module>]`
Lists every configured log module with a coloured enabled/disabled indicator sourced from the live router snapshot. Use `toggle <module>` to flip a flag — the router is reloaded instantly and the setting persists to `config.yml`.

### `/elogs version`
Reports the plugin version, git commit (if available), and server compatibility information.

### `/elogs session`
Displays the current tracked session summaries, including duration and active players. Use it during events to capture highlights quickly.

---

## ✨ Features at a Glance
- Comprehensive logging: chat, commands, economy, combat, inventory, stats, console, sessions, warnings, errors, disconnects, and more.
- Dedicated `/logs/disconnects` folder captures login denials, kicks, resource-pack responses, and even server disconnect screens (via ProtocolLib when available) with normalized key/value fields (`result`, `ip`, `reason`, `source`, etc.).
- Optional ProtocolLib capture can now be toggled through `logs.disconnects.capture-screen` for hosts that prefer to disable JSON snooping.
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

---

## 🧭 Module & Function Guide

### Logging modules (`logs.types`)
- **warns** — records anything elevated via `plugin.getLogger().warning()` or similar API calls.
- **errors** — captures stack traces and fatal errors so you can diff recurring crashes.
- **chat** — stores public chat, including UUID/name metadata for replaying context.
- **commands** — logs every command execution with executor and arguments.
- **players** — join/quit flow, teleport summaries, and per-player mirrors when `split-by-player` is enabled.
- **disconnects** — the structured pipeline for pre-login denials, login kicks, quits, resource-pack statuses, and (optionally) disconnect screens.
- **combat** — PvP/PvE hits, kills, and death context (weapon, attacker, world).
- **inventory** — chest access, ender-chest interactions, container movements, shulker loot.
- **economy** — Vault-backed balance changes, trades, shop purchases.
- **stats** — advancements, statistic milestones, playtime counters.
- **console** — a rotating copy of the live console for forensics.
- **rcon** — everything executed through remote console connections.
- **suppressed** — overflow bucket that stores messages muted elsewhere (handy for auditing filters).

### Disconnect pipeline
- Phases recorded: `prelogin-deny`, `login-deny`, `kick`, `quit`, `resource-pack`, `disconnect-screen`.
- Key/value layout: `[phase] uuid=<...> name=<...> result=<...> reason=<...>` etc. No YAML/JSON parsing required.
- ProtocolLib hook writes both human-readable text and the raw JSON payload for disconnect screens when enabled.
- Player mirrors are synced to `/logs/disconnects/players/<uuid>/` alongside global streams.

### Session reporting
- Global session summaries stored in `/logs/sessions/global/`, refreshed every `sessions.autosave-minutes`.
- Per-player session history (login duration, deaths, economy delta) written to `/logs/sessions/players/<uuid>/`.
- Triggered automatically on join/quit and when watchdog fires to capture the current state.

### HTTP API
- `api.auth-token` auto-generates on first launch; `/elogs apikey` exposes management options.
- Endpoints provide status, metrics, watchdog insights, and streamed log buffers.
- Rate limiting is handled by the in-memory log buffer size (`api.log-history`).

### Discord alerts
- `discord.send` block lets you enable granular topics: errors, warnings, sessions, watchdog, inspector.
- Rate-limiter prevents spam by enforcing `discord.rate-limit-seconds` between posts.

### Watchdog automation
- Fires when TPS drops below `watchdog.tps-threshold` or errors exceed `watchdog.error-threshold`.
- Automatically runs the inspector, generates crash reports, and notifies Discord (if configured).

---

### 🔌 Disconnect log format

- Every entry is emitted as `[phase] key=value …` so scripts and SIEM pipelines can parse them easily.
- Phases: `prelogin-deny`, `login-deny`, `kick`, `quit`, `resource-pack`, and `disconnect-screen` (ProtocolLib).
- Common keys include `result`, `ip`, `source`, `cause`, `status`, `reason`, and `raw-json` (disconnect screen payloads).
- Per-player mirrors are written to `logs/disconnects/players/<uuid>/global-YYYY-MM-DD.log` when `split-by-player` is enabled.

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
- Leave `auth-token` empty and EliteLogs will auto-generate a strong token on startup; use `/elogs apikey show` (or `regenerate`) to reveal or rotate it safely.
- Provide the token via the `X-API-Key` header (preferred) or the `token` query parameter.
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

### Version metadata
- `version` is stamped during the build so the YAML always matches the jar you are running. Leave it untouched; `/elogs reload` will rewrite the value after you deploy an update.

```yaml
# ============================
#  EliteLogs Configuration
#  vibe-coded © 2025
# ============================

# Version metadata (auto-managed during builds)
version: "1.2.1"        # Overwritten on reload/update

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
  keep-days: 30               # Number of days to retain daily archives (-1 = forever)
  archive: true               # Compress rotated logs into /logs/archive for long-term storage
  split-by-player: true       # Write per-player logs in module folders
  legacy:
    flat-player-files: false  # Old style: player-Name-YYYY-MM-DD.log (not recommended)
  types:
    warns: true               # Captures anything logged through plugin.warn(...)
    errors: true              # Fatal stacktraces and exceptions dumped by plugins
    chat: true                # Global chat history with sender UUID/username metadata
    commands: true            # Every command dispatch (player + console) with context
    players: true             # Includes join/quit events and per-player folders
    disconnects: true         # Tracks login denials, kicks, resource pack status, disconnect screens
    combat: true              # PvP/PvE damage, kills, and death summaries
    inventory: true           # Item pickups/drops, container access, trade logs
    economy: true             # Vault economy transactions and balance updates
    stats: true               # Stat/advancement milestones, playtime counters
    console: true             # Mirrors the live console output into rotating files
    rcon: true                # Remote console sessions and issued commands
    suppressed: true          # Catch-all bucket for anything muted elsewhere
  disconnects:
    capture-screen: true      # Requires ProtocolLib to read the disconnect screen text

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
  auth-token: ""             # Leave blank to auto-generate, manage via /elogs apikey
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


---

## 🇷🇺 Полная версия README

### 📦 Установка
1. Скачайте [актуальный релиз](https://github.com/SsilensioCrafter/EliteLogs/releases).
2. Поместите `EliteLogs.jar` в папку `plugins/` вашего сервера.
3. Перезапустите ядро.
4. Получите заслуженный респект от команды админов.

### 📖 Быстрый старт

После установки вам хватит двух шагов:

1. Выполните `/elogs help` в игре или консоли, чтобы проверить доступные команды и права.
2. Загляните в `/plugins/EliteLogs/logs/` — для каждого модуля создаётся собственная папка автоматически.

### 🛠️ Справочник команд

Все подкоманды безопасно выполнять «на горячую» — перезапуск не требуется.

#### `/elogs help`
Печатает локализованную справку со всеми подкомандами, включая выключенные через `config.yml` модули.

#### `/elogs reload`
Перечитывает `config.yml`, обновляет флаги модулей в живом `LogRouter` и перепроверяет ключ HTTP API. Запускайте после правки конфигурации.

#### `/elogs inspector`
Собирает диагностический снимок: список плагинов, модов, параметры JVM, хэши конфигов и сохраняет отчёт в `logs/inspector/`.

#### `/elogs metrics`
Показывает текущие TPS, использование памяти, количество игроков, пороги watchdog и статус сборщиков. Удобно для поиска лагов без внешних инструментов.

#### `/elogs export`
Упаковывает последние логи (или указанный диапазон) в zip-файл в каталоге `/exports/`. Полезно для передачи доказательств коллегам.

#### `/elogs rotate [force]`
Мгновенно запускает ротацию логов. С аргументом `force` игнорирует минимальный интервал, иначе уважает расписание. Архивы попадают в `/logs/archive/`, если `logs.archive = true`.

#### `/elogs apikey` (алиас `/elogs token`)
Админская команда для HTTP API: поддерживает `show`, `status` и `regenerate`, чтобы увидеть или перевыпустить секрет без ручного редактирования YAML.

#### `/elogs logs [toggle <module>]`
Выводит список всех модулей с цветовым статусом из активного `LogRouter`. Команда `toggle <module>` мгновенно меняет флаг, сохраняет его в `config.yml` и перезагружает роутер.

#### `/elogs version`
Сообщает версию плагина, git-коммит (если доступен) и информацию о совместимости.

#### `/elogs session`
Показывает текущие сводки сессий: длительность, активных игроков и ключевые события — удобно во время ивентов.

### ✨ Краткий обзор возможностей
- Полное покрытие логами: чат, команды, экономика, бой, инвентарь, статистика, консоль, сессии, варны, ошибки, отключения и многое другое.
- Папка `/logs/disconnects` фиксирует отклонения на логине, кики, выходы, ответы на ресурспак и тексты экранов отключения (при наличии ProtocolLib) в формате ключ=значение.
- Перехват через ProtocolLib можно отключить или включить флагом `logs.disconnects.capture-screen`.
- Персональные логи в `logs/<module>/players/<uuid>` и истории сессий в `logs/players/<playerName>/sessions`.
- Глобальные дневные файлы `logs/<module>/global-YYYY-MM-DD.log` для быстрых обзоров.
- Гибкие переключатели модулей в `config.yml`.
- Отчёты сессий для сервера и игроков, хранятся отдельно.
- Интеграция с Discord: ошибки, варны, сессии и уведомления watchdog отправляются в канал.
- Inspector, metrics, suppressor и watchdog идут из коробки.
- Watchdog автоматически запускает inspector, собирает crash-репорты и передаёт статус в HTTP API.
- Лёгкий HTTP API отдаёт метрики и последние логи для внешних панелей.
- Режим совместимости с плоскими файлами игроков для любителей олдскула.
- Локализации EN/RU/DE/FR/ES с англоязычным запасным вариантом.
- Написано на смеси кофеина и Java, но достаточно стабильно для продакшена.

### 🧭 Путеводитель по функциям

#### Модули логирования (`logs.types`)
- **warns** — предупреждения из `plugin.getLogger().warning()` и аналогичных вызовов.
- **errors** — стектрейсы и фатальные ошибки для расследования крашей.
- **chat** — публичный чат с UUID/ником отправителя.
- **commands** — любая команда, кто и что ввёл.
- **players** — входы/выходы, телепорты, зеркала по игрокам при `split-by-player`.
- **disconnects** — весь пайплайн отключений: pre-login, login, kicks, quits, ресурспак, экраны disconnect.
- **combat** — удары, убийства, информация об оружии и мире.
- **inventory** — сундуки, эндер-сундуки, движения предметов, обмены.
- **economy** — транзакции Vault, магазинные покупки, изменение балансов.
- **stats** — достижения, статистика, счётчики времени.
- **console** — копия живой консоли для аудита.
- **rcon** — команды, отправленные через RCON-сессии.
- **suppressed** — сообщения, которые были отфильтрованы другими модулями.

#### Пайплайн отключений
- Фиксирует стадии `prelogin-deny`, `login-deny`, `kick`, `quit`, `resource-pack`, `disconnect-screen`.
- Формат `[фаза] ключ=значение` избавляет от парсинга YAML/JSON.
- При наличии ProtocolLib пишется и читабельный текст, и сырой JSON экрана отключения.
- Зеркала игроков сохраняются в `/logs/disconnects/players/<uuid>/` наряду с глобальными файлами.

#### Отчёты сессий
- Глобальные сводки в `/logs/sessions/global/`, пересохраняются каждые `sessions.autosave-minutes` минут.
- История конкретного игрока (`/logs/sessions/players/<uuid>/`) с длительностью, смертями и изменением экономики.
- Автоматически срабатывает на вход/выход и при тревогах watchdog.

#### HTTP API
- `api.auth-token` генерируется автоматически; `/elogs apikey` помогает показать или обновить ключ.
- Эндпоинты возвращают статус, метрики, информацию watchdog и буферы логов.
- Ограничение скорости зависит от размера буфера (`api.log-history`).

#### Уведомления в Discord
- Блок `discord.send` включает отдельные темы: ошибки, варны, сессии, watchdog, inspector.
- Лимитер `discord.rate-limit-seconds` защищает канал от спама.

#### Watchdog
- Срабатывает при падении TPS ниже `watchdog.tps-threshold` или при превышении `watchdog.error-threshold`.
- Запускает inspector, создаёт crash-репорт и оповещает Discord (если включено).

### 🔌 Формат disconnect-логов
- Каждая запись — `[фаза] ключ=значение …`, удобно для SIEM и скриптов.
- Поддерживаемые фазы: `prelogin-deny`, `login-deny`, `kick`, `quit`, `resource-pack`, `disconnect-screen` (ProtocolLib).
- Основные ключи: `result`, `ip`, `source`, `cause`, `status`, `reason`, `raw-json`.
- При `split-by-player` зеркала пишутся в `logs/disconnects/players/<uuid>/global-YYYY-MM-DD.log`.

### 🧩 Совместимость
| Диапазон | Особенности |
| --- | --- |
| 1.8.x – 1.12.x | Слушатели используют рефлексию для `getItemInMainHand` и старых ивентов `PlayerPickupItemEvent`, поэтому функциональность инвентаря сохраняется. |
| 1.13.x – 1.20.6 | Применяются новые события (`EntityPickupItemEvent`) и коллекции `Bukkit.getOnlinePlayers()` без поломок на старых ядрах. |
| 1.21.x | Соберите проект с зависимостью `spigot-api:1.21.x` — слой совместимости продолжит работать, нужны только тесты. |

### 🌐 Локализация
- В комплекте сообщения EN/RU/DE/FR/ES.
- Выберите `language` в `config.yml`; при отсутствии строки используется английский.
- Собственные переводы можно положить в `plugins/EliteLogs/lang/<code>.yml` — при перезагрузке они перекрывают встроенные.

### 🗺️ Дорожная карта
- [x] Логи предупреждений и отчётов
- [ ] Поддержка базы данных
- [ ] Красивый веб-панель
- [ ] Возможно, AI-резюме логов

### 🔌 HTTP API
EliteLogs может запускать опциональный HTTP-сервер, чтобы ваша панель (например, SsilensioWeb) получала данные напрямую. Включите `api.enabled`, настройте адрес/порт и пользуйтесь.

#### Эндпоинты
- `GET /api/v1/status` — версия плагина, активные модули, ключевые флаги конфигурации.
- `GET /api/v1/metrics` — TPS/CPU/память, счётчики сессий и пороги watchdog.
- `GET /api/v1/watchdog` — пороги, тайминги срабатываний, счётчик ошибок и последняя причина.
- `GET /api/v1/logs` — список категорий, хранимых в буфере.
- `GET /api/v1/logs/<category>?limit=100` — последние строки по категории (лимит равен `api.log-history`).

#### Аутентификация
- Оставьте `auth-token` пустым — плагин сгенерирует стойкое значение и подскажет, как получить его через `/elogs apikey`.
- Передавайте токен в заголовке `X-API-Key` (предпочтительно) или параметром `token`.
- `log-history` задаёт объём буфера для мгновенных ответов API.
- Связывайте сервер на `127.0.0.1`, если используете обратный прокси; `0.0.0.0` используйте только при осознанном решении открыть доступ наружу.

### 🤝 Вклад
Хотите вкладываться?

1. Форкните репозиторий.
2. Создайте ветку (`git checkout -b feature/your-idea`).
3. Закоммитьте изменения (`git commit -m '✨ add cool stuff'`).
4. Отправьте ветку (`git push origin feature/your-idea`).
5. Откройте Pull Request.
6. Бонус — мем в описании PR.

### 📜 Лицензия
Проект распространяется по [MIT License](LICENSE).

Коротко:

- ✅ Можно использовать.
- ✅ Можно модифицировать.
- ✅ Можно распространять.
- ❌ Никаких претензий, если сервер внезапно загорится 🔥.

### ⚙️ Конфигурация
Да, здесь тоже есть пример конфигурации (комментарии остаются на английском, чтобы не расходиться с файлом `config.yml`):

### Метаданные версии
- Поле `version` автоматически проставляется при сборке, чтобы YAML совпадал с установленным jar. Не трогайте его вручную — после обновления достаточно выполнить `/elogs reload`, и значение перепишется само.

```yaml
# ============================
#  EliteLogs Configuration
#  vibe-coded © 2025
# ============================

# Version metadata (auto-managed during builds)
version: "1.2.1"        # Overwritten on reload/update

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
  webhook-url: ""
  rate-limit-seconds: 10
  send:
    errors: true
    warns: true
    sessions: true
    watchdog: true
    inspector: true

# Logging system
logs:
  rotate: true
  keep-days: 30               # Number of days to retain daily archives (-1 = forever)
  archive: true               # Compress rotated logs into /logs/archive for long-term storage
  split-by-player: true       # Write per-player logs in module folders
  legacy:
    flat-player-files: false
  types:
    warns: true               # Captures anything logged through plugin.warn(...)
    errors: true              # Fatal stacktraces and exceptions dumped by plugins
    chat: true                # Global chat history with sender UUID/username metadata
    commands: true            # Every command dispatch (player + console) with context
    players: true             # Includes join/quit events and per-player folders
    disconnects: true         # Tracks login denials, kicks, resource pack status, disconnect screens
    combat: true              # PvP/PvE damage, kills, and death summaries
    inventory: true           # Item pickups/drops, container access, trade logs
    economy: true             # Vault economy transactions and balance updates
    stats: true               # Stat/advancement milestones, playtime counters
    console: true             # Mirrors the live console output into rotating files
    rcon: true                # Remote console sessions and issued commands
    suppressed: true          # Catch-all bucket for anything muted elsewhere
  disconnects:
    capture-screen: true      # Requires ProtocolLib to read the disconnect screen text

# Player sessions summary
sessions:
  enabled: true
  autosave-minutes: 10
  save-global: true
  save-players: true

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
  mode: blacklist
  spam-limit: 1000
  filters: []

# Watchdog — emergency watchdog
watchdog:
  enabled: true
  tps-threshold: 5.0
  error-threshold: 50
  actions:
    run-inspector: true
    create-crash-report: true
    discord-alert: true
```

