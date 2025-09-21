package com.pkm.said

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.pkm.said.databinding.FragmentProfileFormBinding

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
            binding.etAge.setText("")
            binding.etAddress.setText("")
            binding.etPhone.setText("")
            return
        }

        binding.tvProfileName.text = user.displayName ?: "User Tanpa Nama"
        binding.etName.setText(user.displayName.orEmpty())

        val authPhotoUrl = user.photoUrl?.toString()
        if (!authPhotoUrl.isNullOrBlank()) {
            showAvatar(authPhotoUrl)
        } else {
            showAvatar(null)
        }

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val dbPhotoUrl = doc.getString("photoUrl")
                val finalPhotoUrl = authPhotoUrl ?: dbPhotoUrl
                showAvatar(finalPhotoUrl)

                val nameFromDb = doc.getString("name")
                if (!nameFromDb.isNullOrBlank()) {
                    binding.tvProfileName.text = nameFromDb
                    binding.etName.setText(nameFromDb)
                }

                binding.etAge.setText(doc.getString("age").orEmpty())
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


    private fun setupClick() {
        Log.d(TAG, "setupClick: Men-setup click listener")
        binding.btnSave.setOnClickListener {
            Log.d(TAG, "btnSave clicked")
            saveProfileData()
        }
        binding.btnEdit.setOnClickListener {
            Log.d(TAG, "btnEdit clicked")
            setEditable(true)
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
        binding.etAge.isEnabled = editable
        binding.etAddress.isEnabled = editable
        binding.etPhone.isEnabled = editable
        if (editable) binding.etName.requestFocus()
    }

    private fun pickImageFromGallery() {
        Log.d(TAG, "pickImageFromGallery: Memulai intent pick image")
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

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
        val user = firebaseUser
        if (user == null) {
            Log.w(TAG, "saveProfileData: firebaseUser null")
            Toast.makeText(context, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }
        val newName = binding.etName.text.toString().trim()
        val age = binding.etAge.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        Log.d(TAG, "saveProfileData: Simpan data - newName=$newName, age=$age, address=$address, phone=$phone, selectedPhotoUri=$selectedPhotoUri")

        // Update displayName dan photo ke Auth
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .apply {
                if (selectedPhotoUri != null) {
                    photoUri = selectedPhotoUri
                    Log.d(TAG, "saveProfileData: Akan update photoUri di Auth")
                }
            }
            .build()

        user.updateProfile(profileUpdates)
            .addOnSuccessListener {
                Log.d(TAG, "saveProfileData: updateProfile ke Auth sukses")
                // Update info lain ke Firestore
                val userMap = mapOf(
                    "age" to age,
                    "address" to address,
                    "phone" to phone
                )
                firestore.collection("users").document(user.uid)
                    .set(userMap)
                    .addOnSuccessListener {
                        Log.d(TAG, "saveProfileData: update Firestore sukses")
                        Toast.makeText(context, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        setEditable(false)
                        setupUserData()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "saveProfileData: Gagal simpan data ke Firestore", e)
                        Toast.makeText(context, "Gagal simpan data ke database", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "saveProfileData: Gagal update profile Auth", e)
                Toast.makeText(context, "Gagal update profil", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called, binding nullified")
        _binding = null
    }
}