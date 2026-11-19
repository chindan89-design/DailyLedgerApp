package com.koyambedu.ledgerapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class CustomerCreationActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etCustName: EditText
    private lateinit var etAddress1: EditText
    private lateinit var etAddress2: EditText
    private lateinit var etArea: EditText
    private lateinit var etPincode: EditText
    private lateinit var etContact: EditText
    private lateinit var etGroup: EditText
    private lateinit var etOpBal: EditText
    private lateinit var tvCustSummary: TextView
    private lateinit var lvCustomers: ListView

    private var customerList = listOf<CustomerMaster>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_customer_creation)
            dbHelper = DatabaseHelper(this)

            etCustName = findViewById(R.id.etCustName)
            etAddress1 = findViewById(R.id.etAddress1)
            etAddress2 = findViewById(R.id.etAddress2)
            etArea = findViewById(R.id.etArea)
            etPincode = findViewById(R.id.etPincode)
            etContact = findViewById(R.id.etContact)
            etGroup = findViewById(R.id.etGroup)
            etOpBal = findViewById(R.id.etOpBal)
            tvCustSummary = findViewById(R.id.tvCustSummary)
            lvCustomers = findViewById(R.id.lvCustomers)

            val btnSave: Button = findViewById(R.id.btnSaveCustomer)
            val btnExport: Button = findViewById(R.id.btnExportCustomers)

            btnSave.setOnClickListener { saveCustomer() }
            btnExport.setOnClickListener { exportCustomersCsv() }

            lvCustomers.setOnItemLongClickListener { _, _, position, _ ->
                val selected = customerList[position]
                AlertDialog.Builder(this)
                    .setTitle("Delete Customer")
                    .setMessage("Delete ${selected.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteCustomerMaster(selected.name)
                        loadCustomers()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            loadCustomers()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveCustomer() {
        val name = etCustName.text.toString().trim()
        val addr1 = etAddress1.text.toString().trim()
        val addr2 = etAddress2.text.toString().trim()
        val area = etArea.text.toString().trim()
        val pincode = etPincode.text.toString().trim()
        val contact = etContact.text.toString().trim()
        val group = etGroup.text.toString().trim()
        val opBal = etOpBal.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            Toast.makeText(this, "Enter Customer Name", Toast.LENGTH_SHORT).show()
            return
        }

        val cust = CustomerMaster(name, addr1, addr2, area, pincode, contact, group, opBal)
        dbHelper.saveCustomerMaster(cust)
        Toast.makeText(this, "Customer Saved", Toast.LENGTH_SHORT).show()

        etCustName.text.clear()
        etAddress1.text.clear()
        etAddress2.text.clear()
        etArea.text.clear()
        etPincode.text.clear()
        etContact.text.clear()
        etGroup.text.clear()
        etOpBal.text.clear()
        etCustName.requestFocus()
        loadCustomers()
    }

    private fun loadCustomers() {
        customerList = dbHelper.getAllCustomerMasters()
        val rows = customerList.map {
            "${it.name} [${it.group.ifEmpty { "General" }}] | OpBal: ₹${it.opBal}\n${it.area} ${it.pincode} | Ph: ${it.contact}"
        }
        lvCustomers.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        val totalOp = customerList.sumOf { it.opBal }
        tvCustSummary.text = "Total Customers: ${customerList.size} | Total OpBal: ₹$totalOp"
    }

    private fun exportCustomersCsv() {
        if (customerList.isEmpty()) {
            Toast.makeText(this, "No customers to export", Toast.LENGTH_SHORT).show()
            return
        }
        val csvFile = File(cacheDir, "customer_master_export.csv")
        csvFile.writeText("\uFEFFName,Address1,Address2,Area,Pincode,Contact,Group,OpBal\n", Charsets.UTF_8)
        for (c in customerList) {
            csvFile.appendText(
                "\"${c.name}\",\"${c.address1}\",\"${c.address2}\",\"${c.area}\",\"${c.pincode}\",\"${c.contact}\",\"${c.group}\",${c.opBal}\n",
                Charsets.UTF_8
            )
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", csvFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export Customers CSV"))
    }
}
