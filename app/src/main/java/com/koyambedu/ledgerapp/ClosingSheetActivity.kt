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

            refreshCustomerAutocomplete()

            rgReportMode.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.rbSingleCustomer) {
                    actvSearchCustomer.visibility = View.VISIBLE
                    tvReportHeader.text = "Select Customer & Date Range"
                    tlReportTable.removeAllViews()
                    llTableHeader.removeAllViews()
                } else {
                    actvSearchCustomer.visibility = View.GONE
                    generateReport()
                }
            }

            actvSearchCustomer.setOnItemClickListener { _, _, _, _ -> generateReport() }

            btnGenerateReport.setOnClickListener { generateReport() }
            btnSetOpening.setOnClickListener { showOpeningBalanceDialog() }
            btnExportReport.setOnClickListener { exportReportToCsv() }
        } catch (e: Exception) {
            Toast.makeText(this, "Closing Sheet Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshCustomerAutocomplete() {
        val customers = dbHelper.getDistinctCreditCustomers()
        actvSearchCustomer.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customers)
        )
    }

    private fun showDatePicker(targetEditText: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                targetEditText.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
                if (rbAllCustomers.isChecked || actvSearchCustomer.text.isNotEmpty()) {
                    generateReport()
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun generateReport() {
        val fromDate = etFromDate.text.toString().trim()
        val toDate = etToDate.text.toString().trim()

        if (rbSingleCustomer.isChecked) {
            val customer = actvSearchCustomer.text.toString().trim()
            if (customer.isEmpty()) {
                Toast.makeText(this, "Enter Customer Name", Toast.LENGTH_SHORT).show()
                return
            }
            renderSingleCustomerStatement(customer, fromDate, toDate)
        } else {
            renderAllCustomersSummary(fromDate, toDate)
        }
    }

    private fun renderSingleCustomerStatement(customer: String, fromDate: String, toDate: String) {
        val report = dbHelper.getCustomerStatement(customer, fromDate, toDate)
        currentSingleReport = report
        currentAllReport = emptyList()

        tvReportHeader.text =
            "Customer: ${report.customerName}  \vert{}  Opening Bal: ₹${formatNum(report.openingBalance)}"

        llTableHeader.removeAllViews()
        llTableHeader.addView(createHeaderCell("Date", 1.3f, Gravity.START))
        llTableHeader.addView(createHeaderCell("Qty", 0.8f, Gravity.END))
        llTableHeader.addView(createHeaderCell("Rate", 0.8f, Gravity.END))
        llTableHeader.addView(createHeaderCell("Amount", 1.1f, Gravity.END))
        llTableHeader.addView(createHeaderCell("Recvd", 1.1f, Gravity.END))
        llTableHeader.addView(createHeaderCell("Closing", 1.2f, Gravity.END))

        tlReportTable.removeAllViews()
        report.rows.forEachIndexed { index, row ->
            val tr = TableRow(this).apply {
                setBackgroundColor(if (index % 2 == 0) Color.WHITE else Color.parseColor("#F4F7F7"))
                setPadding(6, 10, 6, 10)
            }
            val shortDate = if (row.date.length >= 10) row.date.substring(5) else row.date
            tr.addView(createDataCell(shortDate, 1.3f, Gravity.START, false))
            tr.addView(createDataCell(if (
