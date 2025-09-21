package com.pkm.said

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.pkm.said.screening.ScreeningActivity

class HomeActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, HomeActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("HomeActivity", "=== HOME ACTIVITY DEBUG START ===")
        Log.d("HomeActivity", "Current Date: 2025-07-29 13:03:39")
        Log.d("HomeActivity", "Current User: itsLuxra")

        try {
            Log.d("HomeActivity", "Setting content view to activity_home layout...")
            setContentView(R.layout.activity_home) // Ganti dari fragment_home ke activity_home
            Log.d("HomeActivity", "✅ Layout set successfully")

            setupViews()

        } catch (e: Exception) {
            Log.e("HomeActivity", "❌ Error setting up activity", e)
            e.printStackTrace()
        }
    }

    private fun setupViews() {
        Log.d("HomeActivity", "🔧 Setting up view components...")

        try {
            // ✅ Setup CardView scan button
            Log.d("HomeActivity", "Looking for buttonScan CardView...")
            val buttonScanCard: CardView = findViewById(R.id.buttonScan)
            Log.d("HomeActivity", "✅ buttonScan CardView found: ${buttonScanCard != null}")

            val buttonBack: ImageButton = findViewById(R.id.btnBack)

            if (buttonScanCard != null) {
                Log.d("HomeActivity", "CardView isClickable: ${buttonScanCard.isClickable}")
                Log.d("HomeActivity", "CardView isEnabled: ${buttonScanCard.isEnabled}")
            }

            // ✅ Set click listener pada CardView
            buttonScanCard.setOnClickListener {
                Log.d("HomeActivity", "🔘 Scan button clicked!")
                Log.d("HomeActivity", "Current Date: 2025-07-29 13:03:39")
                Log.d("HomeActivity", "Current User: itsLuxra")

                startScreeningProcess()
            }

            buttonBack.setOnClickListener {
                // Cukup kembali ke aktivitas sebelumnya (MainActivity dengan Dashboard)
                onBackPressedDispatcher.onBackPressed()
                // atau: finish()
            }

            Log.d("HomeActivity", "✅ CardView click listener set successfully")
            Log.d("HomeActivity", "✅ HomeActivity setup completed successfully")

        } catch (e: Exception) {
            Log.e("HomeActivity", "❌ Error setting up view components", e)
            Log.e("HomeActivity", "Error type: ${e.javaClass.simpleName}")
            Log.e("HomeActivity", "Error message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startScreeningProcess() {
        try {
            Log.d("HomeActivity", "🚀 Launching dedicated screening activity...")
            Log.d("HomeActivity", "User: itsLuxra starting screening at 2025-07-31 14:52:26")

            // Launch dedicated screening activity
            ScreeningActivity.start(this, "itsLuxra")

            Log.d("HomeActivity", "✅ Screening activity launched")
        } catch (e: Exception) {
            Log.e("HomeActivity", "❌ Error starting screening process", e)
            Toast.makeText(this, "Error starting screening", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("HomeActivity", "=== HOME ACTIVITY DEBUG END ===")
    }
}