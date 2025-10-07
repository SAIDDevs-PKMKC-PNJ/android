package com.pkm.said

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.pkm.said.databinding.FragmentProfileBinding
import com.pkm.said.databinding.ItemProfileMenuBinding
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.service.VoiceActivationService
import com.pkm.said.util.AuthManager
import com.pkm.said.util.SessionManager
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

    private fun setProfileLoading(isLoading: Boolean) {
        try {
            val contentGroup = binding.root.findViewById<ViewGroup>(R.id.container_card)
            val progressBar = view?.findViewById<View>(R.id.profileOverlay)

            if (isLoading) {
                progressBar?.visibility = View.VISIBLE
                contentGroup?.alpha = 0.4f
                contentGroup?.isEnabled = false
            } else {
                progressBar?.visibility = View.GONE
                contentGroup?.alpha = 1f
                contentGroup?.isEnabled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting loading state", e)
        }
    }

    private fun checkUserAuthentication() {
        if (currentUser == null) {
            Log.w(TAG, "User not authenticated, redirecting to login")
            showError("Silakan login untuk mengakses profil")
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
            val cachedName = SessionManager.getUserName(requireContext())
            if (cachedName != null) {
                binding.tvProfileName.text = cachedName
            }

            if (user == null) {
                setProfileLoading(false)
                return
            }

            // Ambil foto dari Firestore dulu
            if (user != null) {
                FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
                    .addOnSuccessListener { doc ->
                        val dbPhotoUrl = doc.getString("photoUrl")
                        val finalUrl = if (!dbPhotoUrl.isNullOrBlank()) dbPhotoUrl else user.photoUrl?.toString()

                        binding.tvProfileName.text = doc.getString("name") ?: user.displayName ?: "Pengguna"

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
                        val joinDate = user.metadata?.creationTimestamp?.let {
                            java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id"))
                                .format(java.util.Date(it))
                        } ?: "-"
                        binding.tvJoinDate.text = joinDate

                        SessionManager.saveBasicFromFirebase(requireContext(), user)
                        setProfileLoading(false)
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
                        setProfileLoading(false)
                    }
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_avatar_default)
                setProfileLoading(false)
            }

            // Menu
            setMenuItem(binding.menuInformasi, R.drawable.ic_user, "Informasi Pribadi")
            setMenuItem(binding.menuHistory, R.drawable.ic_location, "Riwayat Screening")
            setMenuItem(binding.menuSettings, R.drawable.ic_setting, "Setting reminder")
            setMenuItem(binding.menuHapus, R.drawable.ic_delete, "Hapus Akun")

            Log.d(TAG, "✅ User data setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up user data", e)
            setProfileLoading(false)
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
                val intent = Intent(requireContext(), NotificationSettingsActivity::class.java)
                startActivity(intent)
            }
            binding.menuHapus.root.setOnClickListener {
                Log.d(TAG, "Menu: Hapus Akun clicked")
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
            showToast("Gagal menampilkan dialog logout")
        }
    }

    private fun performFirebaseLogout() {
        Log.d(TAG, "Performing Firebase logout...")
        if (!isAdded) return

        binding.btnLogout.isEnabled = false
        binding.btnLogout.text = "Logging out..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                stopVoiceActivationService()

                val userEmail = currentUser?.email
                Log.d(TAG, "Logging out user: $userEmail")

                FirebaseAuth.getInstance().signOut()

                if (FirebaseAuth.getInstance().currentUser == null) {
                    Log.d(TAG, "✅ Firebase logout successful")

                    withContext(Dispatchers.IO) {
                        cleanupLocalData()
                    }

                    withContext(Dispatchers.Main) {
                        showToast("Logout berhasil")
                        navigateToLoginScreen()
                    }

                } else {
                    Log.e(TAG, "❌ Firebase logout failed - user still authenticated")
                    throw IllegalStateException("Firebase logout failed")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during Firebase logout", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        binding.btnLogout.isEnabled = true
                        binding.btnLogout.text = getString(R.string.logout)
                        showError("Logout gagal: ${e.message ?: "Coba lagi"}")
                    }
                }
            }
        }
    }

    private fun cleanupLocalData() {
        try {
            Log.d(TAG, "Cleaning local data...")
            ScreeningDataManager.cancelSession(requireContext())
            ScreeningDataManager.clearAll(requireContext())
            SessionManager.clear(requireContext())
            sharedPreferences.edit { clear() }
            Log.d(TAG, "✅ Local data cleaned successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning local data", e)
        }
    }

    private fun showDeleteAccountDialog() {
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle("Hapus Akun")
            .setMessage("Tindakan ini PERMANEN. Semua data profil dan riwayat screening akan dihapus. Lanjutkan?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                deleteAccountWithBestEffort()
            }
            .show()
    }

    private fun setDeletingUi(isDeleting: Boolean) {
        binding.menuHapus.root.isEnabled = !isDeleting
        binding.menuHapus.root.alpha = if (isDeleting) 0.5f else 1.0f

        if (isDeleting) {
            binding.menuHapus.menuTitle.text = "Menghapus akun..."
        } else {
            binding.menuHapus.menuTitle.text = "Hapus Akun"
        }
    }

    private fun deleteAccountWithBestEffort() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            showToast("Tidak ada user aktif.")
            return
        }

        setDeletingUi(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = user.uid

                // ✅ 1. Pastikan credentials valid untuk Firestore operations
                if (isAuthTokenExpired(user)) {
                    Log.w(TAG, "⚠️ Auth token expired, requesting reauth")
                    withContext(Dispatchers.Main) {
                        setDeletingUi(false)
                        showReauthDialog()
                    }
                    return@launch
                }

                // ✅ 2. Hapus Firestore data SELAGAM MASIH TERAUTHENTIKASI
                deleteFirestoreData(uid)

                // ✅ 3. Baru hapus auth account (akhir session)
                user.delete().await()
                Log.d(TAG, "✅ Firebase Auth account deleted successfully")

                // ✅ 4. Cleanup local data (tidak butuh auth)
                withContext(Dispatchers.Main) {
                    onAccountDeletedSuccess()
                }

            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                Log.w(TAG, "⚠️ Reauthentication required during process")
                withContext(Dispatchers.Main) {
                    setDeletingUi(false)
                    showReauthDialog()
                }
                return@launch
            } catch (e: Exception) {
                Log.e(TAG, "❌ Account deletion failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    setDeletingUi(false)
                    showError("Gagal menghapus akun: ${e.message ?: "Coba lagi nanti"}")
                }
            }
        }
    }

    private fun isAuthTokenExpired(user: FirebaseUser): Boolean {
        return try {
            // Cek last sign-in time (lebih reliable daripada force refresh)
            val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0
            val currentTime = System.currentTimeMillis()
            val hoursSinceLastSignIn = (currentTime - lastSignIn) / (1000 * 60 * 60)

            // Jika lebih dari 1 jam, mungkin butuh reauth
            hoursSinceLastSignIn > 1
        } catch (e: Exception) {
            Log.w(TAG, "Error checking auth token: ${e.message}")
            true // Safe default: assume need reauth
        }
    }

    private suspend fun deleteFirestoreData(uid: String) {
        val db = FirebaseFirestore.getInstance()

        try {
            Log.d(TAG, "🗑️ Starting Firestore cleanup for user: $uid")

            // 1. Hapus semua screenings dulu
            val screenings = db.collection("users").document(uid)
                .collection("screenings").get().await()

            Log.d(TAG, "📊 Found ${screenings.documents.size} screenings to delete")

            screenings.documents.forEachIndexed { index, doc ->
                try {
                    doc.reference.delete().await()
                    Log.d(TAG, "✅ Screening ${index + 1}/${screenings.size()} deleted: ${doc.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to delete screening ${doc.id}: ${e.message}")
                    // Continue dengan screening lainnya meski ada yang gagal
                }
            }

            // 2. Hapus user document
            try {
                db.collection("users").document(uid).delete().await()
                Log.d(TAG, "✅ User document deleted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to delete user document: ${e.message}")
                throw e // Re-throw karena ini critical
            }

            Log.d(TAG, "🎉 Firestore cleanup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Firestore cleanup failed: ${e.message}", e)
            throw e // Re-throw untuk handling di caller
        }
    }

    private fun showReauthDialog() {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email ?: ""

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_reauth_password, null)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etPassword = view.findViewById<EditText>(R.id.etPassword)

        etEmail.setText(email)
        etEmail.isEnabled = false

        AlertDialog.Builder(requireContext())
            .setTitle("Verifikasi Ulang")
            .setMessage("Masukkan password untuk konfirmasi penghapusan akun.")
            .setView(view)
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
                setDeletingUi(false)
            }
            .setPositiveButton("Konfirmasi Hapus") { _, _ ->
                val password = etPassword.text.toString().trim()
                if (password.isEmpty()) {
                    showError("Password harus diisi")
                    return@setPositiveButton
                }
                reauthenticateAndDelete(email, password)
            }
            .setOnCancelListener {
                setDeletingUi(false)
            }
            .show()
    }

    private fun reauthenticateAndDelete(email: String, password: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        setDeletingUi(true)

        user.reauthenticate(EmailAuthProvider.getCredential(email, password))
            .addOnSuccessListener {
                Log.d(TAG, "✅ Reauthentication successful")

                // ✅ LANGSUNG hapus data setelah reauth, jangan kembali ke awal
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val uid = user.uid

                        // 1. Hapus Firestore data DENGAN CREDENTIALS YANG SEGAR
                        deleteFirestoreData(uid)

                        // 2. Baru hapus auth account
                        user.delete().await()
                        Log.d(TAG, "✅ Firebase Auth account deleted successfully")

                        // 3. Cleanup
                        withContext(Dispatchers.Main) {
                            onAccountDeletedSuccess()
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Post-reauth deletion failed: ${e.message}")
                        withContext(Dispatchers.Main) {
                            setDeletingUi(false)
                            showError("Gagal menghapus data: ${e.message ?: "Coba lagi"}")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Reauthentication failed: ${e.message}")
                setDeletingUi(false)
                showError("Password salah atau verifikasi gagal")
            }
    }

    private fun onAccountDeletedSuccess() {
        Log.d(TAG, "🎉 Account deletion completed successfully")

        cleanupLocalData()
        stopVoiceActivationService()
        FirebaseAuth.getInstance().signOut()

        showToast("Akun berhasil dihapus")
        navigateToLoginScreen()
    }

    private fun stopVoiceActivationService() {
        try {
            Log.d(TAG, "Stopping Voice Activation Service...")
            val stopIntent = Intent(requireContext(), VoiceActivationService::class.java).apply {
                action = VoiceActivationService.ACTION_STOP
            }
            requireContext().stopService(stopIntent)
            Log.d(TAG, "✅ Voice Activation Service stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping Voice Activation Service", e)
        }
    }

    private fun navigateToLoginScreen() {
        try {
            Log.d(TAG, "Navigating to login screen...")
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            requireActivity().finish()
            Log.d(TAG, "✅ Navigation to LoginActivity completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to login", e)
            try {
                val restartIntent = Intent(requireContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(restartIntent)
                requireActivity().finish()
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Even fallback navigation failed", e2)
                showToast("Logout berhasil. Silakan restart aplikasi.")
            }
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

    override fun onResume() {
        super.onResume()
        if (!AuthManager.ensureUserLoggedIn(requireActivity())) return
    }
}