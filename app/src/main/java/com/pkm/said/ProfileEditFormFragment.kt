package com.pkm.said

import android.app.Activity
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

                binding.etBirthdate.setText(doc.getString("birthdate").orEmpty())
                binding.etAddress.setText(doc.getString("address").orEmpty())
                binding.etPhone.setText(doc.getString("phone").orEmpty())

                Log.d(TAG, "Firestore loaded: age=${doc.getString("age")}, address=${doc.getString("address")}, phone=${doc.getString("phone")}")
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

    suspend fun uploadUriToCloudinaryUnsigned(
        uri: Uri,
        cloudName: String,
        uploadPreset: String,
        folder: String
    ): CloudinaryUploadResp? = withContext(Dispatchers.IO) {
        try {
            // Ambil mime type dari file
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

    private fun setupClick() {
        Log.d(TAG, "setupClick: Men-setup click listener")
        binding.btnEditSave.setOnClickListener {
            if (!isEditing) {
                // Masuk ke mode edit
                Log.d(TAG, "btnEditSave: masuk mode edit")
                isEditing = true
                setEditable(true)
                binding.btnEditSave.text = "Simpan"
            } else {
                // Mode simpan
                Log.d(TAG, "btnEditSave: menyimpan perubahan")
                saveProfileData()
            }
        }
        binding.etBirthdate.setOnClickListener {
            if (!isEditing) return@setOnClickListener   // locked → Abaikan
            showDatePicker()
        }
        binding.btnBack.setOnClickListener {
            Log.d(TAG, "btnBack clicked")
            findNavController().navigateUp()
        }
        binding.avatarClickCover.setOnClickListener {
            Log.d(TAG, "ivProfile clicked, membuka galeri")
            pickImageFromGallery()
        }
    }

    private fun setEditable(editable: Boolean) {
        Log.d(TAG, "setEditable: $editable")
        binding.etName.isEnabled = editable
        binding.etBirthdate.isEnabled = editable
        binding.etAddress.isEnabled = editable
        binding.etPhone.isEnabled = editable
        if (editable) binding.etName.requestFocus()
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

        DatePickerDialog(requireContext(), { _, y, m, d ->
            val dateString = "%02d/%02d/%04d".format(d, m + 1, y)
            binding.etBirthdate.setText(dateString)
        }, year, month, day).show()
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
                    .error(R.drawable.ic_person)
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

        val newName = binding.etName.text.toString().trim()
        val birthdate = binding.etBirthdate.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        // tampilkan loading spinner di sini kalau ada

        if (selectedPhotoUri != null) {
            // kalau ada foto baru → upload dulu
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
                        .apply { if (finalUrl != null) photoUri = Uri.parse(finalUrl) }
                        .build()

                    user.updateProfile(profileUpdates).addOnSuccessListener {
                        val userMap = mapOf(
                            "name" to newName,
                            "birthdate" to birthdate,
                            "address" to address,
                            "phone" to phone,
                            "photoUrl" to (finalUrl ?: ""),
                            "cloudinaryPublicId" to (publicId ?: ""),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users").document(user.uid)
                            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(context, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                setEditable(false)
                                setupUserData()
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Upload foto gagal", e)
                    Toast.makeText(context, "Gagal upload foto", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // kalau hanya ubah data text → langsung update
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()

            user.updateProfile(profileUpdates).addOnSuccessListener {
                val userMap = mapOf(
                    "name" to newName,
                    "birthdate" to birthdate,
                    "address" to address,
                    "phone" to phone,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("users").document(user.uid)
                    .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(context, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        setEditable(false)
                        setupUserData()
                    }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called, binding nullified")
        _binding = null
    }
}