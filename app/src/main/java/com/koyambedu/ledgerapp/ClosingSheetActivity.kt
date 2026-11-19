package com.koyambedu.ledgerapp

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ClosingSheetActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var rgMode: RadioGroup
    private lateinit var rbSingle: RadioButton
    private lateinit var rbAll: RadioButton
    private lateinit var etFrom: EditText
    private lateinit var etTo: EditText
    private lateinit var actvCust: AutoCompleteTextView
    private lateinit var tvHead: TextView
    private lateinit var llHead: LinearLayout
    private lateinit var tlTable: TableLayout
    private lateinit var tvTot: TextView

    private var singleRep: CustomerStatementReport? = null
    private var allRep: List<CustomerRangeSummaryRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_closing_sheet)
            dbHelper = DatabaseHelper(this)
            rgMode = findViewById(R.id.rgReportMode)
            rbSingle = findViewById(R.id.rbSingleCustomer)
            rbAll = findViewById(R.id.rbAllCustomers)
            etFrom = findViewById(R.id.etFromDate)
            etTo = findViewById(R.id.etToDate)
            actvCust = findViewById(R.id.actvSearchCustomer)
            tvHead = findViewById(R.id.tvReportHeader)
            llHead = findViewById(R.id.llTableHeader)
            tlTable = findViewById(R.id.tlReportTable)
            tvTot = findViewById(R.id.tvReportTotals)

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            etFrom.setText(today)
            etTo.setText(today)
            etFrom.setOnClickListener { pickDate(etFrom) }
            etTo.setOnClickListener { pickDate(etTo) }

            loadNames()
            rgMode.setOnCheckedChangeListener { _, id ->
                if (id == R.id.rbSingleCustomer) {
                    actvCust.visibility = View.VISIBLE
                    tvHead.text = "Select Customer"
                    tlTable.removeAllViews()
                    llHead.removeAllViews()
                } else {
                    actvCust.visibility = View.GONE
                    runReport()
                }
            }

            actvCust.setOnItemClickListener { _, _, _, _ -> runReport() }
            findViewById<Button>(R.id.btnGenerateReport).setOnClickListener { runReport() }
            findViewById<Button>(R.id.btnSetOpening).setOnClickListener { openDialog() }
            findViewById<Button>(R.id.btnExportReport).setOnClickListener { exportCsv() }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadNames() {
        val list = dbHelper.getDistinctCreditCustomers()
        actvCust.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list))
    }

    private fun pickDate(et: EditText) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            et.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            if (rbAll.isChecked || actvCust.text.isNotEmpty()) runReport()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun runReport() {
        val f = etFrom.text.toString().trim()
        val t = etTo.text.toString().trim()
        if (rbSingle.isChecked) {
            val name = actvCust.text.toString().trim()
            if (name.isEmpty()) return
            showSingle(name, f, t)
        } else {
            showAll(f, t)
        }
    }

    private fun showSingle(name: String, f: String, t: String) {
        val rep = dbHelper.getCustomerStatement(name, f, t)
        singleRep = rep
        allRep = emptyList()
        tvHead.text = "${rep.customerName} | Open: ${fmt(rep.openingBalance)}"
        llHead.removeAllViews()
        llHead.addView(cell("Date", 1.3f, Gravity.START, true, true))
        llHead.addView(cell("Qty", 0.8f, Gravity.END, true, true))
        llHead.addView(cell("Rate", 0.8f, Gravity.END, true, true))
        llHead.addView(cell("Amt", 1.1f, Gravity.END, true, true))
        llHead.addView(cell("Rec", 1.1f, Gravity.END, true, true))
        llHead.addView(cell("Close", 1.2f, Gravity.END, true, true))

        tlTable.removeAllViews()
        rep.rows.forEachIndexed { i, r ->
            val tr = TableRow(this)
            tr.setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#F4F7F7"))
            tr.setPadding(6, 10, 6, 10)
            tr.addView(cell(if (r.date.length >= 10) r.date.substring(5) else r.date, 1.3f, Gravity.START, false, false))
            tr.addView(cell(if (r.quantity > 0) fmt(r.quantity) else "-", 0.8f, Gravity.END, false, false))
            tr.addView(cell(if (r.rate > 0) fmt(r.rate) else "-", 0.8f, Gravity.END, false, false))
            tr.addView(cell(if (r.amount > 0) fmt(r.amount) else "-", 1.1f, Gravity.END, false, false))
            tr.addView(cell(if (r.receivedAmount > 0) fmt(r.receivedAmount) else "-", 1.1f, Gravity.END, false, false))
            tr.addView(cell(fmt(r.closingBalance), 1.2f, Gravity.END, true, false))
            tlTable.addView(tr)
        }
        val tq = fmt(rep.rows.sumOf { it.quantity })
        val ts = fmt(rep.rows.sumOf { it.amount })
        val trc = fmt(rep.rows.sumOf { it.receivedAmount })
        tvTot.text = "Qty: $tq | Sales: $ts | Rec: $trc | Close: ${fmt(rep.finalClosingBalance)}"
    }

    private fun showAll(f: String, t: String) {
        val list = dbHelper.getAllCustomersClosingRange(f, t)
        allRep = list
        singleRep = null
        tvHead.text = "All Customers ($f to $t)"
        llHead.removeAllViews()
        llHead.addView(cell("Name", 1.5f, Gravity.START, true, true))
        llHead.addView(cell("Open", 1.0f, Gravity.END, true, true))
        llHead.addView(cell("Sales", 1.0f, Gravity.END, true, true))
        llHead.addView(cell("Rec", 1.0f, Gravity.END, true, true))
        llHead.addView(cell("Close", 1.1f, Gravity.END, true, true))

        tlTable.removeAllViews()
        list.forEachIndexed { i, r ->
            val tr = TableRow(this)
            tr.setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#F4F7F7"))
            tr.setPadding(6, 10, 6, 10)
            tr.addView(cell(r.customerName, 1.5f, Gravity.START, false, false))
            tr.addView(cell(fmt(r.openingBalance), 1.0f, Gravity.END, false, false))
            tr.addView(cell(fmt(r.sales), 1.0f, Gravity.END, false, false))
            tr.addView(cell(fmt(r.income), 1.0f, Gravity.END, false, false))
            tr.addView(cell(fmt(r.closingBalance), 1.1f, Gravity.END, true, false))
            tlTable.addView(tr)
        }
        tvTot.text = "Open: ${fmt(list.sumOf { it.openingBalance })} | Sales: ${fmt(list.sumOf { it.sales })} | Rec: ${fmt(list.sumOf { it.income })} | Close: ${fmt(list.sumOf { it.closingBalance })}"
    }

    private fun cell(txt: String, w: Float, g: Int, bold: Boolean, header: Boolean): TextView {
        val tv = TextView(this)
        tv.text = txt
        tv.gravity = g
        tv.textSize = 11f
        tv.setTextColor(if (header) Color.WHITE else Color.BLACK)
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        tv.layoutParams = if (header) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
        else TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, w)
        return tv
    }

    private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v)

    private fun openDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 20, 40, 10)
        val n = AutoCompleteTextView(this)
        n.hint = "Customer Name"
        n.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dbHelper.getDistinctCreditCustomers()))
        val a = EditText(this)
        a.hint = "Opening Balance"
        box.addView(n)
        box.addView(a)
        AlertDialog.Builder(this).setTitle("Opening Balance").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val amt = a.text.toString().toDoubleOrNull()
                if (n.text.isNotEmpty() && amt != null) {
                    dbHelper.setOpeningBalance(n.text.toString().trim(), amt)
                    loadNames()
                    runReport()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun exportCsv() {
        val f = etFrom.text.toString().trim()
        val t = etTo.text.toString().trim()
        val file = File(cacheDir, "Closing_${f}_to_${t}.csv")
        if (rbSingle.isChecked) {
            val rep = singleRep ?: return
            file.writeText("\uFEFFCustomer:,\"${rep.customerName}\"\nDate,Qty,Rate,Amt,Rec,Close\n", Charsets.UTF_8)
            rep.rows.forEach { file.appendText("${it.date},${it.quantity},${it.rate},${it.amount},${it.receivedAmount},${it.closingBalance}\n", Charsets.UTF_8) }
        } else {
            if (allRep.isEmpty()) return
            file.writeText("\uFEFFCustomer,Open,Sales,Rec,Close\n", Charsets.UTF_8)
            allRep.forEach { file.appendText("\"${it.customerName}\",${it.openingBalance},${it.sales},${it.income},${it.closingBalance}\n", Charsets.UTF_8) }
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Export CSV"))
    }
}
