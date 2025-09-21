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
            } ?: "27 Januari 2025"
            binding.tvJoinDate.text = joinDate

            // Profile picture pakai Glide
            user?.photoUrl?.let { photoUri ->
                Glide.with(this)
                    .load(photoUri)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivProfile)
                Log.d(TAG, "Loaded photoUrl: $photoUri")
            } ?: run {
                binding.ivProfile.setImageResource(R.drawable.ic_person)
                Log.d(TAG, "No photoUrl, using default icon")
            }

            // Menu text & icon: binding.child bisa diakses manual jika pakai <include>
            setMenuItem(binding.menuInformasi, R.drawable.ic_user, "Informasi Pribadi")
            setMenuItem(binding.menuGeologi, R.drawable.ic_location, "Posisi Geologi")
            setMenuItem(binding.menuSettings, R.drawable.ic_setting, "Settings")
            setMenuItem(binding.menuDonasi, R.drawable.ic_donation, "Donasi")

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
            binding.menuGeologi.root.setOnClickListener {
                Log.d(TAG, "Menu: Posisi Geologi clicked")
//                findNavController().navigate(R.id.action_profileFragment_to_geologiFragment)
            }
            binding.menuSettings.root.setOnClickListener {
                Log.d(TAG, "Menu: Settings clicked")
                showToast("Fitur Settings belum tersedia")
            }
            binding.menuDonasi.root.setOnClickListener {
                Log.d(TAG, "Menu: Donasi clicked")
                showToast("Fitur Donasi belum tersedia")
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
        try {
            Log.d(TAG, "Performing Firebase logout...")
            binding.btnLogout.isEnabled = false
            binding.btnLogout.text = "Logging out..."
            val userEmail = currentUser?.email
            Log.d(TAG, "Logging out user: $userEmail")
            firebaseAuth.signOut()
            if (firebaseAuth.currentUser == null) {
                Log.d(TAG, "✅ Firebase logout successful")
                clearAppData()
                showToast("Successfully logged out!")
                navigateToLoginScreen()
            } else {
                Log.e(TAG, "❌ Firebase logout failed - user still authenticated")
                throw Exception("Firebase logout failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during Firebase logout", e)
            binding.btnLogout.isEnabled = true
            binding.btnLogout.text = "Logout"
            showError("Failed to logout. Please try again.")
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