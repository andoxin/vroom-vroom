package com.example.vroomvroom;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sauvegarde locale (SharedPreferences = petit fichier clé/valeur propre à l'appli) :
 * paramètres, initiales, tuto déjà fait, classement des 2 niveaux.
 */
public class SaveManager {

    public static final int MAX_SCORES = 5; // top 5 par niveau

    /** Une ligne du classement. */
    public static class ScoreEntry {
        public final String initials;
        public final int stars;     // 1 étoile = 1 cycle terminé (cf. sketch écran de fin)
        public final int score;
        public final int distance;

        public ScoreEntry(String initials, int stars, int score, int distance) {
            this.initials = initials;
            this.stars = stars;
            this.score = score;
            this.distance = distance;
        }
    }

    private final SharedPreferences prefs;

    public SaveManager(Context context) {
        prefs = context.getSharedPreferences("vapor_rush", Context.MODE_PRIVATE);
    }

    // ---------- Paramètres (dossier §2b) ----------
    public int getSoundVolume() { return prefs.getInt("sound", 80); }
    public void setSoundVolume(int v) { prefs.edit().putInt("sound", v).apply(); }

    public int getMusicVolume() { return prefs.getInt("music", 70); }
    public void setMusicVolume(int v) { prefs.edit().putInt("music", v).apply(); }

    public boolean isVibrationOn() { return prefs.getBoolean("vibration", true); }
    public void setVibrationOn(boolean on) { prefs.edit().putBoolean("vibration", on).apply(); }

    public int getMicSensitivity() { return prefs.getInt("mic_sens", 50); }
    public void setMicSensitivity(int v) { prefs.edit().putInt("mic_sens", v).apply(); }

    // ---------- Divers ----------
    /** null tant que le joueur n'a jamais saisi ses initiales. */
    public String getInitials() { return prefs.getString("initials", null); }
    public void setInitials(String s) { prefs.edit().putString("initials", s).apply(); }

    public boolean isTutoDone() { return prefs.getBoolean("tuto_done", false); }
    public void setTutoDone(boolean done) { prefs.edit().putBoolean("tuto_done", done).apply(); }

    public boolean isMicAsked() { return prefs.getBoolean("mic_asked", false); }
    public void setMicAsked(boolean asked) { prefs.edit().putBoolean("mic_asked", asked).apply(); }

    // ---------- Classement ----------
    // Stocké en texte : "ABC|2|1534|1200;THO|1|900|850"  (initiales|étoiles|score|distance)

    public List<ScoreEntry> getScores(int level) {
        List<ScoreEntry> list = new ArrayList<>();
        String raw = prefs.getString("scores_" + level, "");
        if (raw.isEmpty()) return list;
        for (String part : raw.split(";")) {
            String[] f = part.split("\\|");       // "|" est un caractère spécial en regex => on l'échappe
            if (f.length != 4) continue;           // ligne abîmée : on l'ignore au lieu de planter
            try {
                list.add(new ScoreEntry(f[0], Integer.parseInt(f[1]),
                        Integer.parseInt(f[2]), Integer.parseInt(f[3])));
            } catch (NumberFormatException ignored) { }
        }
        return list;
    }

    /** Le score entre-t-il dans le top 5 ? */
    public boolean isHighScore(int level, int score) {
        if (score <= 0) return false;
        List<ScoreEntry> list = getScores(level);
        return list.size() < MAX_SCORES || score > list.get(list.size() - 1).score;
    }

    public int getBestScore(int level) {
        List<ScoreEntry> list = getScores(level);
        return list.isEmpty() ? 0 : list.get(0).score;
    }

    public void addScore(int level, ScoreEntry entry) {
        List<ScoreEntry> list = getScores(level);
        list.add(entry);
        Collections.sort(list, (a, b) -> Integer.compare(b.score, a.score)); // tri décroissant
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
