package com.pkm.said

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pkm.said.databinding.ActivityUserInformationBinding
import com.pkm.said.util.SessionManager
import java.util.Calendar

class UserInformationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UserInformationActivity"
    }

    private lateinit var binding: ActivityUserInformationBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var isFormattingPhone = false

    // Intent extras
    private var intentEmail: String? = null
    private var intentName: String? = null
    private var intentPhotoUrl: String? = null
    private var intentLoginMethod: String? = null
    private var intentEmailVerified: Boolean = false
    private var fromRegistration: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserInformationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        readIntentExtras()
        prefillFields()
        setupListeners()
        setupPhoneNumberInput()
        setupEmergencyNumberInput()
        setupSaveButton() // ✅ DIPANGGIL DI SINI
    }

    private fun readIntentExtras() {
        intentEmail = intent.getStringExtra("user_email")
        intentName = intent.getStringExtra("user_name")
        intentPhotoUrl = intent.getStringExtra("user_photo_url")
        intentLoginMethod = intent.getStringExtra("login_method")
        intentEmailVerified = intent.getBooleanExtra("email_verified", false)
        fromRegistration = intent.getBooleanExtra("from_registration", false)

        Log.d(TAG, "Intent extras -> email=$intentEmail name=$intentName loginMethod=$intentLoginMethod verified=$intentEmailVerified fromRegistration=$fromRegistration")
    }

    private fun prefillFields() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Prefill nama (prioritas: intent > auth.displayName)
        binding.etName.setText(intentName ?: user.displayName ?: "")

        // Ambil data existing Firestore (jika user sudah pernah isi)
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Log.d(TAG, "Dokumen user sudah ada, prefill tambahan")

                    // Format ulang nomor telepon jika sudah ada
                    val existingPhone = doc.getString("phone") ?: ""
                    val existingEmergency = doc.getString("emergencyPhone") ?: ""

                    binding.etBirthdate.setText(doc.getString("birthdate") ?: "")
                    binding.etPhone.setText(formatPhoneForDisplay(existingPhone))
                    binding.etAddress.setText(doc.getString("address") ?: "")
                    binding.etEmergency.setText(formatPhoneForDisplay(existingEmergency))
                } else {
                    Log.d(TAG, "Dokumen belum ada, gunakan field kosong / intent")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal mengambil data existing", e)
            }
    }

    private fun setupListeners() {
        binding.etBirthdate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            saveData()
        }
    }

    private inner class PhoneNumberTextWatcher(
        private val editText: TextInputEditText
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (isFormattingPhone) return

            isFormattingPhone = true

            val originalString = s.toString()
            val digitsOnly = originalString.replace("\\D".toRegex(), "")

            // Logika validasi dan batasan angka 8-9 di awal
            val validatedDigits = if (digitsOnly.isNotEmpty()) {
                val firstChar = digitsOnly[0]
                if (firstChar == '8' || firstChar == '9') {
                    if (digitsOnly.length > 12) digitsOnly.substring(0, 12) else digitsOnly
                } else {
                    if (digitsOnly.length > 1) digitsOnly.substring(1) else ""
                }
            } else {
                ""
            }

            // Format: 8123-4567-89
            val formatted = StringBuilder()
            for (i in validatedDigits.indices) {
                if (i == 4 || i == 8) {
                    formatted.append("-")
                }
                formatted.append(validatedDigits[i])
            }

            val finalString = formatted.toString()

            if (originalString != finalString) {
                s?.replace(0, s.length, finalString)
                editText.setSelection(finalString.length)
            }

            isFormattingPhone = false

            // Clear error saat mengetik
            editText.error = null
        }
    }


    private fun setupPhoneNumberInput() {
        // Nonaktifkan kode negara utama
        binding.etCountryCode?.let { etCountryCode ->
            etCountryCode.isEnabled = false
            etCountryCode.isFocusable = false
            etCountryCode.isClickable = false
        }

        binding.etPhone.addTextChangedListener(PhoneNumberTextWatcher(binding.etPhone))

        // Focus listener untuk validasi
        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validatePhoneNumber()
            }
        }
    }

    private fun setupEmergencyNumberInput() {
        binding.etCountryCodeEmergency?.let { etCountryCodeEmergency ->
            etCountryCodeEmergency.isEnabled = false
            etCountryCodeEmergency.isFocusable = false
            etCountryCodeEmergency.isClickable = false
        }

        binding.etEmergency.addTextChangedListener(PhoneNumberTextWatcher(binding.etEmergency))

        // Focus listener untuk validasi darurat
        binding.etEmergency.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateEmergencyNumber()
            }
        }
    }

    private fun validatePhoneInput(editText: TextInputEditText, fieldName: String): Boolean {
        val phoneText = editText.textValue().replace("\\D".toRegex(), "")

        return when {
            phoneText.isEmpty() -> {
                editText.error = "$fieldName tidak boleh kosong"
                false
            }
            phoneText.length < 9 -> {
                editText.error = "$fieldName minimal 10 digit"
                false
            }
            phoneText.length > 11 -> {
                editText.error = "$fieldName maksimal 12 digit"
                false
            }
            phoneText.firstOrNull() != '8' && phoneText.firstOrNull() != '9' -> { // Menggunakan firstOrNull() untuk menghindari crash jika empty
                editText.error = "$fieldName harus dimulai dengan 8 atau 9"
                false
            }
            else -> {
                editText.error = null
                true
            }
        }
    }

    private fun validatePhoneNumber(): Boolean {
        return validatePhoneInput(binding.etPhone, "Nomor telepon")
    }

    private fun validateEmergencyNumber(): Boolean {
        return validatePhoneInput(binding.etEmergency, "Nomor darurat")
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            // ✅ FORMAT YANG LEBIH USER FRIENDLY
            val months = arrayOf(
                "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"
            )
            val dateString = "$selectedDay ${months[selectedMonth]} $selectedYear"
            binding.etBirthdate.setText(dateString)

            Log.d(TAG, "Tanggal lahir dipilih: $dateString")
        }, year, month, day)

        datePicker.datePicker.maxDate = calendar.timeInMillis
        datePicker.show()
    }

    private fun saveData() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        val name = binding.etName.textValue()
        val birthdate = binding.etBirthdate.textValue()
        val phone = binding.etPhone.textValue()
        val emergency = binding.etEmergency.textValue()
        val address = binding.etAddress.textValue()

        // Validasi semua field
        if (name.isBlank() || birthdate.isBlank() || phone.isBlank() || address.isBlank()) {
            Toast.makeText(this, "Lengkapi semua field", Toast.LENGTH_SHORT).show()
            return
        }

        // Validasi nomor telepon
        if (!validatePhoneNumber()) {
            Toast.makeText(this, "Periksa kembali nomor telepon", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        val formattedPhone = formatPhoneForFirebase(phone)
        val formattedEmergency = formatPhoneForFirebase(emergency)

        Log.d(TAG, "Phone formatted: $phone -> $formattedPhone")
//        Log.d(TAG, "Emergency formatted: $emergency -> $formattedEmergency")

        // 1. Update displayName Auth jika berubah
        val needUpdateAuthName = (user.displayName ?: "") != name
        val updateAuthTask = if (needUpdateAuthName) {
            Log.d(TAG, "Update profile Auth displayName=$name")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
        } else {
            Log.d(TAG, "DisplayName sama, skip update Auth")
            null
        }

        // Setelah (atau jika tidak perlu) -> simpan Firestore
        val proceedFirestore: () -> Unit = {
            val dataMap = hashMapOf(
                "uid" to user.uid,
                "name" to name,
                "email" to (intentEmail ?: user.email),
                "photoUrl" to (intentPhotoUrl ?: user.photoUrl?.toString()),
                "loginMethod" to intentLoginMethod,
                "emailVerified" to intentEmailVerified,
                "birthdate" to birthdate,
                "phone" to formattedPhone,
                "address" to address, // Kunci Firestore "address"
                "emergencyPhone" to formattedEmergency, // Kunci Firestore "emergencyPhone"
                "updatedAt" to FieldValue.serverTimestamp()
            )

// Jika dokumen baru: tambahkan createdAt via merge set
            val docRef = firestore.collection("users").document(user.uid)
            docRef.get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        dataMap["createdAt"] = FieldValue.serverTimestamp()
                    }
                    docRef.set(dataMap, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "Data user tersimpan/termerge")
                            Toast.makeText(this, "Data berhasil disimpan", Toast.LENGTH_SHORT).show()

                            // ✅ PERBAIKAN: URUTAN ARGUMEN SUDAH SESUAI DEFINISI saveFullProfile
                            // (birthdate, phone, emergencyPhone, address, loginMethod, emailVerified)
                            SessionManager.saveFullProfile(
                                this,
                                user.uid,
                                name,
                                intentEmail ?: user.email,
                                intentPhotoUrl ?: user.photoUrl?.toString(),
                                birthdate,        // 6. birthdate
                                formattedPhone,   // 7. phone
                                formattedEmergency, // 8. emergencyPhone (BENAR)
                                address,          // 9. address (BENAR)
                                intentLoginMethod,
                                intentEmailVerified
                            )
                            navigateToMain(name)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Gagal simpan Firestore", e)
                            setLoading(false)
                            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Gagal cek existing doc", e)
                    setLoading(false)
                }
        }

        if (updateAuthTask != null) {
            updateAuthTask
                .addOnSuccessListener {
                    Log.d(TAG, "Update Auth sukses")
                    proceedFirestore()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Gagal update Auth", e)
                    setLoading(false)
                    Toast.makeText(this, "Gagal update nama", Toast.LENGTH_SHORT).show()
                }
        } else {
            proceedFirestore()
        }
    }

    private fun formatPhoneForFirebase(phone: String): String {
        val digitsOnly = phone.replace("\\D".toRegex(), "")
        return "+62$digitsOnly"
    }

    private fun formatPhoneForDisplay(phone: String): String {
        // Jika sudah format +62, konversi ke format display
        return if (phone.startsWith("+62")) {
            val digitsOnly = phone.substring(3) // Hapus +62
            val formatted = StringBuilder()
            for (i in digitsOnly.indices) {
                if (i == 4 || i == 8) {
                    formatted.append("-")
                }
                formatted.append(digitsOnly[i])
            }
            formatted.toString()
        } else {
            phone // Return as-is jika bukan format +62
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSave.isEnabled = !loading
        binding.btnSave.text = if (loading) "Menyimpan..." else "Simpan"
    }

    private fun navigateToMain(name: String) {
        Log.d(TAG, "Navigasi ke MainActivity")
        val dialog = WelcomeDialog(this, name){
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }
        dialog.show()
    }

    private fun TextInputEditText.textValue(): String = this.text?.toString()?.trim().orEmpty()
}