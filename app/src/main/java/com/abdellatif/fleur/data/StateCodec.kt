package com.abdellatif.fleur.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Currency
import org.json.JSONArray
import org.json.JSONObject

object StateCodec {
    // JSONObject.getLong truncates fractions, which would silently change imported money.
    private fun JSONObject.strictLong(key: String): Long =
        get(key).toString().toLongOrNull()
            ?: throw IllegalArgumentException("Invalid integer for $key")

    private fun JSONObject.strictInt(key: String): Int =
        strictLong(key).let {
            require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Invalid integer for $key" }
            it.toInt()
        }

    fun account(a: Account) =
        JSONObject().apply {
            put("id", a.id)
            put("name", a.name)
            put("type", a.type.name)
            put("initial", a.initial)
            put("goal", a.goal)
            put("icon", a.icon)
            put("color", a.color)
            put("hidden", a.hidden)
            put("includeInBalance", a.includeInBalance)
            put("order", a.order)
        }

    fun account(j: JSONObject) =
        Account(
            j.getString("id"),
            j.getString("name"),
            AccountType.valueOf(j.getString("type")),
            j.strictLong("initial"),
            j.strictLong("goal"),
            j.getString("icon"),
            j.strictLong("color"),
            j.getBoolean("hidden"),
            j.getBoolean("includeInBalance"),
            j.strictInt("order"),
        )

    fun category(c: Category) =
        JSONObject().apply {
            put("id", c.id)
            put("name", c.name)
            put("type", c.type.name)
            put("icon", c.icon)
            put("color", c.color)
            put("order", c.order)
        }

    fun category(j: JSONObject) =
        Category(
            j.getString("id"),
            j.getString("name"),
            EntryType.valueOf(j.getString("type")),
            j.getString("icon"),
            j.strictLong("color"),
            j.strictInt("order"),
        )

    fun entry(e: Entry) =
        JSONObject().apply {
            put("id", e.id)
            put("type", e.type.name)
            put("amount", e.amount)
            put("categoryId", e.categoryId)
            put("accountId", e.accountId)
            put("toAccountId", e.toAccountId)
            put("date", e.date.toString())
            put("paid", e.paid)
            put("notes", e.notes)
            put("createdAt", e.createdAt)
            put(
                "repeat",
                JSONObject().apply {
                    put("frequency", e.repeat.frequency.name)
                    put("interval", e.repeat.interval)
                    put("weekDays", JSONArray(e.repeat.weekDays.map { it.value }))
                    put("lastDay", e.repeat.lastDay)
                    put("end", e.repeat.end?.toString() ?: JSONObject.NULL)
                    put("anchorDay", e.repeat.anchorDay ?: JSONObject.NULL)
                },
            )
        }

