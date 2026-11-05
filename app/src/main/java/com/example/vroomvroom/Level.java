package com.example.vroomvroom;

/**
 * Un niveau = une vitesse de départ + UN set d'obstacles (= 1 cycle).
 * Dossier §1 : "1 cycle = 1 set d'obstacles. Pour chaque nouveau cycle, speed up avec le même set."
 * => Le set est rejoué en boucle, de plus en plus vite (géré dans GameWorld).
 */
public class Level {

    // Voies codées en "masque de bits" : on peut bloquer plusieurs voies sur une même ligne.
    static final int L = 1;  // voie gauche   (bit 0)
    static final int C = 2;  // voie centre   (bit 1)
    static final int R = 4;  // voie droite   (bit 2)

    public final int number;
    public final String name;
    public final float baseSpeed;    // vitesse de départ en m/s
    public final float cycleLength;  // longueur d'un cycle en mètres
    public final float[] obstacleDist; // position de chaque ligne d'obstacles dans le cycle (m)
    public final int[] obstacleLanes;  // voies bloquées pour chaque ligne (masque L|C|R)

    private Level(int number, String name, float baseSpeed, float cycleLength, float[] dist, int[] lanes) {
        this.number = number;
        this.name = name;
        this.baseSpeed = baseSpeed;
        this.cycleLength = cycleLength;
        this.obstacleDist = dist;
        this.obstacleLanes = lanes;
    }

    public static Level get(int number) {
        return number == 2 ? level2() : level1();
    }

    /** Niveau 1 : surtout des obstacles simples, bien espacés. */
    private static Level level1() {
        return new Level(1, "FACILE", 20f, 420f,
                new float[]{40, 80, 115, 150, 185, 220, 255, 290, 320, 350, 385},
                new int[]  {L,  R,  C,   L|C, R,   C|R, L,   R,   L|C, C|R, L|R});
    }

    /** Niveau 2 : "set d'obstacles plus dur" : plus rapide, plus serré, surtout des doubles. */
    private static Level level2() {
        return new Level(2, "DIFFICILE", 25f, 420f,
                new float[]{35,  62, 88,  112, 138, 162, 188, 212, 238, 262, 288, 312, 338, 362, 390},
                new int[]  {L|C, R,  C|R, L,   L|R, C,   L|C, C|R, L|R, C,   L|C, R|C, L,   L|R, C|R});
    }
}
