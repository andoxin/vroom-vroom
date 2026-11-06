package com.example.vroomvroom;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;

// Détection du souffle : seuil = bruit ambiant (mesuré pendant le 3-2-1) × facteur de sensibilité.
public class BreathDetector {
    private static final int SAMPLE_RATE = 44100;

    private Thread thread;
    private volatile boolean running;
    private volatile float level;
    private volatile float ambient = 200f;
    private volatile int sensitivity = 50;

    private final Object calibLock = new Object();
    private boolean calibrating;
    private double calibSum;
    private int calibCount;
    private float calibPeak;

    private AudioEffect aec, agc, ns;

    // Permission micro accordée ?
    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // Démarre l'écoute du micro (false si impossible).
    @SuppressLint("MissingPermission")
    public synchronized boolean start(Context context) {
        if (running) return true;
        if (!hasPermission(context)) return false;

        final int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) return false;

        // VOICE_COMMUNICATION active l'annulation d'écho (retire la musique du haut-parleur) ; sinon micro brut.
        AudioRecord rec = openRecorder(MediaRecorder.AudioSource.VOICE_COMMUNICATION, minBuf);
        if (rec == null) rec = openRecorder(MediaRecorder.AudioSource.MIC, minBuf);
        if (rec == null) return false;
        attachEffects(rec.getAudioSessionId());

        final AudioRecord recorder = rec;
        running = true;
        level = 0;
        thread = new Thread(() -> readLoop(recorder, minBuf / 2), "BreathDetector");
        thread.start();
        return true;
    }

    // Ouvre le micro sur une source donnée.
    private static AudioRecord openRecorder(int source, int minBuf) {
        AudioRecord rec = null;
        try {
            rec = new AudioRecord(source, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                return null;
            }
            rec.startRecording();
            return rec;
        } catch (Exception e) {
            if (rec != null) rec.release();
            return null;
        }
    }

    // Active l'annulation d'écho, coupe réduction de bruit et gain auto.
    private void attachEffects(int session) {
        releaseEffects();
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(session);
                if (aec != null) aec.setEnabled(true);
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(session);
                if (ns != null) ns.setEnabled(false);
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(session);
                if (agc != null) agc.setEnabled(false);
            }
        } catch (RuntimeException ignored) {
        }
    }

    // Libère les effets audio.
    private void releaseEffects() {
        if (aec != null) { aec.release(); aec = null; }
        if (ns != null)  { ns.release();  ns = null; }
        if (agc != null) { agc.release(); agc = null; }
    }

    // Lit le micro en continu et calcule le volume.
    private void readLoop(AudioRecord rec, int size) {
        short[] buffer = new short[Math.max(size, 256)];
        try {
            while (running) {
                int n = rec.read(buffer, 0, buffer.length);
                if (n < 0) break;
                if (n == 0) continue;
                double sum = 0;
                for (int i = 0; i < n; i++) sum += (double) buffer[i] * buffer[i];
                float rms = (float) Math.sqrt(sum / n);
                level = level * 0.5f + rms * 0.5f;
                synchronized (calibLock) {
                    if (calibrating) {
                        calibSum += rms; calibCount++;
                        if (level > calibPeak) calibPeak = level;
                    }
                }
            }
        } finally {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();  // libéré par le thread, une fois sorti de read() (évite un crash)
            releaseEffects();
        }
    }

    // Arrête l'écoute.
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(300); } catch (InterruptedException ignored) { }
            thread = null;
        }
        level = 0;
    }

    // Écoute en cours ?
    public boolean isRunning() { return running; }

    // Commence la mesure du bruit ambiant.
    public void startCalibration() {
        synchronized (calibLock) { calibrating = true; calibSum = 0; calibCount = 0; calibPeak = 0; }
    }

    // Termine la mesure et fixe le bruit ambiant.
    public void endCalibration() {
        synchronized (calibLock) {
            calibrating = false;
            if (calibCount > 0) {
                float mean = (float) (calibSum / calibCount);
                // On tient compte des pics de la musique, pas seulement de la moyenne.
                ambient = Math.max(60f, Math.max(mean, calibPeak * 0.8f));
            }
        }
    }

    // Règle la sensibilité (0-100).
    public void setSensitivity(int s) { sensitivity = s; }

    // Seuil de détection du souffle.
    public float getThreshold() {
        float factor = 4f - 2.5f * sensitivity / 100f;
        float minAbs = 1500f - 10f * sensitivity;
        return Math.max(ambient * factor, minAbs);
    }

    // Volume / seuil (>= 1 = souffle).
    public float getRatio() { return running ? level / getThreshold() : 0f; }

    // Souffle détecté ?
    public boolean isBlowing() { return getRatio() >= 1f; }
}
