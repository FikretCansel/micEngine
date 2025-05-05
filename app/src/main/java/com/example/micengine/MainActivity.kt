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
import kotlin.math.pow

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    
    private var audioRecord: AudioRecord? = null
    private var soundPool: SoundPool? = null
    private var isRecording = false
    private var isSoundLoaded = false
    private val handler = Handler(Looper.getMainLooper())

    // Motor sesi ID'si ve stream ID'si
    private var engineSoundId = 0
    private var currentStreamId = 0

    // Motor durumu
    private var currentVolume = MIN_VOLUME
    private var currentRate = MIN_RATE
    private var targetVolume = MIN_VOLUME
    private var targetRate = MIN_RATE

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
        private const val VOLUME_STEP = 0.02f
        private const val RATE_STEP = 0.02f
        
        // Motor sesi parametreleri
        private const val MIN_VOLUME = 0.2f
        private const val MAX_VOLUME = 1.0f
        private const val MIN_RATE = 0.6f
        private const val MAX_RATE = 2.5f
        private const val VOLUME_RANGE = MAX_VOLUME - MIN_VOLUME
        private const val RATE_RANGE = MAX_RATE - MIN_RATE

        // Mikrofon hassasiyet parametreleri
        private const val MIC_SENSITIVITY = 2.5f     // Mikrofon hassasiyet çarpanı
        private const val NOISE_THRESHOLD = 0.05f    // Gürültü eşiği
        private const val RMS_SMOOTHING = 0.7f       // RMS yumuşatma faktörü (0-1 arası)
    }

    // Son RMS değeri için değişken
    private var lastRMS = 0.0f

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
            
            // UI elemanlarını başlat
            try {
                initializeUI()
                Log.d(TAG, "UI başarıyla başlatıldı")
            } catch (e: Exception) {
                Log.e(TAG, "UI başlatma hatası: ${e.message}")
                showErrorAndFinish("Arayüz başlatılamadı: ${e.message}")
                return
            }
            
            // SoundPool'u başlat
            setupSoundPool()
            
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
        try {
            Log.d(TAG, "SoundPool başlatılıyor...")
            
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(attributes)
                .build()

            soundPool?.setOnLoadCompleteListener { pool, sampleId, status ->
                Log.d(TAG, "Ses yükleme durumu - SampleId: $sampleId, Status: $status")
                if (status == 0) {
                    Log.d(TAG, "Ses başarıyla yüklendi")
                    isSoundLoaded = true
                    runOnUiThread {
                        binding.btnStartStop.isEnabled = true
                    }
                } else {
                    Log.e(TAG, "Ses yüklenemedi!")
                    runOnUiThread {
                        showError("Motor sesi yüklenemedi!")
                    }
                }
            }

            engineSoundId = soundPool?.load(this, R.raw.engine_idle, 1) ?: 0
            Log.d(TAG, "Ses yükleme başlatıldı - engineSoundId: $engineSoundId")
            
            if (engineSoundId == 0) {
                throw Exception("Ses dosyası yüklenemedi")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "SoundPool başlatma hatası: ${e.message}")
            showError("Ses sistemi başlatılamadı: ${e.message}")
        }
    }

    private fun startEngineSound() {
        if (!isSoundLoaded || engineSoundId == 0) {
            Log.e(TAG, "Ses henüz yüklenmedi!")
            showError("Motor sesi hazır değil!")
            return
        }

        try {
            // Mevcut sesi durdur
            if (currentStreamId != 0) {
                soundPool?.stop(currentStreamId)
            }

            // Yeni sesi başlat
            Log.d(TAG, "Motor sesi başlatılıyor - Volume: $currentVolume, Rate: $currentRate")
            currentStreamId = soundPool?.play(engineSoundId, currentVolume, currentVolume, 1, -1, currentRate) ?: 0

            if (currentStreamId == 0) {
                throw Exception("Ses başlatılamadı")
            }

            Log.d(TAG, "Motor sesi başlatıldı - StreamId: $currentStreamId")
        } catch (e: Exception) {
            Log.e(TAG, "Ses başlatma hatası: ${e.message}")
            showError("Motor sesi başlatılamadı: ${e.message}")
        }
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
        if (currentStreamId == 0) {
            startEngineSound()
            return
        }

        try {
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
            soundPool?.apply {
                setVolume(currentStreamId, currentVolume, currentVolume)
                setRate(currentStreamId, currentRate)
            }

            // UI güncelle - mikrofon seviyesini normalize et
            val normalizedLevel = ((currentVolume - MIN_VOLUME) / VOLUME_RANGE * 100).toInt()
            binding.soundLevelBar.progress = normalizedLevel.coerceIn(0, 100)
            
            // Debug log
            Log.d(TAG, "Ses güncellendi - Volume: $currentVolume, Rate: $currentRate, Level: $normalizedLevel")
            
        } catch (e: Exception) {
            Log.e(TAG, "Ses güncelleme hatası: ${e.message}")
            currentStreamId = 0 // Bir sonraki güncellemede yeniden başlatılacak
        }
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
            if (!isSoundLoaded) {
                Log.e(TAG, "Ses henüz yüklenmedi!")
                showError("Motor sesi hazır değil!")
                return
            }

            // Başlangıç değerlerini sıfırla
            lastRMS = 0.0f
            currentVolume = MIN_VOLUME
            currentRate = MIN_RATE
            targetVolume = MIN_VOLUME
            targetRate = MIN_RATE

            if (audioRecord == null) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            // Motor sesini başlat
            startEngineSound()

            isRecording = true
            binding.btnStartStop.text = getString(R.string.stop)

            // Mikrofon okuma thread'ini başlat
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
                        Thread.sleep(20) // Örnekleme hızını artır
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
                
                // Ses çalmayı durdur
                if (currentStreamId != 0) {
                    soundPool?.stop(currentStreamId)
                    currentStreamId = 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord durdurma hatası: ${e.message}")
            }
            
            // Motor sesini rölantiye al
            targetVolume = MIN_VOLUME
            targetRate = MIN_RATE
            currentVolume = MIN_VOLUME
            currentRate = MIN_RATE
            
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
        try {
            // RMS değerini normalize et ve hassasiyet çarpanını uygula
            var normalizedRMS = (rms / 32768.0 * MIC_SENSITIVITY).coerceIn(0.0, 1.0)
            
            // Gürültü eşiğini uygula
            normalizedRMS = if (normalizedRMS < NOISE_THRESHOLD) {
                0.0
            } else {
                // Gürültü eşiği üzerindeki değerleri yeniden ölçeklendir
                ((normalizedRMS - NOISE_THRESHOLD) / (1 - NOISE_THRESHOLD)).coerceIn(0.0, 1.0)
            }
            
            // RMS değerini yumuşat
            val smoothedRMS = (RMS_SMOOTHING * lastRMS + (1 - RMS_SMOOTHING) * normalizedRMS).toFloat()
            lastRMS = smoothedRMS
            
            // Ses şiddetini hesapla (daha hassas tepki için üstel değeri düşürdük)
            val volumeFactor = Math.pow(smoothedRMS.toDouble(), 1.2).toFloat()
            
            // RPM faktörünü hesapla (daha hassas RPM artışı için üstel değeri düşürdük)
            val rpmFactor = Math.pow(smoothedRMS.toDouble(), 1.3).toFloat()
            
            // Hedef ses seviyesini ayarla
            targetVolume = (MIN_VOLUME + (volumeFactor * VOLUME_RANGE)).coerceIn(MIN_VOLUME, MAX_VOLUME)
            
            // Hedef RPM'i (playback rate) ayarla
            targetRate = (MIN_RATE + (rpmFactor * RATE_RANGE)).coerceIn(MIN_RATE, MAX_RATE)
            
            // Debug log
            Log.d(TAG, "RMS: $normalizedRMS, Smoothed: $smoothedRMS, Target Volume: $targetVolume, Target Rate: $targetRate")
        } catch (e: Exception) {
            Log.e(TAG, "Mikrofon güncelleme hatası: ${e.message}")
        }
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