package lk.dailymoney.tracker

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Tx(
    val id: Long, val type: String, val amount: Double, val category: String,
    val date: String, val note: String
)

class MoneyDb(context: Context) : SQLiteOpenHelper(context, "daily_money.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL,
            amount REAL NOT NULL,
            category TEXT NOT NULL,
            date TEXT NOT NULL,
            note TEXT NOT NULL DEFAULT ''
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun all(): List<Tx> {
        val result = mutableListOf<Tx>()
        readableDatabase.rawQuery("SELECT id,type,amount,category,date,note FROM transactions ORDER BY date DESC,id DESC", null).use { c ->
            while (c.moveToNext()) result.add(Tx(c.getLong(0), c.getString(1), c.getDouble(2), c.getString(3), c.getString(4), c.getString(5)))
        }
        return result
    }
    fun save(id: Long?, type: String, amount: Double, category: String, date: String, note: String) {
        val values = ContentValues().apply {
            put("type", type); put("amount", amount); put("category", category); put("date", date); put("note", note)
        }
        if (id == null) writableDatabase.insert("transactions", null, values)
        else writableDatabase.update("transactions", values, "id=?", arrayOf(id.toString()))
    }
    fun delete(id: Long) { writableDatabase.delete("transactions", "id=?", arrayOf(id.toString())) }
    fun replaceAll(items: List<Tx>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("transactions", null, null)
            items.forEach { t ->
                val v = ContentValues().apply {
                    put("type", t.type); put("amount", t.amount); put("category", t.category); put("date", t.date); put("note", t.note)
                }
                writableDatabase.insert("transactions", null, v)
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
}

class MainActivity : Activity() {
    private lateinit var db: MoneyDb
    private lateinit var root: LinearLayout
    private lateinit var summary: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var monthSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var amountInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var dateButton: Button
    private lateinit var noteInput: EditText
    private lateinit var saveButton: Button
    private var editingId: Long? = null
    private var selectedDate = today()
    private var sinhala = true
    private var selectedMonth = currentMonth()
    private var selectedFilter = "ALL"

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = MoneyDb(this)
        buildUi()
        refresh()
    }

    private fun txt(si: String, en: String) = if (sinhala) si else en
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    private fun money(value: Double): String = "Rs. " + NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(value)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float = 15f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(0xFF20332E.toInt()); setPadding(0, dp(5), 0, dp(5))
    }
    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text; isAllCaps = false; setOnClickListener { action() }
    }
    private fun add(parent: LinearLayout, view: View, top: Int = 0) {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (top > 0) p.topMargin = dp(top)
        parent.addView(view, p)
    }
    private fun makeSpinner(items: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F7F6.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        val scroller = ScrollView(this)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(page)
        setContentView(scroller)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "My Daily Money"; textSize = 23f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF164B3F.toInt()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        header.addView(title)
        header.addView(button("සිං / EN") { sinhala = !sinhala; buildUi(); refresh() })
        add(page, header)
        add(page, label(txt("ආදායම් සහ වියදම් කළමනාකරණය", "Income & Expense Tracker"), 14f))
        summary = TextView(this).apply {
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF164B3F.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        add(page, summary, 10)

        add(page, label(txt("නව සටහනක් / Add transaction"), 19f), 14)
        typeSpinner = makeSpinner(listOf(txt("වියදම / Expense", "Expense / වියදම"), txt("ආදායම / Income", "Income / ආදායම")))
        add(page, typeSpinner)
        amountInput = EditText(this).apply {
            hint = txt("මුදල (රු.)", "Amount (LKR)"); inputType = 8194
        }
        add(page, amountInput, 6)

        categorySpinner = makeSpinner(expenseCategories())
        add(page, categorySpinner, 6)
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val old = categorySpinner.selectedItem?.toString()
                categorySpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    if (position == 1) incomeCategories() else expenseCategories())
                val opts = if (position == 1) incomeCategories() else expenseCategories()
                val match = opts.indexOf(old)
                if (match >= 0) categorySpinner.setSelection(match)
            }
        }

        dateButton = button("${txt("දිනය", "Date")}: $selectedDate") { pickDate() }
        add(page, dateButton, 6)
        noteInput = EditText(this).apply { hint = txt("විස්තර (අත්‍යවශ්‍ය නැත)", "Note (optional)") }
        add(page, noteInput, 6)
        saveButton = button(txt("සටහන සුරකින්න", "Save transaction")) { saveTransaction() }
        add(page, saveButton, 6)
        add(page, button(txt("අවලංගු කරන්න / Clear form", "Cancel edit / Clear form")) { clearForm() }, 4)

        add(page, label(txt("මාසික වාර්තාව / Monthly report", "Monthly report"), 19f), 18)
        val months = (0..24).map { offset ->
            val cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_MONTH, 1); cal.add(Calendar.MONTH, -offset)
            SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
        }
        monthSpinner = makeSpinner(months)
        monthSpinner.setSelection(months.indexOf(selectedMonth).coerceAtLeast(0))
        add(page, monthSpinner, 4)
        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMonth = months[position]; refresh()
            }
        }
        val backupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        backupRow.addView(button(txt("Backup ගන්න", "Export backup")) { createBackup.launch("daily-money-backup.json") },
            LinearLayout.LayoutParams(0, -2, 1f))
        backupRow.addView(button(txt("Restore කරන්න", "Restore backup")) {
            restoreBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        add(page, backupRow, 8)

        add(page, label(txt("සටහන් ඉතිහාසය / Transaction history", "Transaction history"), 19f), 18)
        val filters = makeSpinner(listOf("ALL", "INCOME", "EXPENSE"))
        filters.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf(txt("සියල්ල / All", "All / සියල්ල"), txt("ආදායම් / Income", "Income / ආදායම්"), txt("වියදම් / Expenses", "Expenses / වියදම්")))
        add(page, filters, 4)
        filters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFilter = listOf("ALL", "INCOME", "EXPENSE")[position]; refresh()
            }
        }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(page, listBox, 6)
        add(page, label(txt("දත්ත ඔබගේ දුරකථනයේම සුරැකේ. නිතර Backup ගන්න.", "Data is stored on this phone. Make backups regularly."), 12f), 12)
    }

    private fun expenseCategories() = listOf(
        "ආහාර / Food", "ප්‍රවාහන / Transport", "බිල්පත් / Bills", "කඩසාප්පු / Shopping",
        "කුලිය / Rent", "සෞඛ්‍ය / Health", "අධ්‍යාපනය / Education", "පවුල / Family",
        "විනෝදාස්වාදය / Entertainment", "ඉතිරිකිරීම් / Savings", "වෙනත් / Other"
    )
    private fun incomeCategories() = listOf(
        "වැටුප් / Salary", "ව්‍යාපාර / Business", "Freelance", "තෑගි / Gift", "Refund", "වෙනත් ආදායම් / Other income"
    )
    private fun pickDate() {
        val parts = selectedDate.split("-")
        val y = parts[0].toInt(); val m = parts[1].toInt() - 1; val d = parts[2].toInt()
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            dateButton.text = "${txt("දිනය", "Date")}: $selectedDate"
        }, y, m, d).show()
    }
    private fun saveTransaction() {
        val amount = amountInput.text.toString().trim().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            amountInput.error = txt("නිවැරදි මුදලක් ඇතුළත් කරන්න", "Enter a valid amount")
            return
        }
        val type = if (typeSpinner.selectedItemPosition == 1) "INCOME" else "EXPENSE"
        val category = categorySpinner.selectedItem?.toString() ?: "Other"
        db.save(editingId, type, amount, category, selectedDate, noteInput.text.toString().trim())
        Toast.makeText(this, txt("සුරකින ලදී", "Transaction saved"), Toast.LENGTH_SHORT).show()
        clearForm(); refresh()
    }
    private fun clearForm() {
        editingId = null; amountInput.setText(""); noteInput.setText(""); selectedDate = today()
        dateButton.text = "${txt("දිනය", "Date")}: $selectedDate"
        saveButton.text = txt("සටහන සුරකින්න", "Save transaction")
    }
    private fun refresh() {
        if (!::summary.isInitialized || !::listBox.isInitialized) return
        val all = db.all()
        val monthItems = all.filter { it.date.startsWith(selectedMonth) }
        val income = monthItems.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expense = monthItems.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        summary.text = "${txt("මාසික ශේෂය", "Monthly balance")}: ${money(income - expense)}\n" +
            "${txt("ආදායම", "Income")}: ${money(income)}    ${txt("වියදම", "Expenses")}: ${money(expense)}"
        listBox.removeAllViews()
        val visible = all.filter { it.date.startsWith(selectedMonth) && (selectedFilter == "ALL" || it.type == selectedFilter) }
        if (visible.isEmpty()) {
            add(listBox, label(txt("තවම සටහන් නැත", "No transactions yet"), 14f))
            return
        }
        visible.forEach { t ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            val headline = TextView(this).apply {
                text = "${if (t.type == "INCOME") "＋" else "−"} ${money(t.amount)}  •  ${t.category}"
                textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (t.type == "INCOME") 0xFF16834B.toInt() else 0xFFB33D32.toInt())
            }
            add(card, headline)
            add(card, label("${t.date}${if (t.note.isNotBlank()) " • ${t.note}" else ""}", 13f))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(button(txt("සංස්කරණය", "Edit")) { edit(t) }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button(txt("මකන්න", "Delete")) {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage(txt("මෙම සටහන මකන්නද?", "Delete this transaction?"))
                    .setNegativeButton(txt("නැහැ", "Cancel"), null)
                    .setPositiveButton(txt("ඔව්", "Delete")) { _, _ -> db.delete(t.id); refresh() }
                    .show()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actions)
            val params = LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8)
            listBox.addView(card, params)
        }
    }
    private fun edit(t: Tx) {
        editingId = t.id
        typeSpinner.setSelection(if (t.type == "INCOME") 1 else 0)
        amountInput.setText(t.amount.toString())
        val categories = if (t.type == "INCOME") incomeCategories() else expenseCategories()
        categorySpinner.setSelection(categories.indexOf(t.category).coerceAtLeast(0))
        selectedDate = t.date; dateButton.text = "${txt("දිනය", "Date")}: $selectedDate"
        noteInput.setText(t.note)
        saveButton.text = txt("වෙනස්කම් සුරකින්න", "Update transaction")
        amountInput.requestFocus()
    }
    private fun writeBackup(uri: Uri) {
        try {
            val array = JSONArray()
            db.all().forEach { t ->
                array.put(JSONObject().put("type", t.type).put("amount", t.amount)
                    .put("category", t.category).put("date", t.date).put("note", t.note))
            }
            val root = JSONObject().put("app", "DailyMoneyTracker").put("version", 1).put("transactions", array)
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(root.toString(2)) }
            Toast.makeText(this, txt("Backup සාර්ථකයි", "Backup exported"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "${txt("Backup අසාර්ථකයි", "Backup failed")}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private fun readBackup(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: throw IllegalStateException("Cannot read file")
            val json = JSONObject(raw)
            val array = json.getJSONArray("transactions")
            val items = mutableListOf<Tx>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val type = o.getString("type")
                val amount = o.getDouble("amount")
                val category = o.getString("category")
                val date = o.getString("date")
                val note = o.optString("note", "")
                require(type == "INCOME" || type == "EXPENSE")
                require(amount > 0 && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
                items.add(Tx(0, type, amount, category, date, note))
            }
            android.app.AlertDialog.Builder(this)
                .setMessage(txt("Restore කිරීමෙන් දැනට ඇති සටහන් වෙනුවට backup එකේ සටහන් යෙදේ. ඉදිරියට යන්නද?",
                    "Restore replaces the current transactions with the backup. Continue?"))
                .setNegativeButton(txt("අවලංගු කරන්න", "Cancel"), null)
                .setPositiveButton(txt("Restore කරන්න", "Restore")) { _, _ ->
                    db.replaceAll(items); refresh()
                    Toast.makeText(this, txt("Restore සාර්ථකයි", "Restore complete"), Toast.LENGTH_LONG).show()
                }.show()
        } catch (e: Exception) {
            Toast.makeText(this, "${txt("Backup ගොනුව කියවිය නොහැක", "Invalid backup file")}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
