package com.example.vroomvroom;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Sauvegarde locale (SharedPreferences) : paramètres, initiales, tuto, classements.
public class SaveManager {
    public static final int MAX_SCORES = 5;

    public static class ScoreEntry {
        public final String initials;
        public final int stars;
        public final int score;
        public final int distance;

        // Crée une ligne du classement.
        public ScoreEntry(String initials, int stars, int score, int distance) {
            this.initials = initials;
            this.stars = stars;
            this.score = score;
            this.distance = distance;
        }
    }

    private final SharedPreferences prefs;

    // Ouvre la sauvegarde.
    public SaveManager(Context context) {
        prefs = context.getSharedPreferences("vapor_rush", Context.MODE_PRIVATE);
    }

    // Volume des sons.
    public int getSoundVolume() { return prefs.getInt("sound", 80); }
    // Enregistre le volume des sons.
    public void setSoundVolume(int v) { prefs.edit().putInt("sound", v).apply(); }

    // Volume de la musique.
    public int getMusicVolume() { return prefs.getInt("music", 70); }
    // Enregistre le volume de la musique.
    public void setMusicVolume(int v) { prefs.edit().putInt("music", v).apply(); }

    // Vibrations activées ?
    public boolean isVibrationOn() { return prefs.getBoolean("vibration", true); }
    // Active/désactive les vibrations.
    public void setVibrationOn(boolean on) { prefs.edit().putBoolean("vibration", on).apply(); }

    // Sensibilité du micro.
    public int getMicSensitivity() { return prefs.getInt("mic_sens", 50); }
    // Enregistre la sensibilité du micro.
    public void setMicSensitivity(int v) { prefs.edit().putInt("mic_sens", v).apply(); }

    // Initiales (null si jamais saisies).
    public String getInitials() { return prefs.getString("initials", null); }
    // Enregistre les initiales.
    public void setInitials(String s) { prefs.edit().putString("initials", s).apply(); }

    // Tuto déjà fait ?
    public boolean isTutoDone() { return prefs.getBoolean("tuto_done", false); }
    // Marque le tuto comme fait.
    public void setTutoDone(boolean done) { prefs.edit().putBoolean("tuto_done", done).apply(); }

    // Micro déjà demandé ?
    public boolean isMicAsked() { return prefs.getBoolean("mic_asked", false); }
    // Marque le micro comme demandé.
    public void setMicAsked(boolean asked) { prefs.edit().putBoolean("mic_asked", asked).apply(); }

    // Classement d'un niveau.
    public List<ScoreEntry> getScores(int level) {
        List<ScoreEntry> list = new ArrayList<>();
        String raw = prefs.getString("scores_" + level, "");
        if (raw.isEmpty()) return list;
        // Format : "ABC|2|1534|1200;..." (initiales|étoiles|score|distance)
        for (String part : raw.split(";")) {
            String[] f = part.split("\\|");
            if (f.length != 4) continue;
            try {
                list.add(new ScoreEntry(f[0], Integer.parseInt(f[1]),
                        Integer.parseInt(f[2]), Integer.parseInt(f[3])));
            } catch (NumberFormatException ignored) { }
        }
        return list;
    }

    // Le score entre-t-il dans le top 5 ?
    public boolean isHighScore(int level, int score) {
        if (score <= 0) return false;
        List<ScoreEntry> list = getScores(level);
        return list.size() < MAX_SCORES || score > list.get(list.size() - 1).score;
    }

    // Meilleur score d'un niveau.
    public int getBestScore(int level) {
        List<ScoreEntry> list = getScores(level);
        return list.isEmpty() ? 0 : list.get(0).score;
    }

    // Ajoute un score et garde le top 5.
    public void addScore(int level, ScoreEntry entry) {
        List<ScoreEntry> list = getScores(level);
        list.add(entry);
        Collections.sort(list, (a, b) -> Integer.compare(b.score, a.score));
        while (list.size() > MAX_SCORES) list.remove(list.size() - 1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            ScoreEntry e = list.get(i);
            if (i > 0) sb.append(';');
            sb.append(e.initials).append('|').append(e.stars).append('|')
              .append(e.score).append('|').append(e.distance);
        }
        prefs.edit().putString("scores_" + level, sb.toString()).apply();
    }
}
