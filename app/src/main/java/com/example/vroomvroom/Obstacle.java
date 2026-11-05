package com.example.vroomvroom;

/** Un obstacle posé sur la route, sur UNE voie. */
public class Obstacle {
    public final float worldZ; // position sur la route en mètres (depuis le départ)
    public final int lane;     // 0 = gauche, 1 = centre, 2 = droite
    public final int kind;     // 0 = bloc, 1 = pyramide (juste visuel, pour varier)
    public boolean hit;        // déjà percuté => ne fait plus perdre de vie

    public Obstacle(float worldZ, int lane, int kind) {
        this.worldZ = worldZ;
        this.lane = lane;
        this.kind = kind;
    }
}
