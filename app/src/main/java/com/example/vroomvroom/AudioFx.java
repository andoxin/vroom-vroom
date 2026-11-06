package com.example.vroomvroom;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.IOException;

// Sons (ToneGenerator), musique en boucle (assets/music) et vibrations.
public class AudioFx {
    private final Context context;
    private final SaveManager save;
    private final Vibrator vibrator;
    private ToneGenerator tone;
    private MediaPlayer music;
    private String track;

    // Initialise vibreur et sons.
    @SuppressWarnings("deprecation")
    public AudioFx(Context context, SaveManager save) {
        this.context = context;
        this.save = save;
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        applySettings();
    }

    // Applique les volumes enregistrés.
    public void applySettings() {
        if (tone != null) { tone.release(); tone = null; }
        int vol = save.getSoundVolume();
        if (vol > 0) {
            try {
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, vol);
            } catch (RuntimeException e) {
                tone = null;
            }
        }
        applyMusicVolume();
    }

    // Met à jour le volume de la musique.
    public void applyMusicVolume() {
        if (music != null) {
            float v = save.getMusicVolume() / 100f;
            music.setVolume(v, v);
        }
    }

    // Joue un bip système.
    private void beep(int type, int ms) {
        if (tone == null) return;
        try { tone.startTone(type, ms); } catch (RuntimeException ignored) { }
    }

    // Son de clic.
    public void click()     { beep(ToneGenerator.TONE_PROP_BEEP, 40); }
    // Bip du 3-2-1.
    public void countdown() { beep(ToneGenerator.TONE_PROP_BEEP, 120); }
    // Bip du GO.
    public void go()        { beep(ToneGenerator.TONE_PROP_BEEP2, 250); }
    // Son du speed up.
    public void speedUp()   { beep(ToneGenerator.TONE_PROP_ACK, 300); }
    // Son + vibration de réussite.
    public void success()   { beep(ToneGenerator.TONE_PROP_ACK, 150); vibrate(50); }
    // Son + vibration du boost.
    public void boost()     { beep(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 300); vibrate(40); }
    // Son + vibration de collision.
    public void collision() { beep(ToneGenerator.TONE_SUP_ERROR, 250); vibrate(180); }
    // Son + vibration de fin de partie.
    public void gameOver()  { beep(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 600); vibrate(400); }

    // Fait vibrer si l'option est activée.
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
        }
    }

    // Nom de piste -> fichier audio.
    private static String musicFile(String name) {
        switch (name) {
            case "music_menu":   return "music/vroomvroom-bgm.m4a";
            case "music_game":
            case "music_secret": return "music/vroomvroom-game.m4a";
            default:             return null;
        }
    }

    // Lance une musique en boucle.
    public void playMusic(String name) {
        String file = musicFile(name);
        if (file != null && file.equals(track)) return;
        stopMusic();
        if (file == null) return;
        track = file;
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor afd = context.getAssets().openFd(file)) {
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mp.setLooping(true);
            mp.prepare();
        } catch (IOException | RuntimeException e) {
            mp.release();  // fichier absent ou illisible => pas de musique
            return;
        }
        music = mp;
        applyMusicVolume();
        music.start();
    }

    // Arrête la musique.
    public void stopMusic() {
        if (music != null) { music.release(); music = null; }
        track = null;
    }

    // Met la musique en pause.
    public void pauseMusic() {
        if (music != null && music.isPlaying()) music.pause();
    }

    // Reprend la musique.
    public void resumeMusic() {
        if (music != null && !music.isPlaying()) music.start();
    }

    // Libère toutes les ressources audio.
    public void release() {
        stopMusic();
        if (tone != null) { tone.release(); tone = null; }
    }
}
