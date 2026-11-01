package com.abdellatif.fleur.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class BudgetDatabase(context: Context, name: String = "fleur-personal.db") :
    SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        for (table in
            listOf("accounts", "categories", "entries", "overrides", "budgets", "preferences")) {
            db.execSQL("CREATE TABLE $table (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported database upgrade from $oldVersion to $newVersion")
    }

    fun load(): BudgetState {
        fun <T> rows(table: String, convert: (String, String) -> T): List<T> =
            readableDatabase
                .query(table, arrayOf("id", "payload"), null, null, null, null, "rowid")
                .use { c ->
                    buildList {
                        while (c.moveToNext()) add(convert(c.getString(0), c.getString(1)))
                    }
                }
        val preferences =
            rows("preferences") { _, text -> StateCodec.preferences(JSONObject(text)) }
                .firstOrNull()
        if (preferences == null) {
            val seed = initialState()
            save(BudgetState(), seed)
            return seed
        }
        return BudgetState(
            rows("accounts") { _, t -> StateCodec.account(JSONObject(t)) }.sortedBy { it.order },
            rows("categories") { _, t -> StateCodec.category(JSONObject(t)) }.sortedBy { it.order },
            rows("entries") { _, t -> StateCodec.entry(JSONObject(t)) },
            rows("overrides") { id, t ->
                    id to if (t == "null") null else StateCodec.entry(JSONObject(t))
                }
                .toMap(),
            rows("budgets") { _, t -> StateCodec.budget(JSONObject(t)) },
            preferences,
        )
    }

    fun save(previous: BudgetState, next: BudgetState) {
        StateCodec.validate(next)
        val db = writableDatabase
        db.beginTransaction()
        try {
            fun <T> sync(
                table: String,
                before: Map<String, T>,
                after: Map<String, T>,
                encode: (T) -> String,
            ) {
                for (id in before.keys - after.keys) db.delete(table, "id = ?", arrayOf(id))
                for ((id, item) in after) if (!before.containsKey(id) || before[id] != item) {
                    val values =
                        ContentValues().apply {
                            put("id", id)
                            put("payload", encode(item))
                        }
                    db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        .also { check(it != -1L) { "Unable to save $table" } }
                }
            }
            sync(
                "accounts",
                previous.accounts.associateBy { it.id },
                next.accounts.associateBy { it.id },
            ) {
                StateCodec.account(it).toString()
            }
            sync(
                "categories",
                previous.categories.associateBy { it.id },
                next.categories.associateBy { it.id },
            ) {
                StateCodec.category(it).toString()
            }
            sync(
                "entries",
                previous.entries.associateBy { it.id },
                next.entries.associateBy { it.id },
            ) {
                StateCodec.entry(it).toString()
            }
            sync("overrides", previous.overrides, next.overrides) {
                it?.let(StateCodec::entry)?.toString() ?: "null"
            }
            sync(
                "budgets",
                previous.budgets.associateBy { it.categoryId },
                next.budgets.associateBy { it.categoryId },
            ) {
                StateCodec.budget(it).toString()
            }
            sync(
                "preferences",
                if (previous.accounts.isEmpty()) emptyMap()
                else mapOf("settings" to previous.preferences),
                mapOf("settings" to next.preferences),
            ) {
                StateCodec.preferences(it).toString()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
