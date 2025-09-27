package com.pkm.said

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.pkm.said.databinding.FragmentProfileBinding
import com.pkm.said.databinding.ItemProfileMenuBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
// import com.google.firebase.storage.FirebaseStorage
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import com.google.firebase.firestore.FirebaseFirestore
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.util.SessionManager

class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
        private const val PREF_NAME = "SaidAppPreferences"
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var firebaseAuth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        firebaseAuth = FirebaseAuth.getInstance()
        currentUser = firebaseAuth.currentUser
        sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        Log.d(TAG, "Firebase User: ${currentUser?.email}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        checkUserAuthentication()
        setupUserData()
        setupClickListeners()
    }

    private fun checkUserAuthentication() {
        if (currentUser == null) {
            Log.w(TAG, "User not authenticated, redirecting to login")
            showError("Please login to access profile")
            navigateToLoginScreen()
        } else {
            Log.d(TAG, "User authenticated: ${currentUser?.email}")
        }
    }

    @Suppress("DEPRECATION")
    private fun setupUserData() {
        try {
            Log.d(TAG, "Setting up user data...")
            val user = currentUser

            // Nama
            binding.tvProfileName.text = user?.displayName ?: "User Tanpa Nama"

            // Tanggal daftar
            val joinDate = user?.metadata?.creationTimestamp?.let {
                java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id"))
                    .format(java.util.Date(it))
            } ?: "-"
            binding.tvJoinDate.text = joinDate

            // Ambil foto dari Firestore dulu
            if (user != null) {
                FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
                    .addOnSuccessListener { doc ->
                        val dbPhotoUrl = doc.getString("photoUrl")
                        val finalUrl = if (!dbPhotoUrl.isNullOrBlank()) dbPhotoUrl else user.photoUrl?.toString()

                        if (!finalUrl.isNullOrBlank()) {
                            Glide.with(this)
                                .load(finalUrl)
                                .placeholder(R.drawable.ic_avatar_default)
                                .error(R.drawable.ic_avatar_default)
                                .circleCrop()
                                .into(binding.ivProfile)
                            Log.d(TAG, "Loaded photoUrl: $finalUrl")
                        } else {
                            binding.ivProfile.setImageResource(R.drawable.ic_avatar_default)
                            Log.d(TAG, "No photoUrl, using default icon")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Firestore get failed", e)
                        // fallback ke Auth photoUrl
                        val authUrl = user.photoUrl?.toString()
                        if (!authUrl.isNullOrBlank()) {
                            Glide.with(this)
                                .load(authUrl)
                                .into(binding.ivProfile)
                        } else {
                            binding.ivProfile.setImageResource(R.drawable.ic_avatar_default)
                        }
                    }
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_avatar_default)
            }

            // Menu
            setMenuItem(binding.menuInformasi, R.drawable.ic_user, "Informasi Pribadi")
            setMenuItem(binding.menuHistory, R.drawable.ic_location, "Riwayat Screening")
            setMenuItem(binding.menuSettings, R.drawable.ic_setting, "Settings")
            setMenuItem(binding.menuHapus, R.drawable.ic_delete, "Hapus Akun")

            Log.d(TAG, "✅ User data setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up user data", e)
        }
    }


    private fun setMenuItem(binding: ItemProfileMenuBinding, iconRes: Int, label: String) {
        try {
            binding.icon.setImageResource(iconRes)
            binding.menuTitle.text = label
        } catch (e: Exception) {
            Log.e(TAG, "Error setting menu item label/icon", e)
        }
    }

    private fun setupClickListeners() {
        Log.d(TAG, "Setting up click listeners...")

        try {
            binding.menuInformasi.root.setOnClickListener {
                Log.d(TAG, "Menu: Informasi Pribadi clicked")
                findNavController().navigate(R.id.action_profile_to_editProfile)
            }
            binding.menuHistory.root.setOnClickListener {
                Log.d(TAG, "Menu: Riwayat Screening clicked")
                findNavController().navigate(R.id.action_profile_to_history)
            }
            binding.menuSettings.root.setOnClickListener {
                Log.d(TAG, "Menu: Settings clicked")
                showToast("Fitur Settings belum tersedia")
            }
            binding.menuHapus.root.setOnClickListener {
                Log.d(TAG, "Menu: Donasi clicked")
                showDeleteAccountDialog()
            }
            binding.btnLogout.setOnClickListener {
                Log.d(TAG, "Logout button clicked")
                showLogoutConfirmationDialog()
            }
            Log.d(TAG, "✅ Click listeners setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up click listeners", e)
        }
    }

    private fun showLogoutConfirmationDialog() {
        try {
            val userEmail = currentUser?.email ?: "Unknown User"
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Yakin ingin logout dari aplikasi?\n\nAkun: $userEmail")
                .setIcon(R.drawable.ic_logout)
                .setPositiveButton("Logout") { dialog, _ ->
                    Log.d(TAG, "User confirmed Firebase logout")
                    dialog.dismiss()
                    performFirebaseLogout()
                }
                .setNegativeButton("Batal", null)
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing logout dialog", e)
            performFirebaseLogout()
        }
    }

    private fun performFirebaseLogout() {
        Log.d(TAG, "Performing Firebase logout...")
        if (!isAdded) return

        binding.btnLogout.isEnabled = false
        binding.btnLogout.text = getString(R.string.logging_out)

        try {
            val userEmail = currentUser?.email
            Log.d(TAG, "Logging out user: $userEmail")

            firebaseAuth.signOut()
            if (firebaseAuth.currentUser == null) {
                Log.d(TAG, "✅ Firebase logout successful")

                // Rapikan data lokal
                ScreeningDataManager.cancelSession(requireContext())
                ScreeningDataManager.clearAll(requireContext())
                clearAppData() // jika perlu & aman dipanggil di sini

                showToast(getString(R.string.logout_success))
                navigateToLoginScreen() // gunakan CLEAR_TASK + NEW_TASK
            } else {
                Log.e(TAG, "❌ Firebase logout failed - user still authenticated")
                throw IllegalStateException("Firebase logout failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during Firebase logout", e)
            if (!isAdded) return
            showError(getString(R.string.logout_failed_try_again))
            binding.btnLogout.isEnabled = true
            binding.btnLogout.text = getString(R.string.logout)
        }
    }


    private fun clearAppData() {
        try {
            Log.d(TAG, "Clearing app-specific data...")
            val editor = sharedPreferences.edit()
            editor.apply()
            val cacheDir = requireContext().cacheDir
            cacheDir.deleteRecursively()
            Log.d(TAG, "✅ App data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing app data", e)
        }
    }

    private fun showDeleteAccountDialog() {
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle("Hapus Akun")
            .setMessage("Tindakan ini permanen. Semua data profil Anda akan dihapus. Lanjutkan?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                deleteAccountWithBestEffort()
            }
            .show()
    }

    private fun setDeletingUi(isDeleting: Boolean) {
        binding.menuHapus.root.isEnabled = !isDeleting
    }


    private fun deleteAccountWithBestEffort() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Tidak ada user aktif.", Toast.LENGTH_SHORT).show()
            return
        }

        setDeletingUi(true)

        // 1) Hapus data Firestore user doc (dan optional foto di Storage)
        val uid = user.uid
        val users = FirebaseFirestore.getInstance().collection("users").document(uid)

        users.get()
            .addOnSuccessListener { doc ->
//                val photoUrl = doc.getString("photoUrl")
                // (Opsional) Hapus foto dari Firebase Storage jika url mengarah ke Storage
//                maybeDeleteStoragePhoto(photoUrl) {
                    // Lanjut hapus dokumen user
                users.delete()
                    .addOnSuccessListener {
                        // lalu lanjut hapus akun Auth
                        deleteAuthAccount()
                    }
                    .addOnFailureListener { e ->
                        // Tetap coba hapus akun Auth walau doc gagal (best effort)
                        Log.w(TAG, "Gagal hapus Firestore doc: ${e.message}")
                        deleteAuthAccount()
                    }
//                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Gagal ambil Firestore doc: ${e.message}")
                // Tetap lanjut hapus akun Auth
                deleteAuthAccount()
            }
    }

    /**
     * Hapus akun dari Firebase Auth. Jika butuh reauth, munculkan dialog reauth.
     */
    private fun deleteAuthAccount() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: run {
            setDeletingUi(false)
            Toast.makeText(requireContext(), "User sudah keluar.", Toast.LENGTH_SHORT).show()
            return
        }

        user.delete()
            .addOnSuccessListener {
                onAccountDeletedSuccess()
            }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthRecentLoginRequiredException) {
                    // Perlu re-authentication
                    showReauthDialog()
                } else {
                    setDeletingUi(false)
                    Toast.makeText(requireContext(), "Gagal hapus akun: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun onAccountDeletedSuccess() {
        // Bersihkan session lokal bila ada
        try {
            SessionManager.clear(this.requireContext())
        } catch (_: Exception) { }

        // Pastikan signOut
        FirebaseAuth.getInstance().signOut()

        Toast.makeText(requireContext(), "Akun berhasil dihapus.", Toast.LENGTH_LONG).show()

        // Arahkan ke OpeningActivity / Login
        startActivity(
            Intent(requireContext(), OpeningActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        requireActivity().finish()
    }

    /**
     * Reauth untuk email/password. Untuk Google/SSO, ganti dengan dapatkan credential Google lalu panggil user.reauthenticate(credential).
     */
    private fun showReauthDialog() {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email ?: ""

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reauth_password, null)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etPassword = view.findViewById<EditText>(R.id.etPassword)

        etEmail.setText(email)

        AlertDialog.Builder(requireContext())
            .setTitle("Verifikasi Ulang")
            .setMessage("Masukkan email & password untuk melanjutkan penghapusan akun.")
            .setView(view)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut") { _, _ ->
                val emailTxt = etEmail.text.toString().trim()
                val passTxt  = etPassword.text.toString().trim()
                reauthenticateAndDeleteEmailPassword(emailTxt, passTxt)
            }
            .show()
    }

    private fun reauthenticateAndDeleteEmailPassword(email: String, password: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val cred = EmailAuthProvider.getCredential(email, password)

        setDeletingUi(true)

        user.reauthenticate(cred)
            .addOnSuccessListener {
                // Sudah reauth → ulangi hapus
                deleteAuthAccount()
            }
            .addOnFailureListener { e ->
                setDeletingUi(false)
                Toast.makeText(requireContext(), "Reauth gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * (Opsional) Hapus foto profil dari Firebase Storage bila URL berasal dari Storage.
     * Jika bukan Storage URL, langsung lanjutkan completion().
     */
//    private fun maybeDeleteStoragePhoto(photoUrl: String?, completion: () -> Unit) {
//        if (photoUrl.isNullOrBlank()) {
//            completion(); return
//        }
//        // Hanya tangani jika ini URL storage Firebase
//        val isStorageUrl = photoUrl.startsWith("gs://") || photoUrl.contains("firebasestorage.googleapis.com")
//        if (!isStorageUrl) {
//            completion(); return
//        }
//
//        try {
//            val ref = FirebaseStorage.getInstance().getReferenceFromUrl(photoUrl)
//            ref.delete()
//                .addOnCompleteListener {
//                    // apa pun hasilnya, lanjut
//                    completion()
//                }
//        } catch (e: Exception) {
//            Log.w(TAG, "Gagal parse Storage URL: ${e.message}")
//            completion()
//        }
//    }

    private fun navigateToLoginScreen() {
        try {
            Log.d(TAG, "Navigating to login screen...")
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
            Log.d(TAG, "✅ Navigation to LoginActivity completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to login", e)
            showToast("Logout successful. Please restart the app.")
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Toast shown: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing toast", e)
        }
    }

    private fun showError(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error shown to user: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing error message", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        try {
            _binding = null
            Log.d(TAG, "✅ ProfileFragment cleaned up, binding nullified")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in cleanup", e)
        }
    }
}