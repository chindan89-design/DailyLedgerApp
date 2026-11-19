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
        try {
            setContentView(R.layout.activity_credit_received)
            dbHelper = DatabaseHelper(this)

            etCrDate = findViewById(R.id.etCrDate)
            actvCustomerName = findViewById(R.id.actvCustomerName)
            tvCustomerBalance = findViewById(R.id.tvCustomerBalance)
            rgCrPaymentMode = findViewById(R.id.rgCrPaymentMode)
            etCrAmount = findViewById(R.id.etCrAmount)
            tvCrSummary = findViewById(R.id.tvCrSummary)
            lvCreditItems = findViewById(R.id.lvCreditItems)

            val btnSave: Button = findViewById(R.id.btnSaveCredit)
            val btnExport: Button = findViewById(R.id.btnExportCredit)
            val btnClosing: Button = findViewById(R.id.btnGoClosingFromCr)

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            etCrDate.setText(sdf.format(Date()))
            etCrDate.setOnClickListener { showDatePicker() }

            refreshSuggestions()

            actvCustomerName.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val cust = s?.toString()?.trim() ?: ""
                    if (cust.isNotEmpty()) {
                        val bal = dbHelper.getCustomerBalance(cust)
                        tvCustomerBalance.text = "Pending ($cust): $bal"
                    } else {
                        tvCustomerBalance.text = "Pending Balance: 0"
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            btnSave.setOnClickListener { saveReceipt() }
            btnExport.setOnClickListener { exportCsv() }
            btnClosing.setOnClickListener { startActivity(Intent(this, ClosingSheetActivity::class.java)) }

            lvCreditItems.setOnItemLongClickListener { _, _, position, _ ->
                val sel = receiptList[position]
                AlertDialog.Builder(this)
                    .setTitle("Delete Receipt")
                    .setMessage("Delete ${sel.customerName}?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteCreditReceived(sel.id)
                        loadReceipts()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            loadReceipts()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshSuggestions() {
        val list = dbHelper.getDistinctCreditCustomers()
        actvCustomerName.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list))
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            etCrDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveReceipt() {
        val date = etCrDate.text.toString().trim()
        val cust = actvCustomerName.text.toString().trim()
        val amt = etCrAmount.text.toString().toDoubleOrNull()
        val mode = when (rgCrPaymentMode.checkedRadioButtonId) {
            R.id.rbByCash -> "Cash"
            R.id.rbByBank -> "Bank"
            else -> "Adjustment"
        }
        if (date.isEmpty() || cust.isEmpty() || amt == null || amt <= 0) {
            Toast.makeText(this, "Enter Customer and Amount", Toast.LENGTH_SHORT).show()
            return
        }
        val item = CreditReceivedItem(date = date, customerName = cust, paymentMode = mode, amount = amt)
        if (dbHelper.insertCreditReceived(item) != -1L) {
            Toast.makeText(this, "Saved ($mode)", Toast.LENGTH_SHORT).show()
            actvCustomerName.text.clear()
            etCrAmount.text.clear()
            actvCustomerName.requestFocus()
            refreshSuggestions()
            loadReceipts()
        }
    }

    private fun loadReceipts() {
        receiptList = dbHelper.getAllCreditReceived()
        val rows = receiptList.map { "${it.date} | ${it.customerName} [${it.paymentMode}]\nReceived: ${it.amount}" }
        lvCreditItems.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        val total = receiptList.sumOf { it.amount }
        val cash = receiptList.filter { it.paymentMode == "Cash" }.sumOf { it.amount }
        val bank = receiptList.filter { it.paymentMode == "Bank" }.sumOf { it.amount }
        val adj = receiptList.filter { it.paymentMode == "Adjustment" }.sumOf { it.amount }
        tvCrSummary.text = "Total: $total | Cash: $cash | Bank: $bank | Adj: $adj"
    }

    private fun exportCsv() {
        if (receiptList.isEmpty()) {
            Toast.makeText(this, "No receipts", Toast.LENGTH_SHORT).show()
            return
        }
        val csvFile = File(cacheDir, "credit_received_export.csv")
        csvFile.writeText("\uFEFFID,Date,Customer_Name,Payment_Mode,Amount\n", Charsets.UTF_8)
        for (i in receiptList) {
            csvFile.appendText("${i.id},${i.date},\"${i.customerName}\",${i.paymentMode},${i.amount}\n", Charsets.UTF_8)
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", csvFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export CSV"))
    }
}
