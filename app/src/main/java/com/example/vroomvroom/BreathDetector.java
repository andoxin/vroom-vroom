package com.example.vroomvroom;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Micro => détection du souffle (dossier §3b).
 * Souffler dans un micro = gros bruit continu => le VOLUME (RMS) monte fort.
 *
 * Gestion du bruit (dossier §6e) :
 *  - calibration : on mesure le bruit ambiant pendant le 3-2-1 ;
 *  - seuil = bruit ambiant × facteur (le facteur dépend de la sensibilité réglée par le joueur) ;
 *  - le "souffle maintenu" et le temps de recharge sont gérés dans GameView / GameWorld.
 *
 * La lecture du micro bloque : elle tourne donc dans un Thread à part (sinon le jeu gèlerait).
 */
public class BreathDetector {

    private static final int SAMPLE_RATE = 44100; // fréquence d'échantillonnage supportée partout

    private Thread thread;
    private volatile boolean running;   // volatile : variable lue par 2 threads différents
    private volatile float level;       // volume actuel (RMS lissé, de 0 à ~32768)
    private volatile float ambient = 200f;
    private volatile int sensitivity = 50;

    private final Object calibLock = new Object();
    private boolean calibrating;
    private double calibSum;
    private int calibCount;

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Démarre l'écoute. Renvoie false si impossible (permission refusée, micro occupé...). */
    @SuppressLint("MissingPermission") // la permission est vérifiée juste en dessous
    public synchronized boolean start(Context context) {
        if (running) return true;
        if (!hasPermission(context)) return false;

        final int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) return false;

        AudioRecord rec = null;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                return false;
            }
            rec.startRecording();
        } catch (Exception e) {
            if (rec != null) rec.release();
            return false; // cas limite : on ne plante pas, le jeu continue sans boost
        }

        final AudioRecord recorder = rec;
        running = true;
        level = 0;
        thread = new Thread(() -> readLoop(recorder, minBuf / 2), "BreathDetector");
        thread.start();
        return true;
    }

    /** Boucle du thread : lit le micro en continu et calcule le volume. */
    private void readLoop(AudioRecord rec, int size) {
        short[] buffer = new short[Math.max(size, 256)];
        try {
            while (running) {
                int n = rec.read(buffer, 0, buffer.length);
                if (n < 0) break;     // erreur micro
                if (n == 0) continue;
                // RMS = racine de la moyenne des carrés = "volume moyen" du morceau de son
                double sum = 0;
                for (int i = 0; i < n; i++) sum += (double) buffer[i] * buffer[i];
                float rms = (float) Math.sqrt(sum / n);
                level = level * 0.5f + rms * 0.5f; // petit lissage
                synchronized (calibLock) {
                    if (calibrating) { calibSum += rms; calibCount++; }
                }
            }
        } finally {
            // C'est le thread qui libère le micro, une fois sorti de read() (évite un crash)
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
        }
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(300); } catch (InterruptedException ignored) { }
            thread = null;
        }
        level = 0;
    }

    public boolean isRunning() { return running; }

    // ---------- Calibration (pendant le 3-2-1) ----------
    public void startCalibration() {
        synchronized (calibLock) { calibrating = true; calibSum = 0; calibCount = 0; }
    }

    public void endCalibration() {
        synchronized (calibLock) {
            calibrating = false;
            if (calibCount > 0) ambient = Math.max(60f, (float) (calibSum / calibCount));
        }
    }

    // ---------- Seuil ----------
    /** 0 = peu sensible (il faut souffler fort), 100 = très sensible. */
    public void setSensitivity(int s) { sensitivity = s; }

    /** Seuil = bruit ambiant × facteur, avec un minimum absolu pour les pièces très calmes. */
    public float getThreshold() {
        float factor = 4f - 2.5f * sensitivity / 100f;  // 4.0 (sens 0) -> 1.5 (sens 100)
        float minAbs = 1500f - 10f * sensitivity;       // 1500 -> 500
        return Math.max(ambient * factor, minAbs);
    }

    /** Volume actuel / seuil : >= 1 veut dire "souffle détecté". Sert aussi à remplir la jauge. */
    public float getRatio() { return running ? level / getThreshold() : 0f; }

    public boolean isBlowing() { return getRatio() >= 1f; }
}
