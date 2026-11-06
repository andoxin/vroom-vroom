package com.example.vroomvroom;

public class Obstacle {
    public final float worldZ;
    public final int lane;  // 0 = gauche, 1 = centre, 2 = droite
    public final int kind;  // 0 = bloc, 1 = pyramide
    public boolean hit;

    // Crée un obstacle.
    public Obstacle(float worldZ, int lane, int kind) {
        this.worldZ = worldZ;
        this.lane = lane;
        this.kind = kind;
    }
}
