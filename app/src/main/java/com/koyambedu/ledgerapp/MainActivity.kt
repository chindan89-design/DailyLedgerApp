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

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etDate: EditText
    private lateinit var etName: EditText
    private lateinit var etQuantity: EditText
    private lateinit var etRate: EditText
    private lateinit var etAmount: EditText
    private lateinit var rgPaymentMode: RadioGroup
    private lateinit var rbCash: RadioButton
    private lateinit var rbBank: RadioButton
    private lateinit var rbCredit: RadioButton
    private lateinit var tvSummary: TextView
    private lateinit var lvItems: ListView
    private var itemList = listOf<LedgerItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            dbHelper = DatabaseHelper(this)

            etDate = findViewById(R.id.etDate)
            etName = findViewById(R.id.etName)
            etQuantity = findViewById(R.id.etQuantity)
            etRate = findViewById(R.id.etRate)
            etAmount = findViewById(R.id.etAmount)
            rgPaymentMode = findViewById(R.id.rgPaymentMode)
            rbCash = findViewById(R.id.rbCash)
            rbBank = findViewById(R.id.rbBank)
            rbCredit = findViewById(R.id.rbCredit)
            tvSummary = findViewById(R.id.tvSummary)
            lvItems = findViewById(R.id.lvItems)

            val btnSave: Button = findViewById(R.id.btnSave)
            val btnExport: Button = findViewById(R.id.btnExport)
            val btnPage2: Button = findViewById(R.id.btnGoCreditReceived)
            val btnPage3: Button = findViewById(R.id.btnGoClosingSheet)

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            etDate.setText(sdf.format(Date()))
            etDate.setOnClickListener { showDatePicker() }

            etName.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val name = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                    if (name == "cash" || name.startsWith("cash ")) {
                        if (rbCredit.isChecked) rbCash.isChecked = true
                    } else {
                        rbCredit.isChecked = true
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            val calcWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val qty = etQuantity.text.toString().toDoubleOrNull() ?: 0.0
                    val rate = etRate.text.toString().toDoubleOrNull() ?: 0.0
                    if (qty > 0 && rate > 0) {
                        val amt = qty * rate
                        etAmount.setText(if (amt % 1.0 == 0.0) amt.toInt().toString() else String.format(Locale.US, "%.2f", amt))
                    } else {
                        etAmount.setText("")
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            etQuantity.addTextChangedListener(calcWatcher)
            etRate.addTextChangedListener(calcWatcher)

            btnSave.setOnClickListener { saveEntry() }
            btnExport.setOnClickListener { exportToCsv() }
            btnPage2.setOnClickListener { startActivity(Intent(this, CreditReceivedActivity::class.java)) }
            btnPage3.setOnClickListener { startActivity(Intent(this, ClosingSheetActivity::class.java)) }

            lvItems.setOnItemLongClickListener { _, _, position, _ ->
                val selected = itemList[position]
                AlertDialog.Builder(this)
                    .setTitle("Delete Entry")
                    .setMessage("Delete ${selected.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteItem(selected.id)
                        loadItems()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            loadItems()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            etDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveEntry() {
        val date = etDate.text.toString().trim()
        val name = etName.text.toString().trim()
        val qty = etQuantity.text.toString().toDoubleOrNull()
        val rate = etRate.text.toString().toDoubleOrNull()
        val amount = etAmount.text.toString().toDoubleOrNull()
        val mode = when (rgPaymentMode.checkedRadioButtonId) {
            R.id.rbCash -> "Cash"
            R.id.rbBank -> "Bank"
            else -> "Credit"
        }
        if (date.isEmpty() || name.isEmpty() || qty == null || rate == null || amount == null) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        val item = LedgerItem(date = date, name = name, quantity = qty, rate = rate, amount = amount, paymentMode = mode)
        if (dbHelper.insertItem(item) != -1L) {
            Toast.makeText(this, "Saved ($mode)", Toast.LENGTH_SHORT).show()
            etName.text.clear()
            etQuantity.text.clear()
            etRate.text.clear()
            etAmount.text.clear()
            rbCredit.isChecked = true
            etName.requestFocus()
            loadItems()
        }
    }

    private fun loadItems() {
        itemList = dbHelper.getAllItems()
        val rows = itemList.map { "${it.date} | ${it.name} [${it.paymentMode}]\nQty: ${it.quantity} x ${it.rate} = ${it.amount}" }
        lvItems.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        val total = itemList.sumOf { it.amount }
        val cash = itemList.filter { it.paymentMode == "Cash" }.sumOf { it.amount }
        val bank = itemList.filter { it.paymentMode == "Bank" }.sumOf { it.amount }
        val credit = itemList.filter { it.paymentMode == "Credit" }.sumOf { it.amount }
        tvSummary.text = "Total: $total | Cash: $cash | Bank: $bank | Credit: $credit"
    }

    private fun exportToCsv() {
        if (itemList.isEmpty()) {
            Toast.makeText(this, "No records", Toast.LENGTH_SHORT).show()
            return
        }
        val csvFile = File(cacheDir, "additems_export.csv")
        csvFile.writeText("\uFEFFID,Date,Name,Quantity,Rate,Amount,Payment_Mode\n", Charsets.UTF_8)
        for (i in itemList) {
            csvFile.appendText("${i.id},${i.date},\"${i.name}\",${i.quantity},${i.rate},${i.amount},${i.paymentMode}\n", Charsets.UTF_8)
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
