package com.koyambedu.ledgerapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class LedgerItem(
    val id: Int = 0,
    val date: String,
    val name: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val paymentMode: String // "Cash", "Bank", "Credit"
)

data class CreditReceivedItem(
    val id: Int = 0,
    val date: String,
    val customerName: String,
    val paymentMode: String, // "Cash", "Bank", "Adjustment"
    val amount: Double
)

data class CustomerStatementRow(
    val date: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val receivedAmount: Double,
    val closingBalance: Double
)

data class CustomerStatementReport(
    val customerName: String,
    val openingBalance: Double,
    val rows: List<CustomerStatementRow>,
    val finalClosingBalance: Double
)

data class CustomerRangeSummaryRow(
    val customerName: String,
    val openingBalance: Double,
    val sales: Double,
    val income: Double,
    val closingBalance: Double
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "DailyLedger.db"
        private const val DATABASE_VERSION = 1

        // Page 1 Table: additems
        const val TABLE_ADD_ITEMS = "additems"
        const val COL_ID = "id"
        const val COL_DATE = "date"
        const val COL_NAME = "name"
        const val COL_QTY = "quantity"
        const val COL_RATE = "rate"
        const val COL_AMOUNT = "amount"
        const val COL_PAYMENT_MODE = "payment_mode"

        // Page 2 Table: credit_received
        const val TABLE_CREDIT_RECEIVED = "credit_received"
        const val COL_CR_ID = "id"
        const val COL_CR_DATE = "date"
        const val COL_CR_CUSTOMER = "customer_name"
        const val COL_CR_MODE = "payment_mode"
        const val COL_CR_AMOUNT = "amount"

        // Page 3 Base Opening Balance Table
        const val TABLE_OPENING = "customer_opening"
        const val COL_OP_CUSTOMER = "customer_name"
        const val COL_OP_AMOUNT = "opening_amount"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_ADD_ITEMS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DATE TEXT NOT NULL,
                $COL_NAME TEXT NOT NULL,
                $COL_QTY REAL NOT NULL,
                $COL_RATE REAL NOT NULL,
                $COL_AMOUNT REAL NOT NULL,
                $COL_PAYMENT_MODE TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CREDIT_RECEIVED (
                $COL_CR_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CR_DATE TEXT NOT NULL,
                $COL_CR_CUSTOMER TEXT NOT NULL,
                $COL_CR_MODE TEXT NOT NULL,
                $COL_CR_AMOUNT REAL NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_OPENING (
                $COL_OP_CUSTOMER TEXT PRIMARY KEY,
                $COL_OP_AMOUNT REAL NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ADD_ITEMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CREDIT_RECEIVED")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_OPENING")
        onCreate(db)
    }

    // --- PAGE 1: additems ---
    fun insertItem(item: LedgerItem): Long {
        val values = ContentValues().apply {
            put(COL_DATE, item.date)
            put(COL_NAME, item.name)
            put(COL_QTY, item.quantity)
            put(COL_RATE, item.rate)
            put(COL_AMOUNT, item.amount)
            put(COL_PAYMENT_MODE, item.paymentMode)
        }
        return writableDatabase.insert(TABLE_ADD_ITEMS, null, values)
    }

    fun getAllItems(): List<LedgerItem> {
        val list = mutableListOf<LedgerItem>()
        readableDatabase.rawQuery("SELECT * FROM $TABLE_ADD_ITEMS ORDER BY $COL_ID DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    LedgerItem(
                        id = c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                        date = c.getString(c.getColumnIndexOrThrow(COL_DATE)),
                        name = c.getString(c.getColumnIndexOrThrow(COL_NAME)),
                        quantity = c.getDouble(c.getColumnIndexOrThrow(COL_QTY)),
                        rate = c.getDouble(c.getColumnIndexOrThrow(COL_RATE)),
                        amount = c.getDouble(c.getColumnIndexOrThrow(COL_AMOUNT)),
                        paymentMode = c.getString(c.getColumnIndexOrThrow(COL_PAYMENT_MODE))
                    )
                )
            }
        }
        return list
    }

    fun deleteItem(id: Int): Int {
        return writableDatabase.delete(TABLE_ADD_ITEMS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // --- PAGE 2: credit_received ---
    fun insertCreditReceived(entry: CreditReceivedItem): Long {
        val values = ContentValues().apply {
            put(COL_CR_DATE, entry.date)
            put(COL_CR_CUSTOMER, entry.customerName)
            put(COL_CR_MODE, entry.paymentMode)
            put(COL_CR_AMOUNT, entry.amount)
        }
        return writableDatabase.insert(TABLE_CREDIT_RECEIVED, null, values)
    }

    fun getAllCreditReceived(): List<CreditReceivedItem> {
        val list = mutableListOf<CreditReceivedItem>()
        readableDatabase.rawQuery("SELECT * FROM $TABLE_CREDIT_RECEIVED ORDER BY $COL_CR_ID DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CreditReceivedItem(
                        id = c.getInt(c.getColumnIndexOrThrow(COL_CR_ID)),
                        date = c.getString(c.getColumnIndexOrThrow(COL_CR_DATE)),
                        customerName = c.getString(c.getColumnIndexOrThrow(COL_CR_CUSTOMER)),
                        paymentMode = c.getString(c.getColumnIndexOrThrow(COL_CR_MODE)),
                        amount = c.getDouble(c.getColumnIndexOrThrow(COL_CR_AMOUNT))
                    )
                )
            }
        }
        return list
    }

    fun deleteCreditReceived(id: Int): Int {
        return writableDatabase.delete(TABLE_CREDIT_RECEIVED, "$COL_CR_ID = ?", arrayOf(id.toString()))
    }

    fun getDistinctCreditCustomers(): List<String> {
        val customers = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT $COL_OP_CUSTOMER FROM $TABLE_OPENING", null).use { c ->
            while (c.moveToNext()) customers.add(c.getString(0))
        }
        readableDatabase.rawQuery(
            "SELECT DISTINCT $COL_NAME FROM $TABLE_ADD_ITEMS WHERE LOWER($COL_NAME) NOT LIKE 'cash%'", null
        ).use { c ->
            while (c.moveToNext()) customers.add(c.getString(0))
        }
        readableDatabase.rawQuery("SELECT DISTINCT $COL_CR_CUSTOMER FROM $TABLE_CREDIT_RECEIVED", null).use { c ->
            while (c.moveToNext()) customers.add(c.getString(0))
        }
        return customers.toList().sorted()
    }

    fun getCustomerBalance(customerName: String): Double {
        var baseOpen = 0.0
        var totalCreditSales = 0.0
        var totalReceived = 0.0

        readableDatabase.rawQuery("SELECT $COL_OP_AMOUNT FROM $TABLE_OPENING WHERE $COL_OP_CUSTOMER = ?", arrayOf(customerName)).use {
            if (it.moveToFirst()) baseOpen = it.getDouble(0)
        }
        readableDatabase.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_ADD_ITEMS WHERE $COL_NAME = ? AND $COL_PAYMENT_MODE = 'Credit'",
            arrayOf(customerName)
        ).use { if (it.moveToFirst()) totalCreditSales = it.getDouble(0) }

        readableDatabase.rawQuery(
            "SELECT SUM($COL_CR_AMOUNT) FROM $TABLE_CREDIT_RECEIVED WHERE $COL_CR_CUSTOMER = ?",
            arrayOf(customerName)
        ).use { if (it.moveToFirst()) totalReceived = it.getDouble(0) }

        return baseOpen + totalCreditSales - totalReceived
    }

    // --- PAGE 3: Closing Sheet ---
    fun setOpeningBalance(customerName: String, amount: Double) {
        val values = ContentValues().apply {
            put(COL_OP_CUSTOMER, customerName.trim())
            put(COL_OP_AMOUNT, amount)
        }
        writableDatabase.insertWithOnConflict(TABLE_OPENING, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getCustomerStatement(customerName: String, fromDate: String, toDate: String): CustomerStatementReport {
        val db = readableDatabase
        var baseOpening = 0.0
        db.rawQuery("SELECT $COL_OP_AMOUNT FROM $TABLE_OPENING WHERE $COL_OP_CUSTOMER = ?", arrayOf(customerName)).use {
            if (it.moveToFirst()) baseOpening = it.getDouble(0)
        }

        var priorSales = 0.0
        db.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_ADD_ITEMS WHERE $COL_NAME = ? AND $COL_PAYMENT_MODE = 'Credit' AND $COL_DATE < ?",
            arrayOf(customerName, fromDate)
        ).use { if (it.moveToFirst()) priorSales = it.getDouble(0) }

        var priorReceived = 0.0
        db.rawQuery(
            "SELECT SUM($COL_CR_AMOUNT) FROM $TABLE_CREDIT_RECEIVED WHERE $COL_CR_CUSTOMER = ? AND $COL_CR_DATE < ?",
            arrayOf(customerName, fromDate)
        ).use { if (it.moveToFirst()) priorReceived = it.getDouble(0) }

        val openingBalance = baseOpening + priorSales - priorReceived
        var runningBalance = openingBalance

        data class RawEvent(val date: String, val sortOrder: Int, val id: Int, val qty: Double, val rate: Double, val amount: Double, val received: Double)
        val events = mutableListOf<RawEvent>()

        db.rawQuery(
            "SELECT $COL_ID, $COL_DATE, $COL_QTY, $COL_RATE, $COL_AMOUNT FROM $TABLE_ADD_ITEMS WHERE $COL_NAME = ? AND $COL_PAYMENT_MODE = 'Credit' AND $COL_DATE BETWEEN ? AND ?",
            arrayOf(customerName, fromDate, toDate)
        ).use { c ->
            while (c.moveToNext()) {
                val qty = c.getDouble(2)
                val rate = c.getDouble(3)
                val amt = if (c.getDouble(4) > 0) c.getDouble(4) else qty * rate
                events.add(RawEvent(c.getString(1), 1, c.getInt(0), qty, rate, amt, 0.0))
            }
        }

        db.rawQuery(
            "SELECT $COL_CR_ID, $COL_CR_DATE, $COL_CR_AMOUNT FROM $TABLE_CREDIT_RECEIVED WHERE $COL_CR_CUSTOMER = ? AND $COL_CR_DATE BETWEEN ? AND ?",
            arrayOf(customerName, fromDate, toDate)
        ).use { c ->
            while (c.moveToNext()) {
                events.add(RawEvent(c.getString(1), 2, c.getInt(0), 0.0, 0.0, 0.0, c.getDouble(2)))
            }
        }

        val sortedEvents = events.sortedWith(compareBy({ it.date }, { it.sortOrder }, { it.id }))
        val rows = mutableListOf<CustomerStatementRow>()
        for (ev in sortedEvents) {
            runningBalance = runningBalance + ev.amount - ev.received
            rows.add(CustomerStatementRow(ev.date, ev.qty, ev.rate, ev.amount, ev.received, runningBalance))
        }
        return CustomerStatementReport(customerName, openingBalance, rows, runningBalance)
    }

    fun getAllCustomersClosingRange(fromDate: String, toDate: String): List<CustomerRangeSummaryRow> {
        val db = readableDatabase
        val summaryList = mutableListOf<CustomerRangeSummaryRow>()

        for (customer in getDistinctCreditCustomers()) {
            var baseOpening = 0.0
            db.rawQuery("SELECT $COL_OP_AMOUNT FROM $TABLE_OPENING WHERE $COL_OP_CUSTOMER = ?", arrayOf(customer)).use {
                if (it.moveToFirst()) baseOpening = it.getDouble(0)
            }

            var priorSales = 0.0
            db.rawQuery(
                "SELECT SUM($COL_AMOUNT) FROM $TABLE_ADD_ITEMS WHERE $COL_NAME = ? AND $COL_PAYMENT_MODE = 'Credit' AND $COL_DATE < ?",
                arrayOf(customer, fromDate)
            ).use { if (it.moveToFirst()) priorSales = it.getDouble(0) }

            var priorIncome = 0.0
            db.rawQuery(
                "SELECT SUM($COL_CR_AMOUNT) FROM $TABLE_CREDIT_RECEIVED WHERE $COL_CR_CUSTOMER = ? AND $COL_CR_DATE < ?",
                arrayOf(customer, fromDate)
            ).use { if (it.moveToFirst()) priorIncome = it.getDouble(0) }

            val openingBal = baseOpening + priorSales - priorIncome

            var rangeSales = 0.0
            db.rawQuery(
                "SELECT SUM($COL_AMOUNT) FROM $TABLE_ADD_ITEMS WHERE $COL_NAME = ? AND $COL_PAYMENT_MODE = 'Credit' AND $COL_DATE BETWEEN ? AND ?",
                arrayOf(customer, fromDate, toDate)
            ).use { if (it.moveToFirst()) rangeSales = it.getDouble(0) }

            var rangeIncome = 0.0
            db.rawQuery(
                "SELECT SUM($COL_CR_AMOUNT) FROM $TABLE_CREDIT_RECEIVED WHERE $COL_CR_CUSTOMER = ? AND $COL_CR_DATE BETWEEN ? AND ?",
                arrayOf(customer, fromDate, toDate)
            ).use { if (it.moveToFirst()) rangeIncome = it.getDouble(0) }

            val closingBal = openingBal + rangeSales - rangeIncome
            if (openingBal != 0.0 || rangeSales != 0.0 || rangeIncome != 0.0 || closingBal != 0.0) {
                summaryList.add(CustomerRangeSummaryRow(customer, openingBal, rangeSales, rangeIncome, closingBal))
            }
        }
        return summaryList
    }
}
