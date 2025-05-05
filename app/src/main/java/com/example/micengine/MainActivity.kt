package com.example.micengine

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.micengine.databinding.ActivityMainBinding
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    
    private var audioRecord: AudioRecord? = null
    private var soundPool: SoundPool? = null
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())

    // Motor sesi ID'si ve stream ID'si
    private var engineSoundId = 0
    private var currentStreamId = 0

    // Motor durumu
    private var currentVolume = 0.5f
    private var currentRate = 1.0f
    private var targetVolume = 0.5f
    private var targetRate = 1.0f

    private val bufferSize by lazy {
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 123
        private const val SAMPLE_RATE = 44100
        private const val VOLUME_STEP = 0.05f
        private const val RATE_STEP = 0.05f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d(TAG, "onCreate başlıyor")
            
            // View binding'i başlat
            try {
                val inflater = LayoutInflater.from(this)
                _binding = ActivityMainBinding.inflate(inflater)
                val view = _binding?.root
                if (view != null) {
                    setContentView(view)
                    Log.d(TAG, "View binding başarıyla oluşturuldu")
                } else {
                    throw Exception("View binding root null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "View binding hatası: ${e.message}")
                showErrorAndFinish("Arayüz oluşturulamadı: ${e.message}")
                return
            }
            
            // SoundPool'u başlat
            setupSoundPool()
            
            // UI elemanlarını başlat
            try {
                initializeUI()
                Log.d(TAG, "UI başarıyla başlatıldı")
            } catch (e: Exception) {
                Log.e(TAG, "UI başlatma hatası: ${e.message}")
                showErrorAndFinish("Arayüz başlatılamadı: ${e.message}")
                return
            }
            
            // İzinleri kontrol et
            try {
                checkPermissions()
                Log.d(TAG, "İzinler kontrol edildi")
            } catch (e: Exception) {
                Log.e(TAG, "İzin kontrolü hatası: ${e.message}")
                showErrorAndFinish("İzinler kontrol edilemedi: ${e.message}")
                return
            }
            
            // Motor sesi güncelleme döngüsünü başlat
            startEngineLoop()
            
        } catch (e: Exception) {
            Log.e(TAG, "onCreate genel hatası: ${e.message}")
            showErrorAndFinish("Uygulama başlatılamadı: ${e.message}")
        }
    }

    private fun setupSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()

        // Motor sesini yükle
        engineSoundId = soundPool?.load(this, R.raw.engine_idle, 1) ?: 0
    }

    private fun startEngineLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isRecording) {
                    updateEngineSound()
                }
                handler.postDelayed(this, 50) // 20 FPS
            }
        }, 50)
    }

    private fun updateEngineSound() {
        // Ses ve hızı hedef değerlere doğru yumuşak bir şekilde güncelle
        if (currentVolume < targetVolume) {
            currentVolume = (currentVolume + VOLUME_STEP).coerceAtMost(targetVolume)
        } else if (currentVolume > targetVolume) {
            currentVolume = (currentVolume - VOLUME_STEP).coerceAtLeast(targetVolume)
        }

        if (currentRate < targetRate) {
            currentRate = (currentRate + RATE_STEP).coerceAtMost(targetRate)
        } else if (currentRate > targetRate) {
            currentRate = (currentRate - RATE_STEP).coerceAtLeast(targetRate)
        }

        // Ses ayarlarını uygula
        if (currentStreamId == 0) {
            // İlk kez başlatılıyorsa
            currentStreamId = soundPool?.play(engineSoundId, currentVolume, currentVolume, 1, -1, currentRate) ?: 0
        } else {
            // Devam eden sesi güncelle
            soundPool?.apply {
                setVolume(currentStreamId, currentVolume, currentVolume)
                setRate(currentStreamId, currentRate)
            }
        }

        // UI güncelle
        binding.soundLevelBar.progress = ((currentVolume + currentRate) * 50).toInt()
    }

    private fun initializeUI() {
        if (_binding == null) {
            throw Exception("View binding null")
        }
        
        binding.btnStartStop.apply {
            isEnabled = false
            text = getString(R.string.start)
            setOnClickListener {
                if (isRecording) stopRecording() else startRecording()
            }
        }
        binding.soundLevelBar.progress = 0
    }

    private fun startRecording() {
        try {
            if (audioRecord == null) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            isRecording = true
            binding.btnStartStop.text = getString(R.string.stop)

            Thread {
                try {
                    val buffer = ShortArray(bufferSize)
                    audioRecord?.startRecording()
                    Log.d(TAG, "Kayıt başladı")

                    while (isRecording) {
                        val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                        if (readSize > 0) {
                            val rms = calculateRMS(buffer, readSize)
                            updateEngineFromMic(rms)
                        }
                        Thread.sleep(50)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Kayıt thread hatası: ${e.message}")
                    handler.post { 
                        stopRecording()
                        showError("Kayıt sırasında hata oluştu")
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "startRecording hatası: ${e.message}")
            stopRecording()
            showError("Kayıt başlatılamadı")
        }
    }

    private fun stopRecording() {
        try {
            isRecording = false
            binding.btnStartStop.text = getString(R.string.start)
            
            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord durdurma hatası: ${e.message}")
            }
            
            // Motor sesini rölantiye al
            targetVolume = 0.3f
            targetRate = 0.8f
            
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording hatası: ${e.message}")
        }
    }

    private fun calculateRMS(buffer: ShortArray, readSize: Int): Double {
        return try {
            var sum = 0.0
            for (i in 0 until readSize) {
                sum += buffer[i] * buffer[i]
            }
            sqrt(sum / readSize)
        } catch (e: Exception) {
            Log.e(TAG, "RMS hesaplama hatası: ${e.message}")
            0.0
        }
    }

    private fun updateEngineFromMic(rms: Double) {
        val normalizedRMS = (rms / 32768.0).coerceIn(0.0, 1.0)
        
        // Ses şiddetine göre motor sesini ayarla
        targetVolume = (0.3f + (normalizedRMS * 0.7f)).toFloat().coerceIn(0.3f, 1.0f)
        targetRate = (0.8f + (normalizedRMS * 1.2f)).toFloat().coerceIn(0.8f, 2.0f)
    }

    private fun showError(message: String) {
        try {
            if (!isFinishing) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
            Log.e(TAG, "Hata mesajı gösterildi: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Hata mesajı gösterme hatası: ${e.message}")
        }
    }

    private fun showErrorAndFinish(message: String) {
        showError(message)
        if (!isFinishing) {
            finish()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        } else {
            binding.btnStartStop.isEnabled = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                binding.btnStartStop.isEnabled = true
            } else {
                showErrorAndFinish("Mikrofon izni gerekli")
            }
        }
    }

    override fun onDestroy() {
        try {
            Log.d(TAG, "onDestroy başladı")
            super.onDestroy()
            stopRecording()
            soundPool?.release()
            audioRecord?.release()
            _binding = null
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy hatası: ${e.message}")
        }
    }
}