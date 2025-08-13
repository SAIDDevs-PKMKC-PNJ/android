package com.pkm.said.screening

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.pkm.said.databinding.FragmentSensorTestBinding
import androidx.navigation.fragment.findNavController
import com.pkm.said.R

class SensorTestFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentSensorTestBinding? = null
    private val binding get() = _binding!!

    private var sensorManager: SensorManager? = null
    private var isTesting = false
    private var step = 0

    private val testTimeoutMs = 15000L // 15 detik
    private var testHandler: Handler? = null
    private var testTimeoutRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSensorTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        binding.btnConfirmSensor.visibility = View.GONE
        binding.btnRetrySensor.visibility = View.GONE
        binding.btnSkipSensor.visibility = View.GONE
        binding.tvInstruction.text = "Tekan tombol di bawah untuk mulai tes sensor."

        binding.btnStartSensorTest.setOnClickListener {
            if (!isTesting) {
                startTest()
            }
        }

        binding.btnConfirmSensor.setOnClickListener {
            saveSensorResult(true, "Berhasil gerak dan miring ke kiri dan kanan")
        }
        binding.btnSkipSensor.setOnClickListener {
            saveSensorResult(false, "Gerakan tidak terbaca oleh sensor")
        }
        binding.btnRetrySensor.setOnClickListener {
            resetTest()
            startTest()
        }
    }

    private fun startTest() {
        isTesting = true
        step = 1
        binding.tvInstruction.text = "Gerakkan HP ke kiri (geser cepat ke kiri)!"
        binding.btnStartSensorTest.isEnabled = false
        binding.btnConfirmSensor.visibility = View.GONE
        binding.btnRetrySensor.visibility = View.GONE
        binding.btnSkipSensor.visibility = View.GONE

        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)

        // Mulai timeout
        testHandler = Handler()
        testTimeoutRunnable = Runnable {
            if (isTesting) {
                sensorManager?.unregisterListener(this)
                isTesting = false
                binding.tvInstruction.text = "Gerakan tidak terbaca! Ulangi atau lanjutkan tes."
                binding.btnRetrySensor.visibility = View.VISIBLE
                binding.btnSkipSensor.visibility = View.VISIBLE
            }
        }
        testHandler?.postDelayed(testTimeoutRunnable!!, testTimeoutMs)
    }

    private fun resetTest() {
        sensorManager?.unregisterListener(this)
        testHandler?.removeCallbacks(testTimeoutRunnable!!)
        testHandler = null
        testTimeoutRunnable = null
        isTesting = false
        step = 0
        binding.tvInstruction.text = "Tekan tombol di bawah untuk mulai tes sensor."
        binding.btnStartSensorTest.isEnabled = true
        binding.btnConfirmSensor.visibility = View.GONE
        binding.btnRetrySensor.visibility = View.GONE
        binding.btnSkipSensor.visibility = View.GONE
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTesting || event == null) return

        when (step) {
            1 -> {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    if (x < -4f) {
                        step = 2
                        binding.tvInstruction.text = "Sekarang miringkan HP ke kiri!"
                        Toast.makeText(requireContext(), "Gerak ke kiri terdeteksi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            2 -> {
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val y = event.values[1]
                    if (y < -1f) {
                        step = 3
                        binding.tvInstruction.text = "Gerakkan HP ke kanan (geser cepat ke kanan)!"
                        Toast.makeText(requireContext(), "Miring kiri terdeteksi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            3 -> {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    if (x > 4f) {
                        step = 4
                        binding.tvInstruction.text = "Sekarang miringkan HP ke kanan!"
                        Toast.makeText(requireContext(), "Gerak ke kanan terdeteksi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            4 -> {
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val y = event.values[1]
                    if (y > 1f) {
                        step = 5
                        binding.tvInstruction.text = "Miring ke kanan terdeteksi!\nKlik Konfirmasi untuk lanjut."
                        binding.btnConfirmSensor.visibility = View.VISIBLE
                        sensorManager?.unregisterListener(this)
                        isTesting = false
                        testHandler?.removeCallbacks(testTimeoutRunnable!!)
                        testHandler = null
                        testTimeoutRunnable = null
                        Toast.makeText(requireContext(), "Miring ke kanan terdeteksi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveSensorResult(success: Boolean, notes: String) {
        ScreeningDataManager.updateTestResult(
            requireContext(),
            TestResult(
                testName = "sensor",
                isCompleted = true,
                isSuccessful = success,
                score = if (success) 1f else 0f,
                notes = notes,
                timestamp = getCurrentTimestamp()
            )
        )
        Toast.makeText(requireContext(), "Tes sensor selesai!", Toast.LENGTH_SHORT).show()
        findNavController().navigate(R.id.action_sensor_to_camera)
    }

    private fun getCurrentTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sensorManager?.unregisterListener(this)
        testHandler?.removeCallbacks(testTimeoutRunnable ?: Runnable {})
        _binding = null
    }
}