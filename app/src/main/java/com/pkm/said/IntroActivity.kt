package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.pkm.said.adapter.IntroSlideAdapter

class IntroActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IntroActivity"
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var btnPrevious: TextView
    private lateinit var btnNext: TextView

    private var currentPage = 0

    private val introSlides = listOf(
        IntroSlide(
            "Deteksi",
            "Deteksi gejala stroke bagi lansia dengan cepat dan tanggap.",
            R.mipmap.intro_deteksi_foreground
        ),
        IntroSlide(
            "Tangani",
            "Terhubung dengan panggilan darurat dan tenaga ahli yang dapat menyelamatkan nyawa.",
            R.mipmap.intro_tangani_foreground
        ),
        IntroSlide(
            "Lindungi",
            "Jaga orang terkasih dari bahaya stroke. Lindungi dan jaga dengan SAID",
            R.mipmap.intro_lindungi_foreground
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== INTRO ACTIVITY START ===")
        Log.d(TAG, "Current Date and Time: 2025-07-31 11:43:38")
        Log.d(TAG, "Current User's Login: itsLuxra")
        Log.d(TAG, "🎯 NO SharedPreferences - Clean Implementation")

        setContentView(R.layout.activity_intro)

        initializeViews()
        setupViewPager()
        setupTabIndicator()
        setupNavigationButtons()

        Log.d(TAG, "✅ IntroActivity setup completed successfully")
    }

    private fun initializeViews() {
        try {
            Log.d(TAG, "🔧 Initializing views...")

            viewPager = findViewById(R.id.viewPagerIntro)
            tabLayout = findViewById(R.id.tabLayoutIndicator)
            btnPrevious = findViewById(R.id.btnPrevious)
            btnNext = findViewById(R.id.btnNext)

            Log.d(TAG, "✅ Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing views", e)
            throw e
        }
    }

    private fun setupViewPager() {
        try {
            Log.d(TAG, "🔧 Setting up ViewPager...")

            val adapter = IntroSlideAdapter(introSlides)
            viewPager.adapter = adapter

            // Register page change callback
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentPage = position
                    updateNavigationButtons(position)
                    Log.d(TAG, "📍 Page changed to: $position")
                }
            })

            Log.d(TAG, "✅ ViewPager setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up ViewPager", e)
        }
    }

    private fun setupTabIndicator() {
        try {
            Log.d(TAG, "🔧 Setting up tab indicator...")

            // Connect TabLayout with ViewPager2 for dot indicators
            TabLayoutMediator(tabLayout, viewPager) { _, _ ->
                // No text needed, just dots styled by tab_selector.xml
            }.attach()

            Log.d(TAG, "✅ Tab indicator setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up tab indicator", e)
        }
    }

    private fun setupNavigationButtons() {
        try {
            Log.d(TAG, "🔧 Setting up navigation buttons...")

            // Previous button click listener
            btnPrevious.setOnClickListener {
                Log.d(TAG, "⬅️ Previous button clicked by itsLuxra")
                if (currentPage > 0) {
                    viewPager.currentItem = currentPage - 1
                } else {
                    Log.d(TAG, "Already at first page")
                }
            }

            // Next button click listener
            btnNext.setOnClickListener {
                Log.d(TAG, "➡️ Next button clicked by itsLuxra")
                if (currentPage < introSlides.size - 1) {
                    viewPager.currentItem = currentPage + 1
                } else {
                    Log.d(TAG, "🎯 Last page reached - finishing intro")
                    finishIntro()
                }
            }

            // Set initial button state
            updateNavigationButtons(0)

            Log.d(TAG, "✅ Navigation buttons setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up navigation buttons", e)
        }
    }

    private fun updateNavigationButtons(position: Int) {
        try {
            Log.d(TAG, "🔄 Updating navigation buttons for position: $position")

            val totalPages = introSlides.size

            // Update Previous button visibility and style
            if (position == 0) {
                btnPrevious.visibility = View.INVISIBLE
                Log.d(TAG, "Previous button hidden (first page)")
            } else {
                btnPrevious.visibility = View.VISIBLE
                btnPrevious.text = "Sebelum"
                btnPrevious.setTextColor(ContextCompat.getColor(this, R.color.GrayLight))
                Log.d(TAG, "Previous button visible")
            }

            // Update Next button text and style
            if (position == totalPages - 1) {
                btnNext.text = "Mulai"
                btnNext.setTextColor(ContextCompat.getColor(this, R.color.lightBlue))
                Log.d(TAG, "Next button set to 'Mulai' (last page)")
            } else {
                btnNext.text = "Lanjut"
                btnNext.setTextColor(ContextCompat.getColor(this, R.color.lightBlue))
                Log.d(TAG, "Next button set to 'Lanjut'")
            }

            Log.d(TAG, "✅ Navigation buttons updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating navigation buttons", e)
        }
    }

    private fun finishIntro() {
        try {
            Log.d(TAG, "🎯 Finishing intro sequence...")
            Log.d(TAG, "User: itsLuxra completed intro at 2025-07-31 11:43:38")

            // Create intent for MainActivity
            Log.d(TAG, "🚀 Creating Intent for LoginActivity...")
            val intent = Intent(this, LoginActivity::class.java)
            Log.d(TAG, "✅ Intent created successfully")

            // Start MainActivity
            Log.d(TAG, "🚀 Starting LoginActivity...")
            startActivity(intent)
            Log.d(TAG, "✅ MainActivity started successfully")

            // Finish IntroActivity
            Log.d(TAG, "🚪 Finishing IntroActivity...")
            finish()

            // Optional: Add smooth transition animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            Log.d(TAG, "✅ Intro sequence completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR finishing intro!", e)
            e.printStackTrace()

            // Fallback: try to finish anyway
            try {
                finish()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "❌ Even fallback finish failed!", fallbackError)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "📱 IntroActivity onStart()")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 IntroActivity onResume()")
        Log.d(TAG, "Current page: $currentPage/${introSlides.size}")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "📱 IntroActivity onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "📱 IntroActivity onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== INTRO ACTIVITY END ===")
        Log.d(TAG, "itsLuxra finished intro session")

        // Clean up ViewPager callback to prevent memory leaks
        try {
            if (::viewPager.isInitialized) {
                viewPager.adapter = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up ViewPager", e)
        }
    }

    // ✅ REMOVED: SharedPreferences related code
    // - PREFS_NAME constant
    // - PREF_INTRO_SHOWN constant
    // - SharedPreferences logic
    // Clean implementation without persistence
}