package com.example.vroomvroom;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.View;

// Accéléromètre => direction lissée en m/s² (> 0 = incliné à droite).
public class TiltController implements SensorEventListener {
    private static final float SMOOTHING = 0.25f;
    private static final float STRAIGHT_TOLERANCE = 2.5f;
    private static final float UPSIDE_DOWN_Y = -5f;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final View view;

    private float steer;
    private float screenY = 9.81f;
    private float neutral;

    // Récupère l'accéléromètre.
    public TiltController(Context context, View view) {
        this.view = view;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
    }

    // Accéléromètre présent ?
    public boolean isAvailable() { return accelerometer != null; }

    // Démarre l'écoute du capteur.
    public void start() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    // Arrête l'écoute du capteur.
    public void stop() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    // Nouvelle mesure : conversion en direction lissée.
    @Override
    public void onSensorChanged(SensorEvent event) {
        float ax = event.values[0];
        float ay = event.values[1];

        // Axes du capteur = portrait : conversion vers l'axe horizontal de l'écran en paysage.
        int rotation = Surface.ROTATION_90;
        Display display = view.getDisplay();
        if (display != null) rotation = display.getRotation();
        float screenX, sy;
        switch (rotation) {
            case Surface.ROTATION_0:   screenX = ax;  sy = ay;  break;
            case Surface.ROTATION_180: screenX = -ax; sy = -ay; break;
            case Surface.ROTATION_270: screenX = ay;  sy = -ax; break;
            case Surface.ROTATION_90:
            default:                   screenX = -ay; sy = ax;  break;
        }
        float target = -screenX;

        steer += SMOOTHING * (target - steer);
        screenY += SMOOTHING * (sy - screenY);
    }

    // Inutilisé.
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // Inclinaison par rapport au zéro.
    public float getSteer() { return steer - neutral; }

    // Téléphone tenu droit ?
    public boolean isHeldStraight() { return Math.abs(steer) < STRAIGHT_TOLERANCE; }

    // Position actuelle = tout droit.
    public void calibrateNeutral() { neutral = steer; }

    // Remet le zéro à plat.
    public void resetNeutral() { neutral = 0; }

    // Téléphone retourné ?
    public boolean isUpsideDown() { return screenY < UPSIDE_DOWN_Y; }
}
