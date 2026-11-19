package com.koyambedu.ledgerapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class LedgerItem(
    val id: Long = 0,
    val date: String,
    val name: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val paymentMode: String
)

data class CreditReceivedItem(
    val id: Long = 0,
    val date: String,
    val customerName: String,
    val paymentMode: String,
    val amount: Double
)

data class CustomerMaster(
    val name: String,
    val address1: String,
    val address2: String,
    val area: String,
    val pincode: String,
    val contact: String,
    val group: String,
    val opBal: Double
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

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "DailyLedger.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE additems (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                name TEXT NOT NULL,
                quantity REAL NOT NULL,
                rate REAL NOT NULL,
                amount REAL NOT NULL,
                payment_mode TEXT NOT NULL DEFAULT 'Credit'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE credit_received (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                customer_name TEXT NOT NULL,
                payment_mode TEXT NOT NULL,
                amount REAL NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE customer_opening (
                customer_name TEXT PRIMARY KEY COLLATE NOCASE,
                opening_balance REAL NOT NULL DEFAULT 0,
                address1 TEXT DEFAULT '',
                address2 TEXT DEFAULT '',
                area TEXT DEFAULT '',
                pincode TEXT DEFAULT '',
                contact TEXT DEFAULT '',
                cust_group TEXT DEFAULT ''
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS credit_received (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, customer_name TEXT NOT NULL, payment_mode TEXT NOT NULL, amount REAL NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS customer_opening (customer_name TEXT PRIMARY KEY COLLATE NOCASE, opening_balance REAL NOT NULL DEFAULT 0)")
        }
        if (oldVersion < 3) {
            val cols = listOf("address1", "address2", "area", "pincode", "contact", "cust_group")
            for (col in cols) {
                try {
                    db.execSQL("ALTER TABLE customer_opening ADD COLUMN $col TEXT DEFAULT ''")
                } catch (_: Exception) {}
            }
        }
    }

    fun saveCustomerMaster(c: CustomerMaster) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("customer_name", c.name.trim())
            put("opening_balance", c.opBal)
            put("address1", c.address1)
            put("address2", c.address2)
            put("area", c.area)
            put("pincode", c.pincode)
            put("contact", c.contact)
            put("cust_group", c.group)
        }
        db.insertWithOnConflict("customer_opening", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllCustomerMasters(): List<CustomerMaster> {
        val list = mutableListOf<CustomerMaster>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM customer_opening ORDER BY customer_name ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CustomerMaster(
                        name = it.getString(it.getColumnIndexOrThrow("customer_name")),
                        address1 = it.getString(it.getColumnIndexOrThrow("address1")) ?: "",
                        address2 = it.getString(it.getColumnIndexOrThrow("address2")) ?: "",
                        area = it.getString(it.getColumnIndexOrThrow("area")) ?: "",
                        pincode = it.getString(it.getColumnIndexOrThrow("pincode")) ?: "",
                        contact = it.getString(it.getColumnIndexOrThrow("contact")) ?: "",
                        group = it.getString(it.getColumnIndexOrThrow("cust_group")) ?: "",
                        opBal = it.getDouble(it.getColumnIndexOrThrow("opening_balance"))
                    )
                )
            }
        }
        return list
    }

    fun deleteCustomerMaster(name: String) {
        writableDatabase.delete("customer_opening", "LOWER(customer_name) = LOWER(?)", arrayOf(name.trim()))
    }

    fun insertItem(item: LedgerItem): Long {
        val values = ContentValues().apply {
            put("date", item.date)
            put("name", item.name)
            put("quantity", item.quantity)
            put("rate", item.rate)
            put("amount", item.amount)
            put("payment_mode", item.paymentMode)
        }
        return writableDatabase.insert("additems", null, values)
    }

    fun getAllItems(): List<LedgerItem> {
        val list = mutableListOf<LedgerItem>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM additems ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LedgerItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        date = it.getString(it.getColumnIndexOrThrow("date")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        quantity = it.getDouble(it.getColumnIndexOrThrow("quantity")),
                        rate = it.getDouble(it.getColumnIndexOrThrow("rate")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        paymentMode = it.getString(it.getColumnIndexOrThrow("payment_mode")) ?: "Credit"
                    )
                )
            }
        }
        return list
    }

    fun deleteItem(id: Long) {
        writableDatabase.delete("additems", "id = ?", arrayOf(id.toString()))
    }

    fun insertCreditReceived(entry: CreditReceivedItem): Long {
        val values = ContentValues().apply {
            put("date", entry.date)
            put("customer_name", entry.customerName)
            put("payment_mode", entry.paymentMode)
            put("amount", entry.amount)
        }
        return writableDatabase.insert("credit_received", null, values)
    }

    fun getAllCreditReceived(): List<CreditReceivedItem> {
        val list = mutableListOf<CreditReceivedItem>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM credit_received ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CreditReceivedItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        date = it.getString(it.getColumnIndexOrThrow("date")),
                        customerName = it.getString(it.getColumnIndexOrThrow("customer_name")),
                        paymentMode = it.getString(it.getColumnIndexOrThrow("payment_mode")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount"))
                    )
                )
            }
        }
        return list
    }

    fun deleteCreditReceived(id: Long) {
        writableDatabase.delete("credit_received", "id = ?", arrayOf(id.toString()))
    }

    fun getDistinctCreditCustomers(): List<String> {
        val names = linkedSetOf<String>()
        val db = readableDatabase
        db.rawQuery("SELECT DISTINCT customer_name FROM customer_opening", null).use {
            while (it.moveToNext()) names.add(it.getString(0).trim())
        }
        db.rawQuery("SELECT DISTINCT name FROM additems WHERE payment_mode = 'Credit' AND LOWER(TRIM(name)) != 'cash'", null).use {
            while (it.moveToNext()) names.add(it.getString(0).trim())
        }
        db.rawQuery("SELECT DISTINCT customer_name FROM credit_received", null).use {
            while (it.moveToNext()) names.add(it.getString(0).trim())
        }
        return names.sorted()
    }

    fun getCustomerBalance(customerName: String): Double {
        val db = readableDatabase
        val clean = customerName.trim()
        var initOp = 0.0
        db.rawQuery("SELECT opening_balance FROM customer_opening WHERE LOWER(TRIM(customer_name)) = LOWER(?)", arrayOf(clean)).use {
            if (it.moveToFirst()) initOp = it.getDouble(0)
        }
        var totalCredit = 0.0
        db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM additems WHERE LOWER(TRIM(name)) = LOWER(?) AND payment_mode = 'Credit'", arrayOf(clean)).use {
            if (it.moveToFirst()) totalCredit = it.getDouble(0)
        }
        var totalRecvd = 0.0
        db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM credit_received WHERE LOWER(TRIM(customer_name)) = LOWER(?)", arrayOf(clean)).use {
            if (it.moveToFirst()) totalRecvd = it.getDouble(0)
        }
        return initOp + totalCredit - totalRecvd
    }

    fun setOpeningBalance(customerName: String, amount: Double) {
        val db = writableDatabase
        val clean = customerName.trim()
        db.execSQL(
            "INSERT INTO customer_opening (customer_name, opening_balance) VALUES (?, ?) ON CONFLICT(customer_name) DO UPDATE SET opening_balance = excluded.opening_balance",
            arrayOf(clean, amount)
        )
    }

    fun getCustomerStatement(customerName: String, fromDate: String, toDate: String): CustomerStatementReport {
        val db = readableDatabase
        val clean = customerName.trim()
        var initialOpening = 0.0
        db.rawQuery("SELECT opening_balance FROM customer_opening WHERE LOWER(TRIM(customer_name)) = LOWER(?)", arrayOf(clean)).use {
            if (it.moveToFirst()) initialOpening = it.getDouble(0)
        }
        var priorSales = 0.0
        db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM additems WHERE LOWER(TRIM(name)) = LOWER(?) AND payment_mode = 'Credit' AND date < ?", arrayOf(clean, fromDate)).use {
            if (it.moveToFirst()) priorSales = it.getDouble(0)
        }
        var priorRecvd = 0.0
        db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM credit_received WHERE LOWER(TRIM(customer_name)) = LOWER(?) AND date < ?", arrayOf(clean, fromDate)).use {
            if (it.moveToFirst()) priorRecvd = it.getDouble(0)
        }
        val openingAsOfFromDate = initialOpening + priorSales - priorRecvd
        var runningBalance = openingAsOfFromDate

        data class RawEvent(val date: String, val qty: Double, val rate: Double, val amount: Double, val recvd: Double, val sortOrder: Int)
        val events = mutableListOf<RawEvent>()

        db.rawQuery("SELECT date, quantity, rate, amount FROM additems WHERE LOWER(TRIM(name)) = LOWER(?) AND payment_mode = 'Credit' AND date BETWEEN ? AND ?", arrayOf(clean, fromDate, toDate)).use {
            while (it.moveToNext()) {
                events.add(RawEvent(it.getString(0), it.getDouble(1), it.getDouble(2), it.getDouble(3), 0.0, 1))
            }
        }
        db.rawQuery("SELECT date, amount FROM credit_received WHERE LOWER(TRIM(customer_name)) = LOWER(?) AND date BETWEEN ? AND ?", arrayOf(clean, fromDate, toDate)).use {
            while (it.moveToNext()) {
                events.add(RawEvent(it.getString(0), 0.0, 0.0, 0.0, it.getDouble(1), 2))
            }
        }

        val sortedEvents = events.sortedWith(compareBy({ it.date }, { it.sortOrder }))
        val rows = sortedEvents.map { ev ->
            runningBalance = runningBalance + ev.amount - ev.recvd
            CustomerStatementRow(ev.date, ev.qty, ev.rate, ev.amount, ev.recvd, runningBalance)
        }
        return CustomerStatementReport(clean, openingAsOfFromDate, rows, runningBalance)
    }

    fun getAllCustomersClosingRange(fromDate: String, toDate: String): List<CustomerRangeSummaryRow> {
        val customers = getDistinctCreditCustomers()
        val db = readableDatabase
        val result = mutableListOf<CustomerRangeSummaryRow>()

        for (cust in customers) {
            var initialOpening = 0.0
            db.rawQuery("SELECT opening_balance FROM customer_opening WHERE LOWER(TRIM(customer_name)) = LOWER(?)", arrayOf(cust)).use {
                if (it.moveToFirst()) initialOpening = it.getDouble(0)
            }
            var priorSales = 0.0
            db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM additems WHERE LOWER(TRIM(name)) = LOWER(?) AND payment_mode = 'Credit' AND date < ?", arrayOf(cust, fromDate)).use {
                if (it.moveToFirst()) priorSales = it.getDouble(0)
            }
            var priorRecvd = 0.0
            db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM credit_received WHERE LOWER(TRIM(customer_name)) = LOWER(?) AND date < ?", arrayOf(cust, fromDate)).use {
                if (it.moveToFirst()) priorRecvd = it.getDouble(0)
            }
            val opening = initialOpening + priorSales - priorRecvd

            var rangeSales = 0.0
            db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM additems WHERE LOWER(TRIM(name)) = LOWER(?) AND payment_mode = 'Credit' AND date BETWEEN ? AND ?", arrayOf(cust, fromDate, toDate)).use {
                if (it.moveToFirst()) rangeSales = it.getDouble(0)
            }
            var rangeIncome = 0.0
            db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM credit_received WHERE LOWER(TRIM(customer_name)) = LOWER(?) AND date BETWEEN ? AND ?", arrayOf(cust, fromDate, toDate)).use {
                if (it.moveToFirst()) rangeIncome = it.getDouble(0)
            }
            val closing = opening + rangeSales - rangeIncome
            if (opening != 0.0 || rangeSales != 0.0 || rangeIncome != 0.0 || closing != 0.0) {
                result.add(CustomerRangeSummaryRow(cust, opening, rangeSales, rangeIncome, closing))
            }
        }
        return result
    }
}
