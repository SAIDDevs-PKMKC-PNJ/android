package com.pkm.said

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.pkm.said.databinding.FragmentProfileFormBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileFormFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFormFragment"
        private const val PICK_IMAGE_REQUEST = 1001
        private val PHONE_REGEX_LOCAL = Regex("^[89]\\d{9,11}$")
    }

    private var _binding: FragmentProfileFormBinding? = null
    private val binding get() = _binding!!
    private val firebaseUser get() = FirebaseAuth.getInstance().currentUser
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var selectedPhotoUri: Uri? = null
    private var isEditing = false
    private val http by lazy { OkHttpClient() }
    private val moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    private val cldAdapter by lazy { moshi.adapter(CloudinaryUploadResp::class.java) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentProfileFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")
        setupUserData()
        setupClick()
        setupInitialState() // ✅ INITIAL STATE: NON-EDITABLE
    }

    // ✅ SETUP INITIAL STATE - FIELD TIDAK BISA DIEDIT
    private fun setupInitialState() {
        setEditable(false)
        binding.btnEditSave.text = "Edit Profil"
        isEditing = false
    }

    private fun setupUserData() {
        val user = firebaseUser ?: run {
            Log.w(TAG, "setupUserData: firebaseUser null")
            binding.tvProfileName.text = "User Tanpa Nama"
            showAvatar(null)
            binding.etName.setText("")
            binding.etBirthdate.setText("")
            binding.etAddress.setText("")
            binding.etPhone.setText("")
            binding.etEmergency.setText("")
            return
        }

        binding.tvProfileName.text = user.displayName ?: "User Tanpa Nama"
        binding.etName.setText(user.displayName.orEmpty())

        val authPhotoUrl = user.photoUrl?.toString()

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val dbPhotoUrl = doc.getString("photoUrl")
                val finalPhotoUrl = if (!dbPhotoUrl.isNullOrBlank()) dbPhotoUrl else authPhotoUrl
                showAvatar(finalPhotoUrl)

                val nameFromDb = doc.getString("name")
                if (!nameFromDb.isNullOrBlank()) {
                    binding.tvProfileName.text = nameFromDb
                    binding.etName.setText(nameFromDb)
                }

                val dbPhone = doc.getString("phone").orEmpty()
                val dbEmergency = doc.getString("emergencyPhone").orEmpty()

                binding.etBirthdate.setText(doc.getString("birthdate").orEmpty())
                binding.etAddress.setText(doc.getString("address").orEmpty())
                binding.etPhone.setText(removePlus62ForEdit(dbPhone))
                binding.etEmergency.setText(removePlus62ForEdit(dbEmergency))

                if (!isEditing) {
                    binding.etPhone.setText(formatPhoneForDisplay(dbPhone))
                    binding.etEmergency.setText(formatPhoneForDisplay(dbEmergency))
                }

                Log.d(TAG, "Firestore loaded: birthdate=${doc.getString("birthdate")}, address=${doc.getString("address")}, phone=${doc.getString("phone")}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore get failed", e)
                showAvatar(authPhotoUrl)
                binding.etName.setText(user.displayName.orEmpty())
            }
    }

    private fun showAvatar(url: String?) {
        if (url.isNullOrBlank()) {
            Glide.with(this)
                .load(R.drawable.ic_avatar_default)
                .into(binding.ivProfile)
        } else {
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_avatar_default)
                .error(R.drawable.ic_avatar_default)
                .into(binding.ivProfile)
        }
    }

    private fun setupClick() {
        Log.d(TAG, "setupClick: Men-setup click listener")

        binding.btnEditSave.setOnClickListener {
            if (!isEditing) {
                // ✅ MASUK KE MODE EDIT
                enterEditMode()
            } else {
                // ✅ MODE SIMPAN - VALIDASI DULU
                if (validateInputs()) {
                    saveProfileData()
                }
            }
        }

        binding.etName.setOnClickListener {
            if (!isEditing) {
                Toast.makeText(requireContext(), "Tekan 'Edit Profil' untuk mengubah data", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etAddress.setOnClickListener {
            if (!isEditing) {
                Toast.makeText(requireContext(), "Tekan 'Edit Profil' untuk mengubah data", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etPhone.setOnClickListener {
            if (!isEditing) {
                Toast.makeText(requireContext(), "Tekan 'Edit Profil' untuk mengubah data", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etBirthdate.setOnClickListener {
            if (!isEditing) {
                Toast.makeText(requireContext(), "Tekan 'Edit Profil' untuk mengubah data", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDatePicker()
        }

        binding.etEmergency.setOnClickListener {
            if (!isEditing) {
                Toast.makeText(requireContext(), "Tekan 'Edit Profil' untuk mengubah data", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBack.setOnClickListener {
            if (isEditing) {
                // ✅ JIKA SEDANG EDIT, TANYA KONFIRMASI BATAL
                showCancelConfirmationDialog()
            } else {
                findNavController().navigateUp()
            }
        }

        binding.avatarClickCover.setOnClickListener {
            if (!isEditing) {
                Toast.makeText(requireContext(), "Tekan 'Edit Profil' untuk mengubah foto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "ivProfile clicked, membuka galeri")
            pickImageFromGallery()
        }
    }

    // ✅ MASUK KE MODE EDIT
    private fun enterEditMode() {
        Log.d(TAG, "btnEditSave: masuk mode edit")
        isEditing = true
        setEditable(true)
        binding.btnEditSave.text = "Simpan Perubahan"
        binding.btnEditSave.isEnabled = true
        binding.btnEditSave.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_color))
    }

    // ✅ KELUAR DARI MODE EDIT (SETELAH SIMPAN/BATAL)
    private fun exitEditMode() {
        Log.d(TAG, "Keluar dari mode edit")
        isEditing = false
        setEditable(false)
        binding.btnEditSave.text = "Edit Profil"
        binding.btnEditSave.isEnabled = true
        binding.btnEditSave.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lightBlue))

        // ✅ RESET SELECTED PHOTO JIKA ADA
        selectedPhotoUri = null
    }

    private fun setEditable(editable: Boolean) {
        Log.d(TAG, "setEditable: $editable")
        binding.etName.isEnabled = editable
        binding.etBirthdate.isEnabled = editable
        binding.etAddress.isEnabled = editable
        binding.etPhone.isEnabled = editable
        binding.etEmergency.isEnabled = editable

        val currentPhone = binding.etPhone.text.toString()
        val currentEmergency = binding.etEmergency.text.toString()

        if (editable) {
            binding.etPhone.setText(removePlus62ForEdit(currentPhone))
            binding.etEmergency.setText(removePlus62ForEdit(currentEmergency))

        } else {
            binding.etPhone.setText(formatPhoneForDisplay(currentPhone))
            binding.etEmergency.setText(formatPhoneForDisplay(currentEmergency))
        }

        // ✅ VISUAL FEEDBACK (Kode sama)
        val alpha = if (editable) 1.0f else 0.7f
        binding.etName.alpha = alpha
        binding.etBirthdate.alpha = alpha
        binding.etAddress.alpha = alpha
        binding.etPhone.alpha = alpha
        binding.etEmergency.alpha = alpha

        if (editable) binding.etName.requestFocus()
    }

    private fun removePlus62ForEdit(phone: String): String {
        val digitsOnly = phone.replace("\\D".toRegex(), "")
        return when {
            // Jika diawali 62, ambil sisanya (misal: +62812 -> 812)
            digitsOnly.startsWith("62") -> digitsOnly.substring(2)
            else -> digitsOnly
        }
    }

    // ✅ VALIDASI INPUT SEBELUM SIMPAN
    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val birthdate = binding.etBirthdate.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val phone = cleanPhoneNumber(binding.etPhone.text.toString())
        val emergency = cleanPhoneNumber(binding.etEmergency.text.toString())

        if (name.isEmpty()) {
            binding.etName.error = "Nama harus diisi"
            binding.etName.requestFocus()
            return false
        }

        if (birthdate.isEmpty()) {
            binding.etBirthdate.error = "Tanggal lahir harus diisi"
            binding.etBirthdate.requestFocus()
            return false
        }

        if (address.isEmpty()) {
            binding.etAddress.error = "Alamat harus diisi"
            binding.etAddress.requestFocus()
            return false
        }

        if (phone.isEmpty()) {
            binding.etPhone.error = "Nomor telepon harus diisi"
            binding.etPhone.requestFocus()
            return false
        }

        if (emergency.isEmpty()) {
            binding.etEmergency.error = "Nomor telepon darurat harus diisi"
            binding.etEmergency.requestFocus()
            return false
        }

        val validationMessage = "Nomor telepon tidak valid (9-11 digit lokal, tanpa 0 di depan)"
        if (!validatePhoneFormat(phone)) return showValidationError(binding.etPhone, validationMessage)
        if (!validatePhoneFormat(emergency)) return showValidationError(binding.etEmergency, validationMessage)

        // 3. Cek Duplikasi
        if (phone == emergency) {
            Toast.makeText(context, "Nomor telepon dan darurat tidak boleh sama.", Toast.LENGTH_LONG).show()
            return false
        }

        clearValidationErrors()
        return true
    }

    private fun clearValidationErrors() {
        binding.etName.error = null
        binding.etBirthdate.error = null
        binding.etAddress.error = null
        binding.etPhone.error = null
        binding.etEmergency.error = null
    }

    private fun showValidationError(view: View, message: String): Boolean {
        if (view is android.widget.EditText) {
            view.error = message
            view.requestFocus()
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        return false
    }

    private fun validatePhoneFormat(cleanedPhone: String): Boolean {
        val isLengthValid = cleanedPhone.length in 9..11

        // Cek format: harus dimulai dengan 8 atau 9 (karena '0' sudah dihapus di cleanPhoneNumber)
        val isFormatValid = cleanedPhone.startsWith("8") || cleanedPhone.startsWith("9")

        // ✅ Perbaikan logika: Pastikan kedua kondisi terpenuhi
        return isLengthValid && isFormatValid
    }

    private fun cleanPhoneNumber(input: String): String {
        val digitsOnly = input.replace("\\D".toRegex(), "")

        val withoutCountryCode = if (digitsOnly.startsWith("62")) {
            digitsOnly.substring(2)
        } else {
            digitsOnly
        }

        return when {
            withoutCountryCode.startsWith("0") -> withoutCountryCode.substring(1)
            else -> withoutCountryCode
        }
    }

    // ✅ DIALOG KONFIRMASI BATAL EDIT
    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Batalkan Perubahan?")
            .setMessage("Perubahan yang belum disimpan akan hilang.")
            .setPositiveButton("Ya, Batalkan") { dialog, _ ->
                dialog.dismiss()
                setupUserData()
                exitEditMode()
                Toast.makeText(requireContext(), "Perubahan dibatalkan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Lanjutkan Edit", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickImageFromGallery() {
        Log.d(TAG, "pickImageFromGallery: Memulai intent pick image")
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
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

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedPhotoUri = data?.data
            selectedPhotoUri?.let {
                Log.d(TAG, "onActivityResult: Image dipilih: $it")
                Glide.with(this)
                    .load(it)
                    .error(R.drawable.ic_avatar_default)
                    .circleCrop()
                    .into(binding.ivProfile)
            }
        }
    }

    private fun saveProfileData() {
        val user = firebaseUser ?: run {
            Toast.makeText(context, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ SET LOADING STATE
        setLoadingState(true)

        val newName = binding.etName.text.toString().trim()
        val birthdate = binding.etBirthdate.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val phone = cleanPhoneNumber(binding.etPhone.text.toString().trim())
        val emergency = cleanPhoneNumber(binding.etEmergency.text.toString())

        if (selectedPhotoUri != null) {
            // ✅ UPLOAD FOTO BARU JIKA ADA
            lifecycleScope.launch {
                try {
                    val resp = uploadUriToCloudinaryUnsigned(
                        uri = selectedPhotoUri!!,
                        cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
                        uploadPreset = BuildConfig.CLOUDINARY_UNSIGNED_PRESET,
                        folder = "profile_photos/${user.uid}"
                    )

                    val finalUrl = resp?.secure_url
                    val publicId = resp?.public_id

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .apply { if (finalUrl != null) setPhotoUri(Uri.parse(finalUrl)) }
                        .build()

                    user.updateProfile(profileUpdates).addOnSuccessListener {
                        saveToFirestore(user.uid, newName, birthdate, address, phone, emergency, finalUrl, publicId)
                    }.addOnFailureListener { e ->
                        setLoadingState(false)
                        Log.e(TAG, "Update profile failed", e)
                        Toast.makeText(context, "Gagal update profil: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    setLoadingState(false)
                    Log.e(TAG, "Upload foto gagal", e)
                    Toast.makeText(context, "Gagal upload foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // ✅ LANGSUNG UPDATE TANPA FOTO BARU
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()

            user.updateProfile(profileUpdates).addOnSuccessListener {
                saveToFirestore(user.uid, newName, birthdate, address, phone, emergency, null, null)
            }.addOnFailureListener { e ->
                setLoadingState(false)
                Log.e(TAG, "Update profile failed", e)
                Toast.makeText(context, "Gagal update profil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ FUNGSI SIMPAN KE FIRESTORE
    private fun saveToFirestore(
        userId: String,
        name: String,
        birthdate: String,
        address: String,
        phone: String,
        emergency: String,
        photoUrl: String?,
        publicId: String?
    ) {
        val formattedPhone = formatPhoneForFirebase(phone)
        val formattedEmergency = formatPhoneForFirebase(emergency)

        val userMap = mutableMapOf(
            "name" to name,
            "birthdate" to birthdate,
            "phone" to formattedPhone,
            "address" to address,
            "emergencyPhone" to formattedEmergency,
            "updatedAt" to System.currentTimeMillis()
        )

        // ✅ TAMBAHKAN PHOTO URL JIKA ADA
        photoUrl?.let { userMap["photoUrl"] = it }
        publicId?.let { userMap["cloudinaryPublicId"] = it }

        firestore.collection("users").document(userId)
            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                setLoadingState(false)
                Toast.makeText(context, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()

                // ✅ KELUAR DARI MODE EDIT DAN RELOAD DATA
                exitEditMode()
                setupUserData() // Reload untuk menampilkan data terbaru
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                Log.e(TAG, "Save to Firestore failed", e)
                Toast.makeText(context, "Gagal menyimpan data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun formatPhoneForFirebase(phone: String): String {
        val digitsOnly = phone.replace("\\D".toRegex(), "")
        if (digitsOnly.isEmpty()) return ""

        // Hapus '0' di depan jika ada, karena +62 sudah menggantikan '0'.
        val normalizedDigits = if (digitsOnly.startsWith("0")) digitsOnly.substring(1) else digitsOnly

        // Pastikan nomor yang sudah bersih tidak dimulai dengan 62 (sudah dihilangkan di cleanPhoneNumber)
        val finalDigits = if (normalizedDigits.startsWith("62")) normalizedDigits.substring(2) else normalizedDigits

        return "+62$finalDigits"
    }

    private fun formatPhoneForDisplay(phone: String): String {
        val digitsOnly = phone.replace("\\D".toRegex(), "")
        if (digitsOnly.startsWith("62")) {
            // Jika sudah ada 62 (dari Firestore), tambahkan '+'
            return "+$digitsOnly"
        }
        // Jika hanya digit lokal, tambahkan +62
        return "+62$digitsOnly"
    }


    // ✅ SET LOADING STATE
    private fun setLoadingState(loading: Boolean) {
        binding.btnEditSave.isEnabled = !loading
        binding.btnEditSave.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
        binding.btnBack.isEnabled = !loading

        // ✅ TAMPILKAN/SEMBUNYIKAN PROGRESS BAR JIKA ADA
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    // ✅ UPLOAD KE CLOUDINARY (FUNGSI YANG SUDAH ADA)
    suspend fun uploadUriToCloudinaryUnsigned(
        uri: Uri,
        cloudName: String,
        uploadPreset: String,
        folder: String
    ): CloudinaryUploadResp? = withContext(Dispatchers.IO) {
        try {
            val cr = requireContext().contentResolver
            val mime = cr.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"

            val inputStream = cr.openInputStream(uri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val fileBody = bytes.toRequestBody(mime.toMediaTypeOrNull())

            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "upload.$ext", fileBody)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("folder", folder)
                .build()

            val req = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(body)
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val txt = res.body?.string().orEmpty()
                cldAdapter.fromJson(txt)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called, binding nullified")
        _binding = null
    }
}