package com.example.vroomvroom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Une seule Activity : tous les écrans (menu, jeu, pause...) sont des ÉTATS de GameView
 * (cf. sujet §4.7 : "changer d'écran ne signifie pas nécessairement changer d'Activity").
 * L'activité s'occupe seulement : de la permission micro, du plein écran et du cycle de vie.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_MIC = 42; // numéro arbitraire pour reconnaître notre demande
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // L'écran ne s'éteint pas pendant qu'on joue (on ne touche jamais l'écran en jeu !)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        gameView = new GameView(this);
        setContentView(gameView);
        hideSystemUi();

        // Dossier §2b "Autoriser le micro (premier lancement)" : on ne demande qu'une fois.
        SaveManager save = gameView.getSave();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                && !save.isMicAsked()) {
            save.setMicAsked(true);
            gameView.setWaitingPermission(true); // le tuto attend la réponse avant de démarrer
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MIC) {
            // Accepté ou refusé, on continue : si refusé, le jeu reste jouable sans boost (dossier §6e).
            gameView.setWaitingPermission(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        gameView.resume();   // relance boucle + capteurs
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();    // met la partie en pause, coupe boucle + capteurs + micro (batterie)
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gameView.release();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi(); // après le clavier/la boîte de dialogue, on repasse en plein écran
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Le bouton retour d'Android est géré par le jeu (ex : en partie => pause)
        if (!gameView.onBack()) super.onBackPressed();
    }

    /** Plein écran "immersif" : cache barre d'état et barre de navigation. */
    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
