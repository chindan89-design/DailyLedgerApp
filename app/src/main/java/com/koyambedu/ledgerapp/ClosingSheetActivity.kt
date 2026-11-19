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
    private lateinit var rgReportMode: RadioGroup
    private lateinit var rbSingleCustomer: RadioButton
    private lateinit var rbAllCustomers: RadioButton
    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var actvSearchCustomer: AutoCompleteTextView
    private lateinit var tvReportHeader: TextView
    private lateinit var llTableHeader: LinearLayout
    private lateinit var tlReportTable: TableLayout
    private lateinit var tvReportTotals: TextView

    private var currentSingleReport: CustomerStatementReport? = null
    private var currentAllReport: List<CustomerRangeSummaryRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_closing_sheet)
            dbHelper = DatabaseHelper(this)

            rgReportMode = findViewById(R.id.rgReportMode)
            rbSingleCustomer = findViewById(R.id.rbSingleCustomer)
            rbAllCustomers = findViewById(R.id.rbAllCustomers)
            etFromDate = findViewById(R.id.etFromDate)
            etToDate = findViewById(R.id.etToDate)
            actvSearchCustomer = findViewById(R.id.actvSearchCustomer)
            tvReportHeader = findViewById(R.id.tvReportHeader)
            llTableHeader = findViewById(R.id.llTableHeader)
            tlReportTable = findViewById(R.id.tlReportTable)
            tvReportTotals = findViewById(R.id.tvReportTotals)

            val btnSetOpening: Button = findViewById(R.id.btnSetOpening)
            val btnGenerateReport: Button = findViewById(R.id.btnGenerateReport)
            val btnExportReport: Button = findViewById(R.id.btnExportReport)

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = sdf.format(Date())
            etFromDate.setText(today)
            etToDate.setText(today)

            etFromDate.setOnClickListener { showDatePicker(etFromDate) }
            etToDate.setOnClickListener { showDatePicker(etToDate) }

            refreshAutocomplete()

            rgReportMode.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.rbSingleCustomer) {
                    actvSearchCustomer.visibility = View.VISIBLE
                    tvReportHeader.text = "Select Customer"
                    tlReportTable.removeAllViews()
                    llTableHeader.removeAllViews()
                } else {
                    actvSearchCustomer.visibility = View.GONE
                    generateReport()
                }
            }

            actvSearchCustomer.setOnItemClickListener { _, _, _, _ -> generateReport() }
            btnGenerateReport.setOnClickListener { generateReport() }
            btnSetOpening.setOnClickListener { showOpeningDialog() }
            btnExportReport.setOnClickListener { exportCsv() }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshAutocomplete() {
        val list = dbHelper.getDistinctCreditCustomers()
        actvSearchCustomer.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list))
    }

    private fun showDatePicker(target: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            target.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            if (rbAllCustomers.isChecked || actvSearchCustomer.text.isNotEmpty()) generateReport()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun generateReport() {
        val fromDate = etFromDate.text.toString().trim()
        val toDate = etToDate.text.toString().trim()
        if (rbSingleCustomer.isChecked) {
            val cust = actvSearchCustomer.text.toString().trim()
            if (cust.isEmpty()) {
                Toast.makeText(this, "Enter Customer Name", Toast.LENGTH_SHORT).show()
                return
            }
            renderSingle(cust, fromDate, toDate)
        } else {
            renderAll(fromDate, toDate)
        }
    }

    private fun renderSingle(cust: String, fromDate: String, toDate: String) {
        val report = dbHelper.getCustomerStatement(cust, fromDate, toDate)
        currentSingleReport = report
        currentAllReport = emptyList()
        tvReportHeader.text = "${report.customerName} \vert{} Open:${fmt(report.openingBalance)}"

        llTableHeader.removeAllViews()
        llTableHeader.addView(hCell("Date", 1.3f, Gravity.START))
        llTableHeader.addView(hCell("Qty", 0.8f, Gravity.END))
        llTableHeader.addView(hCell("Rate", 0.8f, Gravity.END))
        llTableHeader.addView(hCell("Amt", 1.1f, Gravity.END))
        llTableHeader.addView(hCell("Rec", 1.1f, Gravity.END))
        llTableHeader.addView(hCell("Close", 1.2f, Gravity.END))

        tlReportTable.removeAllViews()
        report.rows.forEachIndexed { i, r ->
            val tr = TableRow(this)
            tr.setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#F4F7F7"))
            tr.setPadding(6, 10, 6, 10)
            val dt = if (r.date.length >= 10) r.date.substring(5) else r.date
            tr.addView(dCell(dt, 1.3f, Gravity.START, false))
            tr.addView(dCell(if (r.quantity > 0) fmt(r.quantity) else "-", 0.8f, Gravity.END, false))
            tr.addView(dCell(if (r.rate > 0) fmt(r.rate) else "-", 0.8f, Gravity.END, false))
            tr.addView(dCell(if (r.amount > 0) fmt(r.amount) else "-", 1.1f, Gravity.END, false))
            tr.addView(dCell(if (r.receivedAmount > 0) fmt(r.receivedAmount) else "-", 1.1f, Gravity.END, false))
            tr.addView(dCell(fmt(r.closingBalance), 1.2f, Gravity.END, true))
            tlReportTable.addView(tr)
        }

        val tQty = report.rows.sumOf { it.quantity }
        val tSales = report.rows.sumOf { it.amount }
        val tRec = report.rows.sumOf { it.receivedAmount }
        tvReportTotals.text = "Qty: ${fmt(tQty)} \vert{} Sales:${fmt(tSales)} | Rec: ${fmt(tRec)} \vert{} Close:${fmt(report.finalClosingBalance)}"
    }

    private fun renderAll(fromDate: String, toDate: String) {
        val list = dbHelper.getAllCustomersClosingRange(fromDate, toDate)
        currentAllReport = list
        currentSingleReport = null
        tvReportHeader.text = "All Customers ($fromDate to$toDate)"

        llTableHeader.removeAllViews()
        llTableHeader.addView(hCell("Name", 1.5f, Gravity.START))
        llTableHeader.
