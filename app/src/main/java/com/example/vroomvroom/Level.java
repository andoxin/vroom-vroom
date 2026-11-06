package com.example.vroomvroom;

// Un niveau = vitesse de départ + un set d'obstacles rejoué en boucle, de plus en plus vite.
public class Level {
    // Voies en masque de bits : plusieurs voies bloquées possibles sur une ligne.
    static final int L = 1;
    static final int C = 2;
    static final int R = 4;

    public final int number;
    public final String name;
    public final float baseSpeed;
    public final float cycleLength;
    public final float[] obstacleDist;
    public final int[] obstacleLanes;

    // Crée un niveau.
    private Level(int number, String name, float baseSpeed, float cycleLength, float[] dist, int[] lanes) {
        this.number = number;
        this.name = name;
        this.baseSpeed = baseSpeed;
        this.cycleLength = cycleLength;
        this.obstacleDist = dist;
        this.obstacleLanes = lanes;
    }

    // Renvoie le niveau demandé.
    public static Level get(int number) {
        return number == 2 ? level2() : level1();
    }

    // Niveau 1 : obstacles simples, espacés.
    private static Level level1() {
        return new Level(1, "FACILE", 20f, 420f,
                new float[]{40, 80, 115, 150, 185, 220, 255, 290, 320, 350, 385},
                new int[]  {L,  R,  C,   L|C, R,   C|R, L,   R,   L|C, C|R, L|R});
    }

    // Niveau 2 : plus rapide et plus serré.
    private static Level level2() {
        return new Level(2, "DIFFICILE", 25f, 420f,
                new float[]{35,  62, 88,  112, 138, 162, 188, 212, 238, 262, 288, 312, 338, 362, 390},
                new int[]  {L|C, R,  C|R, L,   L|R, C,   L|C, C|R, L|R, C,   L|C, R|C, L,   L|R, C|R});
    }
}
