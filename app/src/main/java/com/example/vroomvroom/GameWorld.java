package com.example.vroomvroom;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Random;

// La course : état de la partie (update) et dessin de la scène (drawScene).
public class GameWorld {
    public static final int LANES = 3;
    public static final int MAX_LIVES = 3;
    public static final float SPEED_UP_FACTOR = 1.15f;  // +15 % de vitesse à chaque cycle
    public static final float MAX_SPEED_FACTOR = 3f;
    public static final float BOOST_DURATION = 2.5f;
    public static final float BOOST_COOLDOWN = 4f;
    public static final float BOOST_MULT = 1.6f;
    public static final float INVINCIBLE_TIME = 1.2f;  // après un choc, on ne perd pas 2 vies d'un coup

    public static final float VIEW_DIST = 140f;
    private static final float K = 14f;
    private static final float HIT_RANGE = 1.6f;
    private static final float GRID_STEP = 8f;

    public static final int EV_HIT = 1, EV_SPEED_UP = 2, EV_DEAD = 4;

    public Level level;
    public int lane;
    public float carLaneX;
    public float lean;
    public float distance;
    public float speed;
    public float bonus;
    public int lives;
    public int cycle;
    public float boostTime, cooldownTime, invincibleTime;
    public float hitFlash, speedUpTime, heartAnimTime;
    public int lostHeart = -1;
    public final ArrayList<Obstacle> obstacles = new ArrayList<>();
    private int spawnCycle, spawnIndex;

