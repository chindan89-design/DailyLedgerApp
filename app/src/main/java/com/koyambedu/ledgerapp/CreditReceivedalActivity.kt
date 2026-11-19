package com.koyambedu.ledgerapp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CreditReceivedActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etCrDate: EditText
    private lateinit var actvCustomerName: AutoCompleteTextView
    private lateinit var tvCustomerBalance: TextView
    private lateinit var rgCrPaymentMode: RadioGroup
    private lateinit var etCrAmount: EditText
    private lateinit var tvCrSummary: TextView
    private lateinit var lvCreditItems: ListView

    private var receiptList = listOf<CreditReceivedItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credit_received)

        dbHelper = DatabaseHelper(this)

        etCrDate = findViewById(R.id.etCrDate)
        actvCustomerName = findViewById(R.id.actvCustomerName)
        tvCustomerBalance = findViewById(R.id.tvCustomerBalance)
        rgCrPaymentMode = findViewById(R.id.rgCrPaymentMode)
        etCrAmount = findViewById(R.id.etCrAmount)
        tvCrSummary = findViewById(R.id.tvCrSummary)
        lvCreditItems = findViewById(R.id.lvCreditItems)

        val btnSaveCredit: Button = findViewById(R.id.btnSaveCredit)
        val btnExportCredit: Button = findViewById(R.id.btnExportCredit)
        val btnGoClosing: Button = findViewById(R.id.btnGoClosingFromCr)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        etCrDate.setText(sdf.format(Date()))
        etCrDate.setOnClickListener { showDatePicker() }

        refreshCustomerSuggestions()

        actvCustomerName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val cust = s?.toString()?.trim() ?: ""
                if (cust.isNotEmpty()) {
                    val balance = dbHelper.getCustomerBalance(cust)
                    tvCustomerBalance.text = "Pending Credit Balance ($cust): ₹$balance"
                } else {
                    tvCustomerBalance.text = "Pending Credit Balance: ₹0"
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSaveCredit.setOnClickListener { saveReceipt() }
        btnExportCredit.setOnClickListener { exportReceiptsToCsv() }
        btnGoClosing.setOnClickListener { startActivity(Intent(this, ClosingSheetActivity::class.java)) }

        lvCreditItems.setOnItemLongClickListener { _, _, position, _ ->
            val selected = receiptList[position]
            AlertDialog.Builder(this)
                .setTitle("Delete Receipt")
                .setMessage("Delete ${selected.customerName} (₹${selected.amount} - ${selected.paymentMode})?")
                .setPositiveButton("Delete") { _, _ ->
                    dbHelper.deleteCreditReceived(selected.id)
                    loadReceipts()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        loadReceipts()
    }

    private fun refreshCustomerSuggestions() {
        val customers = dbHelper.getDistinctCreditCustomers()
        actvCustomerName.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customers))
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                etCrDate.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveReceipt() {
        val date = etCrDate.text.toString().trim()
        val customer = actvCustomerName.text.toString().trim()
        val amount = etCrAmount.text.toString().toDoubleOrNull()

        val paymentMode = when (rgCrPaymentMode.checkedRadioButtonId) {
            R.id.rbByCash -> "Cash"
            R.id.rbByBank -> "Bank"
            else -> "Adjustment"
        }

        if (date.isEmpty() || customer.isEmpty() || amount == null || amount <= 0) {
            Toast.makeText(this, "Enter Customer Name and Amount", Toast.LENGTH_SHORT).show()
            return
        }

        val entry = CreditReceivedItem(date = date, customerName = customer, paymentMode = paymentMode, amount = amount)
        if (dbHelper.insertCreditReceived(entry) != -1L) {
            Toast.makeText(this, "Saved Receipt ($paymentMode)", Toast.LENGTH_SHORT).show()
            actvCustomerName.text.clear()
            etCrAmount.text.clear()
            actvCustomerName.requestFocus()
            refreshCustomerSuggestions()
            loadReceipts()
        }
    }

    private fun loadReceipts() {
        receiptList = dbHelper.getAllCreditReceived()
        val displayRows = receiptList.map {
            "${it.date} | ${it.customerName} [${it.paymentMode}]
Received: ₹${it.amount}"
        }
        lvCreditItems.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayRows)

        val totalRec = receiptList.sumOf { it.amount }
        val cashRec = receiptList.filter { it.paymentMode == "Cash" }.sumOf { it.amount }
        val bankRec = receiptList.filter { it.paymentMode == "Bank" }.sumOf { it.amount }
        val adjRec = receiptList.filter { it.paymentMode == "Adjustment" }.sumOf { it.amount }

        tvCrSummary.text = "Total Received: ₹$totalRec
Cash: ₹$cashRec | Bank: ₹$bankRec | Adj: ₹$adjRec"
    }

    private fun exportReceiptsToCsv() {
        if (receiptList.isEmpty()) {
            Toast.makeText(this, "No receipts to export", Toast.LENGTH_SHORT).show()
            return
        }
        val csvFile = File(cacheDir, "credit_received_export.csv")
        csvFile.writeText("﻿ID,Date,Customer_Name,Payment_Mode,By_Cash,By_Bank,By_Adjustment,Total_Amount
", Charsets.UTF_8)
        for (item in receiptList) {
            val cashCol = if (item.paymentMode == "Cash") item.amount else 0.0
            val bankCol = if (item.paymentMode == "Bank") item.amount else 0.0
            val adjCol = if (item.paymentMode == "Adjustment") item.amount else 0.0
            csvFile.appendText(
                "${item.id},${item.date},"${item.customerName}",${item.paymentMode},$cashCol,$bankCol,$adjCol,${item.amount}
",
                Charsets.UTF_8
            )
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", csvFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Export Credit Received to Excel / WhatsApp"))
    }
}
