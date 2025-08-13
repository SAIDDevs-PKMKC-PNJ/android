package com.pkm.said

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.pkm.said.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var onBackPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "=== MAIN ACTIVITY DEBUG START ===")
        Log.d("MainActivity", "Current Date: 2025-07-31 06:51:16")
        Log.d("MainActivity", "Current User: itsLuxra")
        Log.d("MainActivity", "🎯 NO TOOLBAR MODE - Clean and Simple")
        Log.d("MainActivity", "Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}")

        try {
            Log.d("MainActivity", "Inflating layout with View Binding...")
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("MainActivity", "✅ View Binding setup completed")

            setupNavigation()
            setupBackPressedHandler()

            Log.d("MainActivity", "✅ MainActivity setup completed successfully - NO TOOLBAR!")

        } catch (e: Exception) {
            Log.e("MainActivity", "❌ CRITICAL ERROR in onCreate", e)
            e.printStackTrace()
        }
    }

    private fun setupNavigation() {
        try {
            Log.d("MainActivity", "🔧 Starting navigation setup...")

            // Find navigation controller with detailed error handling
            Log.d("MainActivity", "Looking for NavController with ID: nav_host_fragment")
            navController = try {
                val controller = findNavController(R.id.nav_host_fragment)
                Log.d("MainActivity", "✅ NavController found successfully!")
                Log.d("MainActivity", "NavController class: ${controller.javaClass.simpleName}")
                Log.d("MainActivity", "Current destination: ${controller.currentDestination?.id}")
                Log.d("MainActivity", "Current destination label: ${controller.currentDestination?.label}")
                controller
            } catch (e: IllegalArgumentException) {
                Log.e("MainActivity", "❌ NavController - IllegalArgumentException", e)
                Log.e("MainActivity", "ERROR: nav_host_fragment ID not found in layout!")
                Log.e("MainActivity", "Check if Fragment exists in activity_main.xml")
                throw e
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "❌ NavController - IllegalStateException", e)
                Log.e("MainActivity", "ERROR: NavHostFragment not properly configured!")
                Log.e("MainActivity", "Check navigation graph or fragment setup")
                throw e
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ NavController - Unknown Exception", e)
                Log.e("MainActivity", "ERROR TYPE: ${e.javaClass.simpleName}")
                Log.e("MainActivity", "ERROR MESSAGE: ${e.message}")
                throw e
            }

            // ✅ NO AppBar configuration needed - removed completely
            Log.d("MainActivity", "✅ Skipping AppBar setup - using custom headers in fragments")

            // Setup bottom navigation with nav controller
            Log.d("MainActivity", "Setting up bottom navigation with NavController...")
            try {
                binding.bottomNavView.setupWithNavController(navController)
                Log.d("MainActivity", "✅ Bottom navigation setup with NavController completed")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error setting up bottom nav with NavController", e)
                throw e
            }

            // ✅ NO destination listener needed for toolbar - removed completely
            Log.d("MainActivity", "✅ Skipping destination listener - no toolbar to manage")

            // Navigate to home by default
            if (navController.currentDestination?.id != R.id.navigation_home) {
                Log.d("MainActivity", "Navigating to home as default destination")
                navController.navigate(R.id.navigation_home)
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "❌ NAVIGATION SETUP FAILED!", e)
            Log.e("MainActivity", "Exception type: ${e.javaClass.simpleName}")
            Log.e("MainActivity", "Exception message: ${e.message}")
            Log.e("MainActivity", "Stack trace:")
            e.printStackTrace()

            Log.d("MainActivity", "Closing MainActivity due to navigation failure...")
            finish()
            return
        }
    }

    private fun setupBackPressedHandler() {
        try {
            Log.d("MainActivity", "🔧 Setting up back pressed handler...")

            onBackPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.d("MainActivity", "🔙 Custom back pressed triggered")
                    handleCustomBackPressed()
                }
            }

            onBackPressedCallback?.let { callback ->
                onBackPressedDispatcher.addCallback(this, callback)
                Log.d("MainActivity", "✅ Back pressed callback registered successfully")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error setting up back handler", e)
        }
    }

    private fun handleCustomBackPressed() {
        try {
            val currentDestId = navController.currentDestination?.id
            val currentDestLabel = navController.currentDestination?.label

            Log.d("MainActivity", "🔄 Handling back press...")
            Log.d("MainActivity", "Current destination ID: $currentDestId")
            Log.d("MainActivity", "Current destination label: $currentDestLabel")

            when (currentDestId) {
                R.id.navigation_home -> {
                    Log.d("MainActivity", "At HOME - finishing app")
                    finishApp()
                }
                R.id.navigation_news,
                R.id.navigation_chatbot -> {
                    Log.d("MainActivity", "At other tab - navigating to HOME")
                    navController.navigate(R.id.navigation_home)
                }
                else -> {
                    Log.d("MainActivity", "At unknown destination - using navigateUp")
                    if (!navController.navigateUp()) {
                        Log.d("MainActivity", "NavigateUp failed - finishing app")
                        finishApp()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error in back press handling", e)
            finishApp()
        }
    }

    private fun finishApp() {
        try {
            Log.d("MainActivity", "🚪 Finishing app...")
            Log.d("MainActivity", "SDK Version: ${Build.VERSION.SDK_INT}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.d("MainActivity", "Using finishAndRemoveTask()")
                finishAndRemoveTask()
            } else {
                Log.d("MainActivity", "Using finish()")
                finish()
            }

            Log.d("MainActivity", "✅ App finish called")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error finishing app", e)
            finish()
        }
    }

    // ✅ REMOVED: All toolbar related functions
    // - setupToolbar() ❌
    // - setupDestinationListener() ❌
    // - showToolbar() ❌
    // - hideToolbar() ❌
    // - showBottomNavigation() ❌
    // - hideBottomNavigation() ❌

    // ✅ REMOVED: Toolbar navigation support
    // override fun onSupportNavigateUp(): Boolean {
    //     // Not needed without toolbar
    // }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "📱 MainActivity onStart()")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "📱 MainActivity onResume()")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "📱 MainActivity onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "📱 MainActivity onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "=== MAIN ACTIVITY DEBUG END ===")
        Log.d("MainActivity", "🎯 Clean shutdown - NO TOOLBAR dependencies")
    }
}