package com.example.dailyledgerapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val customerList = mutableListOf<String>()
    private lateinit var customerAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            // Tab Layouts & Buttons
            val btnTabLedger = findViewById<Button>(R.id.btnTabLedger)
            val btnTabCustomer = findViewById<Button>(R.id.btnTabCustomer)
            val layoutLedgerEntry = findViewById<LinearLayout>(R.id.layoutLedgerEntry)
            val layoutCustomerCreation = findViewById<LinearLayout>(R.id.layoutCustomerCreation)

            // Ledger Entry Views (Strictly matched with XML types)
            val etDate = findViewById<EditText>(R.id.etDate)
            val actvCustomerName = findViewById<AutoCompleteTextView>(R.id.actvCustomerName)
            val etQty = findViewById<EditText>(R.id.etQty)
            val etRate = findViewById<EditText>(R.id.etRate)
            val etAmount = findViewById<EditText>(R.id.etAmount)
            val spPaymentMode = findViewById<Spinner>(R.id.spPaymentMode)
            val btnSaveEntry = findViewById<Button>(R.id.btnSaveEntry)

            // Customer Creation Views
            val etCustName = findViewById<EditText>(R.id.etCustName)
            val etAddress1 = findViewById<EditText>(R.id.etAddress1)
            val etAddress2 = findViewById<EditText>(R.id.etAddress2)
            val etArea = findViewById<EditText>(R.id.etArea)
            val etPincode = findViewById<EditText>(R.id.etPincode)
            val etContact = findViewById<EditText>(R.id.etContact)
            val etGroup = findViewById<EditText>(R.id.etGroup)
            val etOpBal = findViewById<EditText>(R.id.etOpBal)
            val btnSaveCustomer = findViewById<Button>(R.id.btnSaveCustomer)

            // Set Default Today's Date
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            etDate.setText(today)

            // Setup Payment Modes Spinner
            val paymentModes = arrayOf("Cash", "Credit", "GPay / UPI", "Bank Transfer")
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, paymentModes)
            spPaymentMode.adapter = spinnerAdapter

            // Setup Customer Name Combo Box (AutoCompleteTextView)
            customerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerList)
            actvCustomerName.setAdapter(customerAdapter)
            actvCustomerName.setOnClickListener {
                actvCustomerName.showDropDown()
            }

            // Auto-Calculate Amount (Qty * Rate)
            val calcWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val qty = etQty.text.toString().toDoubleOrNull() ?: 0.0
                    val rate = etRate.text.toString().toDoubleOrNull() ?: 0.0
                    if (qty > 0 && rate > 0) {
                        etAmount.setText(String.format(Locale.getDefault(), "%.2f", qty * rate))
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            etQty.addTextChangedListener(calcWatcher)
            etRate.addTextChangedListener(calcWatcher)

            // Switch Tabs
            btnTabLedger.setOnClickListener {
                layoutLedgerEntry.visibility = View.VISIBLE
                layoutCustomerCreation.visibility = View.GONE
            }

            btnTabCustomer.setOnClickListener {
                layoutLedgerEntry.visibility = View.GONE
                layoutCustomerCreation.visibility = View.VISIBLE
            }

            // Save Customer Action
            btnSaveCustomer.setOnClickListener {
                val name = etCustName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter Customer Name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!customerList.contains(name)) {
                    customerList.add(name)
                    customerAdapter.notifyDataSetChanged()
                }

                Toast.makeText(this, "Customer '$name' Saved!", Toast.LENGTH_SHORT).show()

                // Clear Customer Fields
                etCustName.text.clear()
                etAddress1.text.clear()
                etAddress2.text.clear()
                etArea.text.clear()
                etPincode.text.clear()
                etContact.text.clear()
                etGroup.text.clear()
                etOpBal.text.clear()

                // Switch back to Ledger Entry
                layoutCustomerCreation.visibility = View.GONE
                layoutLedgerEntry.visibility = View.VISIBLE
                actvCustomerName.setText(name)
            }

            // Save Ledger Entry Action
            btnSaveEntry.setOnClickListener {
                val custName = actvCustomerName.text.toString().trim()
                val amount = etAmount.text.toString().trim()
                val mode = spPaymentMode.selectedItem.toString()

                if (custName.isEmpty() || amount.isEmpty()) {
                    Toast.makeText(this, "Enter Customer Name and Amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Toast.makeText(this, "Saved: $custName - ₹$amount ($mode)", Toast.LENGTH_SHORT).show()

                // Clear Entry Fields
                actvCustomerName.text.clear()
                etQty.text.clear()
                etRate.text.clear()
                etAmount.text.clear()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