    private int w, h;
    private float horizon, carY, cx, laneW, sunR, sunCy, relMin, pBottom;
    private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundPaint = new Paint();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] starX = new float[40], starY = new float[40];
    private final Path mountains = new Path(), roadPath = new Path(), tmp = new Path();
    private final RectF rect = new RectF();

    // Prépare les pinceaux.
    public GameWorld() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    // Calcule la géométrie selon la taille de l'écran.
    public void setSize(int width, int height) {
        w = width;
        h = height;
        horizon = h * 0.42f;
        carY = h * 0.86f;
        cx = w / 2f;
        float baseHalf = Math.min(w * 0.30f, h * 0.75f);
        laneW = baseHalf * 2f / 3f;
        pBottom = (h - horizon) / (carY - horizon);
        relMin = K / pBottom - K;

        skyPaint.setShader(new LinearGradient(0, 0, 0, horizon, 0xFF0B0221, 0xFF4A1270, Shader.TileMode.CLAMP));
        sunR = h * 0.24f;
        sunCy = horizon - sunR * 0.30f;
        sunPaint.setShader(new LinearGradient(0, sunCy - sunR, 0, sunCy + sunR * 0.3f,
                0xFFFFE07A, 0xFFFF2E97, Shader.TileMode.CLAMP));
        groundPaint.setShader(new LinearGradient(0, horizon, 0, h, 0xFF1A0436, 0xFF07010F, Shader.TileMode.CLAMP));

        Random r = new Random(7);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextFloat() * w;
            starY[i] = r.nextFloat() * horizon * 0.8f;
        }
        mountains.reset();
        float limit = cx - w * 0.12f;
        float x = -w * 0.02f;
        mountains.moveTo(x, horizon);
        while (x < limit) {
            float peakX = x + w * (0.04f + r.nextFloat() * 0.06f);
            float peakH = h * (0.06f + r.nextFloat() * 0.14f);
            mountains.lineTo(Math.min(peakX, limit), horizon - peakH);
            x = peakX + w * (0.03f + r.nextFloat() * 0.05f);
            mountains.lineTo(Math.min(x, limit), horizon - h * 0.01f);
        }
        mountains.lineTo(limit, horizon);
        mountains.close();
        Path mirror = new Path();
        Matrix m = new Matrix();
        m.setScale(-1, 1, cx, 0);
        mountains.transform(m, mirror);
        mountains.addPath(mirror);
    }

    // Remet la partie à zéro.
    public void reset(Level l) {
        level = l;
        lane = 1;
        carLaneX = 1;
        lean = 0;
        distance = 0;
        speed = l.baseSpeed;
        bonus = 0;
        lives = MAX_LIVES;
        cycle = 0;
        boostTime = cooldownTime = invincibleTime = 0;
        hitFlash = speedUpTime = heartAnimTime = 0;
        lostHeart = -1;
        obstacles.clear();
        spawnCycle = 0;
        spawnIndex = 0;
        spawn();
    }

    // Change de voie (une à la fois).
    public void moveLane(int dir) {
        lane = Math.max(0, Math.min(LANES - 1, lane + dir));
    }

    // Boost disponible ?
    public boolean isBoostReady() { return boostTime <= 0 && cooldownTime <= 0; }
    // Boost en cours ?
    public boolean isBoosting() { return boostTime > 0; }

    // Lance un boost si possible.
    public boolean tryBoost() {
        if (!isBoostReady()) return false;
        boostTime = BOOST_DURATION;
        return true;
    }

    // Score = distance + bonus.
    public int getScore() { return (int) (distance + bonus); }

    // Avance la partie de dt, renvoie les événements.
    public int update(float dt) {
        int events = 0;

        float mult = boostTime > 0 ? BOOST_MULT : 1f;
        float dz = speed * mult * dt;
        distance += dz;

        if (boostTime > 0) {
            bonus += dz;
            boostTime -= dt;
            if (boostTime <= 0) {
                boostTime = 0;
                cooldownTime = BOOST_COOLDOWN;
            }
        } else if (cooldownTime > 0) {
            cooldownTime = Math.max(0, cooldownTime - dt);
        }

        int c = (int) (distance / level.cycleLength);
        if (c > cycle) {
            cycle = c;
            speed = Math.min(speed * SPEED_UP_FACTOR, level.baseSpeed * MAX_SPEED_FACTOR);
            speedUpTime = 1.5f;
            events |= EV_SPEED_UP;
        }

        carLaneX += (lane - carLaneX) * Math.min(1f, dt * 14f);

        spawn();

        int carLane = Math.round(carLaneX);
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle o = obstacles.get(i);
            float rel = o.worldZ - distance;
            if (rel < -12f) {
                obstacles.remove(i);
                continue;
            }
            float prevRel = rel + dz;
            // Obstacle traversé pendant la frame : pas de collision manquée si la frame est longue.
            boolean crossing = rel <= HIT_RANGE && prevRel >= -HIT_RANGE;
            if (!o.hit && invincibleTime <= 0 && o.lane == carLane && crossing) {
                o.hit = true;
                lives--;
                lostHeart = lives;
                heartAnimTime = 0.6f;
                hitFlash = 0.35f;
                invincibleTime = INVINCIBLE_TIME;
                events |= EV_HIT;
                if (lives <= 0) events |= EV_DEAD;
            }
        }

        hitFlash = Math.max(0, hitFlash - dt);
        speedUpTime = Math.max(0, speedUpTime - dt);
        heartAnimTime = Math.max(0, heartAnimTime - dt);
        invincibleTime = Math.max(0, invincibleTime - dt);
        return events;
    }

    // Ajoute les obstacles qui entrent dans le champ de vision.
    private void spawn() {
        while (true) {
            float z = spawnCycle * level.cycleLength + level.obstacleDist[spawnIndex];
            if (z > distance + VIEW_DIST) break;
            int mask = level.obstacleLanes[spawnIndex];
            for (int l = 0; l < LANES; l++) {
                if ((mask & (1 << l)) != 0) obstacles.add(new Obstacle(z, l, (spawnIndex + l) % 2));
            }
            spawnIndex++;
            if (spawnIndex >= level.obstacleDist.length) {
                spawnIndex = 0;
                spawnCycle++;
            }
        }
    }

    // Perspective : 1 au niveau de la voiture, -> 0 à l'horizon.
    private float project(float rel) { return K / (K + rel); }

    // Position Y à l'écran pour un facteur de perspective.
    private float yAt(float p) { return horizon + (carY - horizon) * p; }

    // Dessine toute la scène.
    public void drawScene(Canvas c, float cam, boolean gameplay, float time) {
        if (w == 0) return;
        boolean boost = gameplay && boostTime > 0;

        c.drawRect(0, 0, w, horizon, skyPaint);
        fillPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < starX.length; i++) {
            fillPaint.setColor(Color.argb((int) (120 + 100 * Math.sin(time * 2 + i)), 255, 255, 255));
            c.drawCircle(starX[i], starY[i], h * 0.0025f, fillPaint);
        }

        c.drawCircle(cx, sunCy, sunR, sunPaint);
        for (int i = 0; i < 6; i++) {
            float y0 = sunCy - sunR * 0.45f + i * sunR * 0.13f;
            float th = sunR * (0.015f + i * 0.012f);
            c.drawRect(cx - sunR - 2, y0, cx + sunR + 2, y0 + th, skyPaint);
        }

        fillPaint.setColor(0xFF1E0B44);
        c.drawPath(mountains, fillPaint);
        linePaint.setColor(0xB02DE2E6);
        linePaint.setStrokeWidth(h * 0.003f);
        c.drawPath(mountains, linePaint);

        c.drawRect(0, horizon, w, h, groundPaint);
        gridPaint.setStrokeWidth(h * 0.003f);
        int gridColor = boost ? 0xFFFF5EB0 : 0xFFFF2E97;
        gridPaint.setColor(gridColor);
        gridPaint.setAlpha(boost ? 255 : 170);
        for (int i = -14; i <= 13; i++) {
            float X = (i + 0.5f) * laneW;
            c.drawLine(cx, horizon, cx + X * pBottom, h, gridPaint);
        }
        float start = (float) Math.ceil((cam + relMin) / GRID_STEP) * GRID_STEP;
        for (float z = start; z < cam + VIEW_DIST * 2; z += GRID_STEP) {
            float p = project(z - cam);
            gridPaint.setAlpha((int) (40 + 180 * Math.min(1f, p * 1.5f)));
            float y = yAt(p);
            c.drawLine(0, y, w, y, gridPaint);
        }

        float pFar = project(VIEW_DIST * 2);
        float edgeFar = 1.5f * laneW * pFar, edgeNear = 1.5f * laneW * pBottom;
        roadPath.reset();
        roadPath.moveTo(cx - edgeFar, yAt(pFar));
        roadPath.lineTo(cx + edgeFar, yAt(pFar));
        roadPath.lineTo(cx + edgeNear, h);
        roadPath.lineTo(cx - edgeNear, h);
        roadPath.close();
        fillPaint.setColor(0xE00D0220);
        c.drawPath(roadPath, fillPaint);

        linePaint.setColor(boost ? 0xFFB8FFFF : GameView.CYAN);
        linePaint.setStrokeWidth(h * (boost ? 0.009f : 0.005f));
        c.drawLine(cx - edgeFar, yAt(pFar), cx - edgeNear, h, linePaint);
        c.drawLine(cx + edgeFar, yAt(pFar), cx + edgeNear, h, linePaint);

        linePaint.setColor(0xCCFFFFFF);
        linePaint.setStrokeWidth(h * 0.004f);
        float dashStart = (float) Math.floor((cam + relMin) / 6f) * 6f;
        for (float z = dashStart; z < cam + VIEW_DIST; z += 6f) {
            float p0 = project(Math.max(z - cam, relMin));
            float p1 = project(z - cam + 3f);
            for (int s = -1; s <= 1; s += 2) {
                float X = s * 0.5f * laneW;
                c.drawLine(cx + X * p0, yAt(p0), cx + X * p1, yAt(p1), linePaint);
            }
        }

        linePaint.setColor(GameView.PINK);
        linePaint.setStrokeWidth(h * 0.004f);
        c.drawLine(0, horizon, w, horizon, linePaint);

        if (!gameplay) return;

        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle o = obstacles.get(i);
            float rel = o.worldZ - cam;
            if (rel >= 0) drawObstacle(c, o, rel);
        }
        if (boost) drawSpeedLines(c, time);
        drawCar(c, time);
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle o = obstacles.get(i);
            float rel = o.worldZ - cam;
            if (rel < 0) drawObstacle(c, o, rel);
        }

        if (hitFlash > 0) c.drawColor(Color.argb((int) (hitFlash / 0.35f * 120), 255, 0, 40));
    }

    // Dessine un obstacle.
    private void drawObstacle(Canvas c, Obstacle o, float rel) {
        if (rel > VIEW_DIST || rel < relMin - 2f) return;
        float p = project(rel);
        float x = cx + (o.lane - 1) * laneW * p;
        float y = yAt(p);
        float bw = laneW * 0.7f * p;
        float bh = bw * 0.55f;
        int alpha = o.hit ? 70 : 255;
        linePaint.setStrokeWidth(Math.max(1f, h * 0.004f * p));

        if (o.kind == 0) {
            fillPaint.setColor(GameView.PINK);
            fillPaint.setAlpha(alpha);
            rect.set(x - bw / 2, y - bh, x + bw / 2, y);
            c.drawRect(rect, fillPaint);
            tmp.reset();
            tmp.moveTo(x - bw / 2, y - bh);
            tmp.lineTo(x - bw * 0.4f, y - bh * 1.3f);
            tmp.lineTo(x + bw * 0.4f, y - bh * 1.3f);
            tmp.lineTo(x + bw / 2, y - bh);
            tmp.close();
            fillPaint.setColor(0xFFFF8AC8);
            fillPaint.setAlpha(alpha);
            c.drawPath(tmp, fillPaint);
            linePaint.setColor(GameView.CYAN);
            linePaint.setAlpha(alpha);
            c.drawRect(rect, linePaint);
        } else {
            tmp.reset();
            tmp.moveTo(x - bw / 2, y);
            tmp.lineTo(x, y - bh * 1.6f);
            tmp.lineTo(x + bw / 2, y);
            tmp.close();
            fillPaint.setColor(GameView.PURPLE);
            fillPaint.setAlpha(alpha);
            c.drawPath(tmp, fillPaint);
            linePaint.setColor(GameView.CYAN);
            linePaint.setAlpha(alpha);
            c.drawPath(tmp, linePaint);
            c.drawLine(x, y - bh * 1.6f, x + bw * 0.1f, y, linePaint);
        }
    }

    // Dessine la voiture.
    private void drawCar(Canvas c, float time) {
        if (invincibleTime > 0 && ((int) (invincibleTime * 10)) % 2 == 0) return;

        float x = cx + (carLaneX - 1f) * laneW;
        float y = carY;
        float cw = laneW * 0.62f;
        float ch = cw * 0.45f;

        c.save();
        c.rotate(lean, x, y);

        if (boostTime > 0) {
            for (int side = -1; side <= 1; side += 2) {
                float fx = x + side * cw * 0.28f;
                float len = ch * (0.7f + 0.5f * (float) Math.abs(Math.sin(time * 40 + side)));
                tmp.reset();
                tmp.moveTo(fx - cw * 0.08f, y);
                tmp.lineTo(fx + cw * 0.08f, y);
                tmp.lineTo(fx, y + len);
                tmp.close();
                fillPaint.setColor(0xFFFF7A1A);
                c.drawPath(tmp, fillPaint);
                tmp.reset();
                tmp.moveTo(fx - cw * 0.04f, y);
                tmp.lineTo(fx + cw * 0.04f, y);
                tmp.lineTo(fx, y + len * 0.6f);
                tmp.close();
                fillPaint.setColor(GameView.YELLOW);
                c.drawPath(tmp, fillPaint);
            }
        }

        fillPaint.setColor(0xFF111111);
        c.drawRect(x - cw * 0.50f, y - ch * 0.30f, x - cw * 0.30f, y + ch * 0.10f, fillPaint);
        c.drawRect(x + cw * 0.30f, y - ch * 0.30f, x + cw * 0.50f, y + ch * 0.10f, fillPaint);

        tmp.reset();
        tmp.moveTo(x - cw * 0.32f, y - ch * 0.75f);
        tmp.lineTo(x - cw * 0.22f, y - ch * 1.20f);
        tmp.lineTo(x + cw * 0.22f, y - ch * 1.20f);
        tmp.lineTo(x + cw * 0.32f, y - ch * 0.75f);
        tmp.close();
        fillPaint.setColor(0xFFB01E3A);
        c.drawPath(tmp, fillPaint);
        rect.set(x - cw * 0.20f, y - ch * 1.10f, x + cw * 0.20f, y - ch * 0.82f);
        fillPaint.setColor(0xFF2A0A4A);
        c.drawRect(rect, fillPaint);

        rect.set(x - cw / 2, y - ch * 0.78f, x + cw / 2, y);
        fillPaint.setColor(0xFFE8304A);
        c.drawRoundRect(rect, ch * 0.15f, ch * 0.15f, fillPaint);

        fillPaint.setColor(boostTime > 0 ? 0xFFFFFFFF : GameView.PINK);
        c.drawRect(x - cw * 0.45f, y - ch * 0.62f, x - cw * 0.20f, y - ch * 0.48f, fillPaint);
        c.drawRect(x + cw * 0.20f, y - ch * 0.62f, x + cw * 0.45f, y - ch * 0.48f, fillPaint);
        fillPaint.setColor(0xFF1A1A1A);
        c.drawRect(x - cw * 0.12f, y - ch * 0.35f, x + cw * 0.12f, y - ch * 0.15f, fillPaint);

        c.restore();
    }

    // Dessine les lignes de vitesse du boost.
    private void drawSpeedLines(Canvas c, float time) {
        linePaint.setColor(0x99FFFFFF);
        linePaint.setStrokeWidth(h * 0.004f);
        for (int i = 0; i < 18; i++) {
            int deg = (i * 53) % 360;
            if (deg > 50 && deg < 130) continue;
            double a = Math.toRadians(deg);
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            float t = (time * 1.8f + i * 0.37f) % 1f;
            float r1 = w * (0.12f + t * 0.55f);
            float r2 = r1 + w * 0.06f;
            c.drawLine(cx + dx * r1, horizon + dy * r1 * 0.6f, cx + dx * r2, horizon + dy * r2 * 0.6f, linePaint);
        }
    }
}
