package com.example.vroomvroom;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Sons, musique et vibrations
 *  - Bruitages : ToneGenerator (bips du système) => marche sans aucun fichier son.
 *  - Musique : fichiers OPTIONNELS dans res/raw : music_menu, music_game, music_secret
 *    (mp3 ou ogg). S'ils n'existent pas, le jeu tourne simplement sans musique.
 */
public class AudioFx {

    private final Context context;
    private final SaveManager save;
    private final Vibrator vibrator;
    private ToneGenerator tone;
    private MediaPlayer music;
    private String track;

    @SuppressWarnings("deprecation")
    public AudioFx(Context context, SaveManager save) {
        this.context = context;
        this.save = save;
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        applySettings();
    }

    public void applySettings() {
        if (tone != null) { tone.release(); tone = null; }
        int vol = save.getSoundVolume();
        if (vol > 0) {
            try {
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, vol);
            } catch (RuntimeException e) {
                tone = null; // certains appareils refusent : on joue sans bruitages
            }
        }
        applyMusicVolume();
    }

    public void applyMusicVolume() {
        if (music != null) {
            float v = save.getMusicVolume() / 100f;
            music.setVolume(v, v);
        }
    }

    private void beep(int type, int ms) {
        if (tone == null) return;
        try { tone.startTone(type, ms); } catch (RuntimeException ignored) { }
    }

    // ---------- Bruitages ----------
    public void click()     { beep(ToneGenerator.TONE_PROP_BEEP, 40); }
    public void countdown() { beep(ToneGenerator.TONE_PROP_BEEP, 120); }
    public void go()        { beep(ToneGenerator.TONE_PROP_BEEP2, 250); }
    public void speedUp()   { beep(ToneGenerator.TONE_PROP_ACK, 300); }
    public void success()   { beep(ToneGenerator.TONE_PROP_ACK, 150); vibrate(50); }
    public void boost()     { beep(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 300); vibrate(40); }
    /** Collision : "vibration courte + son" (le flash rouge est dessiné par GameWorld). */
    public void collision() { beep(ToneGenerator.TONE_SUP_ERROR, 250); vibrate(180); }
    public void gameOver()  { beep(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 600); vibrate(400); }

    // ---------- Vibrations ----------
    @SuppressWarnings("deprecation")
    public void vibrate(int ms) {
        if (!save.isVibrationOn() || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (RuntimeException ignored) {
            // ex : SecurityException si la permission VIBRATE manque => on joue sans vibration
        }
    }

    // ---------- Musique ----------
    public void playMusic(String name) {
        if (name.equals(track)) return; // déjà en train de jouer ce morceau
        stopMusic();
        track = name;
        // getIdentifier renvoie 0 si le fichier res/raw/<name> n'existe pas => pas de crash
        int id = context.getResources().getIdentifier(name, "raw", context.getPackageName());
        if (id == 0) return;
        music = MediaPlayer.create(context, id);
        if (music == null) return;
        music.setLooping(true);
        applyMusicVolume();
        music.start();
    }

    public void stopMusic() {
        if (music != null) { music.release(); music = null; }
        track = null;
    }

    public void pauseMusic() {
        if (music != null && music.isPlaying()) music.pause();
    }

    public void resumeMusic() {
        if (music != null && !music.isPlaying()) music.start();
    }

    public void release() {
        stopMusic();
        if (tone != null) { tone.release(); tone = null; }
    }
}
