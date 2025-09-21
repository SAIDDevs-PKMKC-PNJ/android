package com.pkm.said

import android.content.Intent
import android.os.Bundle
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

class UserInformationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UserInformationActivity"
    }

    private lateinit var binding: ActivityUserInformationBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

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
                    binding.etAge.setText(doc.getString("age") ?: "")
                    binding.etPhone.setText(doc.getString("phone") ?: "")
                    binding.etAddress.setText(doc.getString("address") ?: "")
                    binding.etEmergency.setText(doc.getString("emergency") ?: "")
                } else {
                    Log.d(TAG, "Dokumen belum ada, gunakan field kosong / intent")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal mengambil data existing", e)
            }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveData()
        }
    }

    private fun saveData() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        val name = binding.etName.textValue()
        val age = binding.etAge.textValue()
        val phone = binding.etPhone.textValue()
        val address = binding.etAddress.textValue()
        val emergency = binding.etEmergency.textValue()

        if (name.isBlank() || age.isBlank() || phone.isBlank() || address.isBlank() || emergency.isBlank()) {
            Toast.makeText(this, "Lengkapi semua field", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

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
                "age" to age,
                "phone" to phone,
                "address" to address,
                "emergency" to emergency,
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
                            SessionManager.saveFullProfile(
                                this,
                                name,
                                intentEmail ?: user.email,
                                intentPhotoUrl ?: user.photoUrl?.toString(),
                                age,
                                phone,
                                address,
                                emergency,
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