    fun entry(j: JSONObject): Entry {
        val r = j.getJSONObject("repeat")
        val days = r.getJSONArray("weekDays")
        return Entry(
            j.getString("id"),
            EntryType.valueOf(j.getString("type")),
            j.strictLong("amount"),
            j.getString("categoryId"),
            j.getString("accountId"),
            j.getString("toAccountId"),
            LocalDate.parse(j.getString("date")),
            j.getBoolean("paid"),
            j.getString("notes"),
            RepeatRule(
                Frequency.valueOf(r.getString("frequency")),
                r.strictInt("interval"),
                (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
                r.getBoolean("lastDay"),
                if (r.isNull("end")) null else LocalDate.parse(r.getString("end")),
                r.optInt("anchorDay", 0).takeIf { it != 0 },
            ),
            j.strictLong("createdAt"),
        )
    }

    fun preferences(p: Preferences) =
        JSONObject().apply {
            put("name", p.name)
            put("currency", p.currency)
            put("darkMode", p.darkMode)
            put("accent", p.accent)
            put("font", p.font)
            put("fontSize", p.fontSize)
            put("defaultAccountId", p.defaultAccountId)
            put("monthStart", p.monthStart)
            put("weekStart", p.weekStart)
            put("period", p.period.name)
            put("carryOver", p.carryOver)
            put("decimals", p.decimals)
            put("showPaid", p.showPaid)
            put("haptics", p.haptics)
            put("showBudget", p.showBudget)
            put("showDebt", p.showDebt)
            put("overallBudget", p.overallBudget)
            put("budgetRemaining", p.budgetRemaining)
            put("reminders", p.reminders)
            put("reminderHour", p.reminderHour)
            put("reminderMinute", p.reminderMinute)
            put("appLock", p.appLock)
            put("defaultPaid", p.defaultPaid)
        }

    fun preferences(j: JSONObject) =
        Preferences(
            name = j.getString("name"),
            currency = j.getString("currency"),
            darkMode = j.getString("darkMode"),
            accent = j.strictLong("accent"),
            font = j.getString("font"),
            fontSize = j.strictInt("fontSize"),
            defaultAccountId = j.getString("defaultAccountId"),
            monthStart = j.strictInt("monthStart"),
            weekStart = j.strictInt("weekStart"),
            period = Period.valueOf(j.getString("period")),
            carryOver = j.getBoolean("carryOver"),
            decimals = j.getBoolean("decimals"),
            showPaid = j.getBoolean("showPaid"),
            haptics = j.getBoolean("haptics"),
            showBudget = j.getBoolean("showBudget"),
            showDebt = j.getBoolean("showDebt"),
            overallBudget = j.strictLong("overallBudget"),
            budgetRemaining = j.getBoolean("budgetRemaining"),
            reminders = j.getBoolean("reminders"),
            reminderHour = j.strictInt("reminderHour"),
            reminderMinute = j.strictInt("reminderMinute"),
            appLock = j.getBoolean("appLock"),
            defaultPaid = j.optBoolean("defaultPaid", true),
        )

    fun budget(b: Budget) = JSONObject().put("categoryId", b.categoryId).put("amount", b.amount)

    fun budget(j: JSONObject) = Budget(j.getString("categoryId"), j.strictLong("amount"))

    fun encode(s: BudgetState): String =
        JSONObject()
            .apply {
                put("format", "fleur-personal")
                put("version", 1)
                put("accounts", JSONArray(s.accounts.map(::account)))
                put("categories", JSONArray(s.categories.map(::category)))
                put("entries", JSONArray(s.entries.map(::entry)))
                put("budgets", JSONArray(s.budgets.map(::budget)))
                put("preferences", preferences(s.preferences))
                put(
                    "overrides",
                    JSONObject().apply {
                        s.overrides.forEach { (k, e) -> put(k, e?.let(::entry) ?: JSONObject.NULL) }
                    },
                )
            }
            .toString(2)

    fun decode(text: String): BudgetState {
        require(text.length <= 25_000_000) { "Backup is too large" }
        val j = JSONObject(text)
        require(j.getString("format") == "fleur-personal" && j.strictInt("version") == 1) {
            "Unsupported backup format"
        }
        fun <T> read(name: String, convert: (JSONObject) -> T): List<T> {
            val a = j.getJSONArray(name)
            require(a.length() <= 100_000) { "Backup contains too many records" }
            return (0 until a.length()).map { convert(a.getJSONObject(it)) }
        }
        val o = j.getJSONObject("overrides")
        require(o.length() <= 100_000) { "Backup contains too many overrides" }
        val state =
            BudgetState(
                read("accounts", ::account),
                read("categories", ::category),
                read("entries", ::entry),
                o.keys().asSequence().associateWith {
                    if (o.isNull(it)) null else entry(o.getJSONObject(it))
                },
                read("budgets", ::budget),
                preferences(j.getJSONObject("preferences")),
            )
        validate(state)
        return state
    }

    fun validate(s: BudgetState) {
        require(s.accounts.isNotEmpty()) { "At least one account is required" }
        val accounts = s.accounts.map { it.id }.toSet()
        val categories = s.categories.map { it.id }.toSet()
        val categoryTypes = s.categories.associate { it.id to it.type }
        val entries = s.entries.map { it.id }.toSet()
        require(
            accounts.size == s.accounts.size &&
                categories.size == s.categories.size &&
                entries.size == s.entries.size,
        ) {
            "Duplicate record IDs"
        }
        require(
            (accounts + categories + entries).all {
                it.isNotBlank() && it.length <= 100 && '@' !in it
            },
        ) {
            "Invalid record ID"
        }
        require(s.accounts.all { it.name.isNotBlank() && it.name.length <= 100 && it.goal >= 0 }) {
            "Invalid account"
        }
        require(
            s.categories.all {
                it.name.isNotBlank() && it.name.length <= 100 && it.type != EntryType.TRANSFER
            },
        ) {
            "Invalid category"
        }
        fun validateEntry(e: Entry) {
            require(e.amount > 0 && e.amount <= 99_999_999_999_999L) {
                "Invalid transaction amount"
            }
            require(
                e.accountId in accounts &&
                    (e.type != EntryType.TRANSFER ||
                        e.toAccountId in accounts && e.toAccountId != e.accountId),
            ) {
                "Invalid transaction account"
            }
            require(e.type == EntryType.TRANSFER || categoryTypes[e.categoryId] == e.type) {
                "A category used by transactions must keep its income or expense type"
            }
            require(e.notes.length <= 10_000 && e.date.year in 1900..2200) {
                "Invalid transaction details"
            }
            require(
                e.repeat.interval in 1..365 &&
                    (e.repeat.end == null || e.repeat.end >= e.date) &&
                    (e.repeat.anchorDay == null || e.repeat.anchorDay in 1..31),
            ) {
                "Invalid repeat schedule"
            }
        }
        s.entries.forEach(::validateEntry)
        s.overrides.forEach { (key, e) ->
            require(key.substringBeforeLast('@') in entries) {
                "Override has no parent transaction"
            }
            LocalDate.ofEpochDay(key.substringAfterLast('@').toLong())
            e?.let(::validateEntry)
        }
        require(s.budgets.all { it.categoryId in categories && it.amount >= 0 }) {
            "Invalid category budget"
        }
        require(s.budgets.map { it.categoryId }.distinct().size == s.budgets.size) {
            "Duplicate category budget"
        }
        with(s.preferences) {
            Currency.getInstance(currency)
            require(
                defaultAccountId in accounts &&
                    monthStart in 1..31 &&
                    weekStart in 1..7 &&
                    fontSize in 32..80 &&
                    overallBudget >= 0 &&
                    reminderHour in 0..23 &&
                    reminderMinute in 0..59,
            ) {
                "Invalid preferences"
            }
        }
        var exposure = java.math.BigInteger.ZERO
        val last = LocalDate.of(2200, 12, 31)
        fun addExposure(amount: Long, count: Long = 1) {
            exposure +=
                java.math.BigInteger.valueOf(amount).abs() * java.math.BigInteger.valueOf(count)
            require(exposure <= java.math.BigInteger.valueOf(Long.MAX_VALUE / 2)) {
                "The combined budget amounts exceed the supported range"
            }
        }
        s.accounts.forEach {
            addExposure(it.initial)
            addExposure(it.goal)
        }
        s.budgets.forEach { addExposure(it.amount) }
        addExposure(s.preferences.overallBudget)
        s.entries.forEach { addExposure(it.amount, it.repeat.count(it.date, last)) }
        s.overrides.values.filterNotNull().forEach { addExposure(it.amount) }
    }
}
