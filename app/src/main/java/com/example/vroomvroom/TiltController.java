package com.example.vroomvroom;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.View;

/**
 * Accéléromètre => valeur "volant" simple (dossier §3a).
 *   getSteer() > 0 : téléphone incliné vers la DROITE
 *   getSteer() < 0 : vers la GAUCHE
 * Unité : m/s² (≈ 9.81 si le téléphone est complètement couché sur le côté).
 *
 * Gestion du bruit (dossier §6e) : lissage + calibration du "zéro" au départ.
 * La zone morte et le "une voie à la fois" sont dans GameView (handleTilt).
 */
public class TiltController implements SensorEventListener {

    /** Lissage passe-bas : 0 = ne bouge jamais, 1 = aucun lissage. 0.25 = on filtre les tremblements. */
    private static final float SMOOTHING = 0.25f;
    /** En dessous de cette inclinaison, le téléphone est considéré "tenu droit" (≈ 15°). */
    private static final float STRAIGHT_TOLERANCE = 2.5f;
    /** z très négatif = écran tourné vers le sol (mode secret). */
    private static final float FACE_DOWN_Z = -7.5f;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final View view; // sert à savoir dans quel sens l'écran est tourné

    private float steer;          // valeur lissée
    private float z = 9.81f;      // axe perpendiculaire à l'écran, lissé
    private float neutral;        // "zéro" mesuré au début de la partie

    public TiltController(Context context, View view) {
        this.view = view;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
    }

    public boolean isAvailable() { return accelerometer != null; }

    /** À appeler dans onResume : sans ça, aucun événement capteur. */
    public void start() {
        if (accelerometer != null) {
            // SENSOR_DELAY_GAME : fréquence adaptée aux jeux (~50 Hz)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /** À appeler dans onPause : un capteur actif en arrière-plan vide la batterie. */
    public void stop() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float ax = event.values[0];
        float ay = event.values[1];

        // Les axes du capteur sont ceux du téléphone EN PORTRAIT. On joue en paysage (dans un sens
        // ou dans l'autre), donc on convertit vers l'axe horizontal de l'ÉCRAN.
        // (même conversion que l'exemple officiel Android "AccelerometerPlay")
        int rotation = Surface.ROTATION_90;
        Display display = view.getDisplay();
        if (display != null) rotation = display.getRotation();
        float screenX;
        switch (rotation) {
            case Surface.ROTATION_0:   screenX = ax;  break;
            case Surface.ROTATION_180: screenX = -ax; break;
            case Surface.ROTATION_270: screenX = ay;  break;
            case Surface.ROTATION_90:
            default:                   screenX = -ay; break;
        }
        float target = -screenX; // on veut + = incliné à droite

        // Filtre passe-bas : on ne prend qu'une partie de la nouvelle valeur => mouvements doux
        steer += SMOOTHING * (target - steer);
        z += SMOOTHING * (event.values[2] - z);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /** Inclinaison par rapport à la position de départ du joueur. */
    public float getSteer() { return steer - neutral; }

    /** Utilisé au compte à rebours : "la partie ne démarre que si le téléphone est tenu droit". */
    public boolean isHeldStraight() { return Math.abs(steer) < STRAIGHT_TOLERANCE; }

    /** Mémorise la position actuelle comme "tout droit" (le joueur ne tient jamais parfaitement à plat). */
    public void calibrateNeutral() { neutral = steer; }

    public void resetNeutral() { neutral = 0; }

    public boolean isFaceDown() { return z < FACE_DOWN_Z; }
}
