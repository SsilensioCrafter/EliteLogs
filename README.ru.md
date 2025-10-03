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
- Опциональное зеркалирование в MySQL: для каждого типа логов автоматически создаётся отдельная таблица (например, `elitelogs_chat` или `elitelogs_errors`) с JSON-массивами тегов, структурированным контекстом, настраиваемым префиксом и автоматическим обновлением схемы/индексов — файловое хранилище при этом продолжает работать.
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

#### MySQL-хранилище (`storage.database`)
- `enabled` и доступы: при включении каждый активный тип логов зеркалируется в БД, не мешая файловым логам.
- `table-prefix`: задаёт префикс для названий таблиц (`elitelogs_chat`, `elitelogs_errors` и т. д.).
- `flush-interval-ticks`: определяет, как часто воркер сбрасывает очередь в БД (1 тик = 50 мс).
- `auto-upgrade`: автоматически создаёт/обновляет таблицы, индексы и реестр категорий при подключении.
- Структура таблицы: `occurred_at`, `event_type`, `message`, `player_uuid`, `player_name`, `tags` (JSON-массив) и `context` (JSON-объект с категорией, игроком и разобранными тегами).
- Служебные таблицы (`<prefix>schema_info`, `<prefix>registry`) поддерживаются автоматически и помогают панелям понимать текущую схему.

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


---

## 🛠️ Сборка

1. Убедитесь, что установлены Maven и JDK 8+.
2. Выполните `mvn -f EliteLogs/pom.xml -DskipTests package` из корня репозитория (или перейдите `cd EliteLogs`, затем выполните `mvn -DskipTests package`).
   - Минимальные API ProtocolLib 5.1.0 лежат в `EliteLogs/src/stubs/java`: build-helper подключает их на этапе компиляции, а jar-плагин исключает из итогового артефакта. Для записи экранов отключения на боевом сервере по-прежнему требуется настоящая ProtocolLib.
