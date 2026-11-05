package com.example.vroomvroom;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.List;
import java.util.Locale;

/**
 * LA vue du jeu (sujet §4) : boucle update/draw, machine à états (tous les écrans),
 * lecture des capteurs, touch, HUD et menus.
 *
 * Organisation du fichier :
 *   1. Constantes & champs      4. Logique par état (update)    7. Dessin des menus
 *   2. Boucle de jeu            5. Transitions entre états      8. Petits outils de dessin
 *   3. Cycle de vie             6. Dessin du jeu (HUD)          9. Touch
 */
public class GameView extends View {

    // =====================================================================
    // 1. CONSTANTES & CHAMPS
    // =====================================================================

    /** Tous les écrans du dossier (§4 Interfaces) + les états internes de la partie. */
    public enum State { MENU, LEVEL_SELECT, LEADERBOARD, SETTINGS, HELP, TUTORIAL, COUNTDOWN, PLAYING, PAUSED, GAME_OVER }

    // --- Réglages capteurs : À AJUSTER EN TESTANT sur vos téléphones ---
    private static final float TILT_TRIGGER = 3.0f;  // inclinaison pour changer de voie (≈ 18°)
    private static final float TILT_RELEASE = 1.5f;  // zone morte : en dessous (≈ 9°) = tout droit
    private static final float TILT_REPEAT = 0.5f;   // si on reste penché, nouvelle voie toutes les 0.5 s
    private static final float BLOW_HOLD = 0.3f;     // souffle maintenu 0.3 s => un bruit bref ne déclenche rien
    private static final float BREATH_AUDIO_GUARD = 0.75f; // évite qu'un bruitage soit pris pour un souffle
    private static final float SECRET_HOLD = 4f;     // retourné 4 s => mode secret (dossier §3d)
    private static final float COUNTDOWN_TIME = 3f;
    private static final float TUTO_SKIP_DELAY = 15f;

    // --- Palette vaporwave ---
    static final int PINK = 0xFFFF2E97, CYAN = 0xFF2DE2E6, PURPLE = 0xFF7B2FF7,
            YELLOW = 0xFFFFD36E, WHITE = 0xFFFFFFFF, DARK = 0xFF0B0221;
    private static final String GAME_NAME = "Vroom Vroom";

    private final SaveManager save;
    private final AudioFx audio;
    private final TiltController tilt;
    private final BreathDetector breath;
    private final GameWorld world = new GameWorld();

    private State state = State.MENU;
    private int selectedLevel = 1;
    private boolean secretMode;
    private boolean micAvailable;      // micro autorisé ET démarré
    private boolean waitingPermission; // la popup de permission est affichée

    // Boucle
    private long lastNanos;
    private float time;                // temps total (animations)
    private boolean looping;

    // Capteurs
    private int tiltLatch;             // -1/0/+1 : direction déjà prise en compte
    private float tiltHold;            // temps passé penché
    private float blowTime;            // temps de souffle continu
    private float breathAudioGuardTime;
    private float upsideDownTime;
    private boolean secretToggled, secretMsgPending;
    private float secretMsgTime;

    // Compte à rebours / messages
    private float countdown;
    private int lastBeep;
    private boolean resumeCountdown;
    private float invertMsgTime;
    private float menuScroll;          // défilement du décor derrière les menus

    // Tuto
    private int tutoStep;
    private float tutoTimer;
    private boolean tutoFromHelp;

    // Fin de partie
    private int lastScore, lastDistance, lastStars;
    private boolean lastRecord;

    // Interface
    private float U = 1;               // 1 U = 1 % de la hauteur de l'écran
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint invertPaint = new Paint();
    private final Path heart = new Path();
    private final RectF tmpRect = new RectF();

    private final UiButton btnPlay = new UiButton("JOUER", true);
    private final UiButton btnRanking = new UiButton("CLASSEMENT", false);
    private final UiButton btnSettings = new UiButton("PARAMÈTRES", false);
    private final UiButton btnHelp = new UiButton("AIDE", false);
    private final UiButton btnBack = new UiButton("← RETOUR", false);
    private final UiButton btnLevel1 = new UiButton("1", true);
    private final UiButton btnLevel2 = new UiButton("2", true);
    private final UiButton btnTuto = new UiButton("LANCER LE TUTO", true);
    private final UiButton btnTutoNext = new UiButton("C'EST PARTI !", true);
    private final UiButton btnTutoSkip = new UiButton("PASSER", false);
    private final UiButton btnResume = new UiButton("REPRENDRE", true);
    private final UiButton btnRestart = new UiButton("RECOMMENCER", false);
    private final UiButton btnMenu = new UiButton("MENU PRINCIPAL", false);
    private final UiButton btnReplay = new UiButton("REJOUER", true);
    private final UiButton btnGoMenu = new UiButton("MENU", false);

    private final UiButton[] menuButtons = {btnPlay, btnRanking, btnSettings, btnHelp};
    private final UiButton[] levelButtons = {btnLevel1, btnLevel2, btnBack};
    private final UiButton[] backOnly = {btnBack};
    private final UiButton[] helpButtons = {btnBack, btnTuto};
    private final UiButton[] pauseButtons = {btnResume, btnRestart, btnMenu};
    private final UiButton[] gameOverButtons = {btnReplay, btnGoMenu};
    private final UiButton[] none = {};

    private UiButton pressed;           // bouton sous le doigt
    private int draggingSlider = -1;
    private final RectF[] sliderTracks = {new RectF(), new RectF(), new RectF()}; // son, musique, micro
    private final float[] rowY = new float[5];
    private final RectF vibRow = new RectF(), initialsRow = new RectF(), pauseRect = new RectF();

    public GameView(Context context) {
        super(context);
        save = new SaveManager(context);
        audio = new AudioFx(context, save);
        tilt = new TiltController(context, this);
        breath = new BreathDetector();
        breath.setSensitivity(save.getMicSensitivity());

        try {
            text.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/Audiowide-Regular.ttf"));
        } catch (RuntimeException e) {
            // police introuvable : on garde la police "rétro" du système plutôt que de planter
            text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        }
        stroke.setStyle(Paint.Style.STROKE);
        // Filtre qui inverse les couleurs (mode secret : couleurs en négatif)
        invertPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0})));
        btnLevel1.textScale = 0.45f;
        btnLevel2.textScale = 0.45f;
        buildHeart();

        // Premier lancement => tuto obligatoire (dossier §6b), sinon menu
        if (save.isTutoDone()) state = State.MENU;
        else startTutorial(false);
        audio.playMusic("music_menu");
    }

    public SaveManager getSave() { return save; }

    // =====================================================================
    // 2. BOUCLE DE JEU (sujet §4.4)
    // =====================================================================

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            long now = System.nanoTime();
            // dt = temps écoulé depuis la frame précédente => le jeu va à la même vitesse
            // sur un téléphone rapide ou lent (au lieu de supposer 16 ms pile)
            float dt = (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            if (dt > 0.05f) dt = 0.05f; // si le téléphone a "ramé", on évite un saut énorme
            update(dt);
            invalidate();              // demande un nouveau onDraw()
            postDelayed(this, 16);     // ≈ 60 images/s
        }
    };

    private void startLoop() {
        if (looping) return;
        looping = true;
        lastNanos = System.nanoTime();
        post(gameLoop);
    }

    private void stopLoop() {
        looping = false;
        removeCallbacks(gameLoop); // la boucle ne tourne pas en arrière-plan
    }

    // =====================================================================
    // 3. CYCLE DE VIE (appelé par MainActivity)
    // =====================================================================

    public void resume() {
        tilt.start();
        audio.resumeMusic();
        // Si on quitte l'appli pendant l'étape micro du tuto, on la relance proprement
        if (state == State.TUTORIAL && (tutoStep == 2 || tutoStep == 3)) enterTutoStep(2);
        startLoop();
    }

    public void pause() {
        // Cas limite : appel / notification / bouton home pendant la partie => pause auto
        if (state == State.PLAYING || state == State.COUNTDOWN) pauseGame();
        breath.stop();
        tilt.stop();
        audio.pauseMusic();
        stopLoop();
    }

    public void release() {
        breath.stop();
        audio.release();
    }

    public void setWaitingPermission(boolean waiting) {
        waitingPermission = waiting;
    }

    /** Bouton retour d'Android. Renvoie false pour laisser Android quitter l'appli. */
    public boolean onBack() {
        switch (state) {
            case MENU:
                return false;
            case PLAYING:
            case COUNTDOWN:
                pauseGame();
                return true;
            case PAUSED:
                resumeGame();
                return true;
            case TUTORIAL:
                if (!tutoFromHelp) return false;
                breath.stop();
                setState(State.HELP);
                return true;
            case GAME_OVER:
                goToMenu();
                return true;
            default:
                setState(State.MENU);
                return true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        world.setSize(w, h);
        layoutUi(w, h);
    }

    /** Place tous les boutons en proportion de l'écran (aucune taille de téléphone codée en dur). */
    private void layoutUi(int w, int h) {
        U = h / 100f;
        float mid = w / 2f;

        // Menu : JOUER grand et central (zone des pouces), le reste plus petit en dessous (dossier §6c)
        btnPlay.set(mid - w * 0.17f, 40 * U, mid + w * 0.17f, 58 * U);
        float bw = w * 0.2f, gap = w * 0.03f, x0 = mid - (bw * 3 + gap * 2) / 2;
        btnRanking.set(x0, 68 * U, x0 + bw, 81 * U);
        btnSettings.set(x0 + bw + gap, 68 * U, x0 + 2 * bw + gap, 81 * U);
        btnHelp.set(x0 + 2 * (bw + gap), 68 * U, x0 + 3 * bw + 2 * gap, 81 * U);

        btnBack.set(3 * U, 3 * U, 3 * U + Math.max(w * 0.16f, 30 * U), 15 * U);

        float cw = w * 0.27f;
        btnLevel1.set(mid - cw - w * 0.03f, 24 * U, mid - w * 0.03f, 82 * U);
        btnLevel2.set(mid + w * 0.03f, 24 * U, mid + w * 0.03f + cw, 82 * U);

        btnTuto.set(mid - w * 0.16f, 80 * U, mid + w * 0.16f, 94 * U);
        btnTutoNext.set(mid - w * 0.15f, 68 * U, mid + w * 0.15f, 84 * U);
        btnTutoSkip.set(w - 3 * U - w * 0.14f, 85 * U, w - 3 * U, 96 * U);

        // Pause : REPRENDRE en grand, au centre ; les 2 autres plus petits en dessous
        btnResume.set(mid - w * 0.18f, 34 * U, mid + w * 0.18f, 52 * U);
        btnRestart.set(mid - w * 0.18f, 60 * U, mid - w * 0.01f, 74 * U);
        btnMenu.set(mid + w * 0.01f, 60 * U, mid + w * 0.18f, 74 * U);

        btnReplay.set(mid - w * 0.21f, 72 * U, mid + w * 0.03f, 90 * U);
        btnGoMenu.set(mid + w * 0.06f, 74 * U, mid + w * 0.21f, 88 * U);

        // Bouton pause : large (15 U ≈ 1 cm) et dans le coin, loin de la route (dossier §6a)
        pauseRect.set(w - 18 * U, 3 * U, w - 3 * U, 18 * U);

        // Paramètres : 5 lignes
        for (int i = 0; i < 5; i++) rowY[i] = (30 + i * 14) * U;
        // Zones tactiles des curseurs plus hautes que le trait dessiné (doigt imprécis)
        sliderTracks[0].set(w * 0.46f, rowY[0] - 5 * U, w * 0.80f, rowY[0] + 5 * U);
        sliderTracks[1].set(w * 0.46f, rowY[1] - 5 * U, w * 0.80f, rowY[1] + 5 * U);
        sliderTracks[2].set(w * 0.46f, rowY[3] - 5 * U, w * 0.80f, rowY[3] + 5 * U);
        vibRow.set(w * 0.12f, rowY[2] - 6 * U, w * 0.88f, rowY[2] + 6 * U);
        initialsRow.set(w * 0.12f, rowY[4] - 6 * U, w * 0.88f, rowY[4] + 6 * U);
    }

    // =====================================================================
    // 4. LOGIQUE PAR ÉTAT (sujet §4.7 : update ne fait évoluer que ce qui doit l'être)
    // =====================================================================

    private void update(float dt) {
        time += dt;
        if (secretMsgTime > 0) secretMsgTime -= dt;

        switch (state) {
            case MENU:
                menuScroll += dt * 12;
                updateSecretGesture(dt);
                break;
            case TUTORIAL:
                menuScroll += dt * 12;
                updateTutorial(dt);
                break;
            case COUNTDOWN:
                updateLean();
                updateCountdown(dt);
                break;
            case PLAYING:
                updatePlaying(dt);
                break;
            case PAUSED:
                break; // rien ne bouge
            default: // autres menus : seul le décor défile
                menuScroll += dt * 12;
                break;
        }
    }

    private void updatePlaying(float dt) {
        updateLean();
        if (tilt.isAvailable()) handleTilt(dt);
        if (micAvailable) {
            if (breathAudioGuardTime > 0) {
                breathAudioGuardTime = Math.max(0, breathAudioGuardTime - dt);
                blowTime = 0;
            } else {
                handleBreath(dt);
            }
        }
        if (invertMsgTime > 0) invertMsgTime -= dt;

        int ev = world.update(dt);
        if ((ev & GameWorld.EV_HIT) != 0) {
            audio.collision();
            guardBreathAfterAudio();
        }
        if ((ev & GameWorld.EV_SPEED_UP) != 0) {
            audio.speedUp();
            guardBreathAfterAudio();
        }
        if ((ev & GameWorld.EV_DEAD) != 0) finishGame();
    }

    /** Inclinaison du joueur, inversée en mode secret (dossier §3d). */
    private float steerInput() {
        float s = tilt.getSteer();
        return secretMode ? -s : s;
    }

    /** Dossier §5 : la voiture s'incline tout de suite avec le téléphone (feedback immédiat). */
    private void updateLean() {
        world.lean = clamp(steerInput() * 2f, -15f, 15f);
    }

    /**
     * Direction (dossier §6e) :
     *  - zone morte : sous TILT_RELEASE on considère "tout droit" ;
     *  - il faut dépasser TILT_TRIGGER pour changer de voie (hystérésis entre les 2 seuils => pas
     *    de va-et-vient si la main tremble autour d'un seul seuil) ;
     *  - une voie à la fois ; si on reste penché, une voie de plus toutes les TILT_REPEAT s.
     */
    private void handleTilt(float dt) {
        float s = steerInput();
        if (Math.abs(s) < TILT_RELEASE) {
            tiltLatch = 0;
            tiltHold = 0;
        } else if (Math.abs(s) > TILT_TRIGGER) {
            int dir = s > 0 ? 1 : -1;
            if (tiltLatch != dir) {
                world.moveLane(dir);
                tiltLatch = dir;
                tiltHold = 0;
            } else {
                tiltHold += dt;
                if (tiltHold > TILT_REPEAT) {
                    world.moveLane(dir);
                    tiltHold = 0;
                }
            }
        }
    }

    /** Boost (dossier §6e) : souffle au-dessus du seuil pendant BLOW_HOLD secondes. */
    private void handleBreath(float dt) {
        if (breath.isBlowing()) blowTime += dt;
        else blowTime = 0;
        if (blowTime >= BLOW_HOLD && world.isBoostReady()) {
            world.tryBoost();
            audio.boost();
            guardBreathAfterAudio();
            blowTime = 0;
        }
    }

    private void guardBreathAfterAudio() {
        breathAudioGuardTime = BREATH_AUDIO_GUARD;
        blowTime = 0;
    }

    /** 3-2-1 : avance seulement si le téléphone est tenu droit ; mesure le bruit ambiant. */
    private void updateCountdown(float dt) {
        boolean straight = !tilt.isAvailable() || tilt.isHeldStraight();
        if (!straight) return; // le compte à rebours attend
        countdown -= dt;
        int sec = (int) Math.ceil(countdown);
        if (sec < lastBeep && sec > 0) {
            lastBeep = sec;
            audio.countdown();
        }
        if (countdown <= 0) {
            tilt.calibrateNeutral();                 // position actuelle = "tout droit"
            if (micAvailable) breath.endCalibration(); // bruit ambiant mesuré => seuil adapté
            state = State.PLAYING;
            audio.go();
            guardBreathAfterAudio();
            if (secretMode) invertMsgTime = 2.5f;      // le message reste encore un peu après GO
        }
    }

    /** Mode secret : téléphone retourné (paysage à l'envers) pendant 4 s, dans le menu principal. */
    private void updateSecretGesture(float dt) {
        if (tilt.isUpsideDown()) {
            upsideDownTime += dt;
            if (upsideDownTime >= SECRET_HOLD && !secretToggled) {
                secretMode = !secretMode;
                secretToggled = true;        // une seule bascule par retournement
                secretMsgPending = true;
                audio.vibrate(300);          // on sent la validation même sans voir l'écran
            }
        } else {
            upsideDownTime = 0;
            secretToggled = false;
            if (secretMsgPending) {          // message affiché quand on remet l'écran face à soi
                secretMsgPending = false;
                secretMsgTime = 3f;
            }
        }
    }

    // ---------- Tuto interactif (dossier §6b : "faire un geste une fois vaut mieux que le lire") ----------
    // Étapes : 0 incliner à gauche, 1 à droite, 2 calibration micro, 3 souffler, 4 terminé

    private void updateTutorial(float dt) {
        if (waitingPermission) return; // on attend la réponse à la popup micro
        tutoTimer += dt;
        float s = tilt.getSteer();
        switch (tutoStep) {
            case 0:
                if (s < -TILT_TRIGGER || !tilt.isAvailable()) nextTutoStep();
                break;
            case 1:
                if (s > TILT_TRIGGER || !tilt.isAvailable()) nextTutoStep();
                break;
            case 2:
                if (tutoTimer >= 2f) {          // 2 s de silence pour mesurer le bruit ambiant
                    breath.endCalibration();
                    enterTutoStep(3);
                }
                break;
            case 3:
                if (breath.isBlowing()) blowTime += dt;
                else blowTime = 0;
                if (blowTime >= BLOW_HOLD) {
                    breath.stop();
                    nextTutoStep();
                }
                break;
            default:
                break;
        }
    }

    private void nextTutoStep() {
        audio.success();
        enterTutoStep(tutoStep + 1);
    }

    private void enterTutoStep(int step) {
        tutoStep = step;
        tutoTimer = 0;
        blowTime = 0;
        if (step == 2) {
            // Pas de micro (refusé ou en panne) => on saute la partie souffle
            if (breath.start(getContext())) {
                breath.setSensitivity(save.getMicSensitivity());
                breath.startCalibration();
            } else {
                tutoStep = 4;
            }
        }
    }

    // =====================================================================
    // 5. TRANSITIONS ENTRE ÉTATS
    // =====================================================================

    private void setState(State s) {
        state = s;
        pressed = null;
        draggingSlider = -1;
    }

    private void startTutorial(boolean fromHelp) {
        tutoFromHelp = fromHelp;
        tilt.resetNeutral();
        tiltLatch = 0;
        setState(State.TUTORIAL);
        enterTutoStep(0);
    }

    private void finishTutorial() {
        breath.stop();
        save.setTutoDone(true);
        setState(tutoFromHelp ? State.HELP : State.MENU);
    }

    private void startGame(int level) {
        selectedLevel = level;
        world.reset(Level.get(level));
        tiltLatch = 0;
        tiltHold = 0;
        blowTime = 0;
        breathAudioGuardTime = 0;
        breath.setSensitivity(save.getMicSensitivity());
        micAvailable = breath.start(getContext()); // false => jeu jouable sans boost (dossier §6e)
        audio.playMusic(secretMode ? "music_secret" : "music_game");
        invertMsgTime = 0;
        startCountdown(false);
    }

    private void startCountdown(boolean resume) {
        countdown = COUNTDOWN_TIME;
        lastBeep = 4;
        resumeCountdown = resume;
        tilt.resetNeutral();
        if (micAvailable) breath.startCalibration();
        setState(State.COUNTDOWN);
    }

    private void pauseGame() {
        breath.stop();          // pas besoin du micro en pause
        setState(State.PAUSED);
    }

    /** Reprendre => petit 3-2-1 pour laisser le temps de reprendre le téléphone en main. */
    private void resumeGame() {
        world.invincibleTime = 0; // sinon la voiture peut rester "clignotée" (invisible) pendant le 3-2-1
        micAvailable = breath.start(getContext());
        startCountdown(true);
    }

    private void goToMenu() {
        breath.stop();
        audio.playMusic("music_menu");
        setState(State.MENU);
    }

    private void finishGame() {
        breath.stop();
        lastScore = world.getScore();
        lastDistance = (int) world.distance;
        lastStars = world.cycle;
        lastRecord = save.isHighScore(selectedLevel, lastScore);
        audio.gameOver();
        audio.playMusic("music_menu");
        setState(State.GAME_OVER);

        if (lastRecord) {
            final int level = selectedLevel, score = lastScore, stars = lastStars, dist = lastDistance;
            if (save.getInitials() != null) {
                // Dossier §6d : initiales gardées => on ne les redemande pas
                save.addScore(level, new SaveManager.ScoreEntry(save.getInitials(), stars, score, dist));
            } else {
                showInitialsDialog("NOUVEAU RECORD ! Tes initiales :", () ->
                        save.addScore(level, new SaveManager.ScoreEntry(save.getInitials(), stars, score, dist)));
            }
        }
    }

    /**
     * Seule saisie de texte du jeu (dossier §6d) : 3 lettres max, lettres uniquement,
     * majuscules automatiques, pas de suggestions du clavier.
     */
    private void showInitialsDialog(String title, final Runnable onDone) {
        final EditText input = new EditText(getContext());
        InputFilter lettersOnly = (source, start, end, dest, dstart, dend) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char ch = source.charAt(i);
                if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) sb.append(Character.toUpperCase(ch));
            }
            String out = sb.toString();
            return out.contentEquals(source.subSequence(start, end)) ? null : out; // null = garder tel quel
        };
        input.setFilters(new InputFilter[]{lettersOnly, new InputFilter.LengthFilter(3)});
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(32);
        String current = save.getInitials();
        if (current != null) {
            input.setText(current);
            input.setSelection(current.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("OK", (d, which) -> {
                    String s = input.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if (s.isEmpty()) s = "AAA";
                    save.setInitials(s);
                    if (onDone != null) onDone.run();
                })
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    // =====================================================================
    // 6. DESSIN DU JEU
    // =====================================================================

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        switch (state) {
            case COUNTDOWN:
            case PLAYING:
            case PAUSED:
                drawGame(c);
                if (state == State.PAUSED) drawPause(c);
                break;
            case GAME_OVER:
                drawGameOver(c);
                break;
            default:
                // Menus : le décor vaporwave défile derrière, assombri pour la lisibilité
                world.drawScene(c, menuScroll, false, time);
                c.drawColor(0x990B0221);
                switch (state) {
                    case MENU: drawMenu(c); break;
                    case LEVEL_SELECT: drawLevelSelect(c); break;
                    case LEADERBOARD: drawLeaderboard(c); break;
                    case SETTINGS: drawSettings(c); break;
                    case HELP: drawHelp(c); break;
                    case TUTORIAL: drawTutorial(c); break;
                    default: break;
                }
        }
    }

    private void drawGame(Canvas c) {
        // Mode secret : tout ce qui est dessiné entre saveLayer et restore passe par le filtre négatif
        if (secretMode) c.saveLayer(null, invertPaint);
        world.drawScene(c, world.distance, true, time);
        drawHud(c);
        if (secretMode) c.restore();
        drawGameMessages(c);
    }

    /** HUD fidèle au sketch "Zone de jeu" : score à gauche, distance au centre, vies + pause à droite. */
    private void drawHud(Canvas c) {
        int w = getWidth();

        // Score (le compteur défile en continu, dossier §5)
        tmpRect.set(3 * U, 3 * U, 3 * U + Math.max(w * 0.16f, 30 * U), 18 * U);
        fill.setColor(0x88FF2E97);
        c.drawRoundRect(tmpRect, 1.5f * U, 1.5f * U, fill);
        drawText(c, "SCORE", tmpRect.left + 2 * U, 7.5f * U, 3 * U, CYAN, Paint.Align.LEFT);
        drawText(c, String.valueOf(world.getScore()), tmpRect.left + 2 * U, 15.5f * U, 6.5f * U, WHITE, Paint.Align.LEFT);
        if (world.cycle > 0) drawText(c, "★ x" + world.cycle, tmpRect.left + 1 * U, 24 * U, 3.8f * U, YELLOW, Paint.Align.LEFT);

        // Distance parcourue, au centre (sur le soleil)
        drawText(c, String.format(Locale.FRANCE, "%d M", (int) world.distance), w / 2f, 11 * U, 7 * U, WHITE, Paint.Align.CENTER);

        // Vies : cœurs pleins / vides ; le cœur perdu grossit et s'efface (dossier §5)
        float heartSize = 6 * U;
        for (int i = 0; i < GameWorld.MAX_LIVES; i++) {
            float hx = pauseRect.left - 6 * U - (GameWorld.MAX_LIVES - 1 - i) * 8 * U;
            float hy = 10 * U;
            if (i < world.lives) {
                drawHeart(c, hx, hy, heartSize, PINK, 255);
            } else if (i == world.lostHeart && world.heartAnimTime > 0) {
                float t = world.heartAnimTime / 0.6f;  // 1 -> 0
                drawHeart(c, hx, hy, heartSize * (1f + (1f - t) * 0.8f), PINK, (int) (255 * t));
            } else {
                drawHeart(c, hx, hy, heartSize, WHITE, 50);
            }
        }

        // Bouton pause
        fill.setColor(0x660B0221);
        c.drawRoundRect(pauseRect, 2 * U, 2 * U, fill);
        stroke.setColor(CYAN);
        stroke.setStrokeWidth(0.5f * U);
        c.drawRoundRect(pauseRect, 2 * U, 2 * U, stroke);
        fill.setColor(CYAN);
        float pcx = pauseRect.centerX(), pcy = pauseRect.centerY();
        c.drawRect(pcx - 3 * U, pcy - 4 * U, pcx - 1 * U, pcy + 4 * U, fill);
        c.drawRect(pcx + 1 * U, pcy - 4 * U, pcx + 3 * U, pcy + 4 * U, fill);

        drawBoostGauge(c);
    }

    /** Jauge de boost (sketch : à gauche, segments jaune -> rouge). Réagit en direct au souffle. */
    private void drawBoostGauge(Canvas c) {
        float left = 3 * U, right = 8 * U, top = 48 * U, bottom = 90 * U;
        boolean active = world.isBoosting();
        boolean cooling = world.cooldownTime > 0;
        float ratio;
        if (!micAvailable) ratio = 0;
        else if (active) ratio = world.boostTime / GameWorld.BOOST_DURATION;            // se vide
        else if (cooling) ratio = 1f - world.cooldownTime / GameWorld.BOOST_COOLDOWN;   // se recharge
        else ratio = clamp(breath.getRatio(), 0, 1) * 0.75f + clamp(blowTime / BLOW_HOLD, 0, 1) * 0.25f;

        int segs = 8;
        float gap = 0.6f * U;
        float segH = (bottom - top - gap * (segs - 1)) / segs;
        int lit = Math.round(ratio * segs);
        for (int i = 0; i < segs; i++) {
            float y1 = bottom - i * (segH + gap);
            int col = i < 3 ? YELLOW : (i < 6 ? 0xFFFF8A3D : 0xFFFF3355);
            if (cooling && !active) col = 0xFF6A5A8A;   // gris violet = pas encore prêt
            fill.setColor(col);
            if (i >= lit) fill.setAlpha(55);
            else if (active && ((int) (time * 20)) % 2 == 0) fill.setAlpha(190); // scintille
            c.drawRect(left, y1 - segH, right, y1, fill);
        }
        String label = !micAvailable ? "MICRO OFF" : active ? "BOOST !" : cooling ? "RECHARGE" : "SOUFFLE";
        drawText(c, label, left, top - 2 * U, 2.8f * U, active ? YELLOW : CYAN, Paint.Align.LEFT);
    }

    /** Messages par-dessus le jeu (pas inversés en mode secret, pour rester lisibles). */
    private void drawGameMessages(Canvas c) {
        float mid = getWidth() / 2f;
        if (state == State.COUNTDOWN) {
            boolean straight = !tilt.isAvailable() || tilt.isHeldStraight();
            if (!straight) {
                tmpRect.set(mid - 40 * U, 30 * U, mid + 40 * U, 78 * U);
                fill.setColor(0xCC0B0221);
                c.drawRoundRect(tmpRect, 3 * U, 3 * U, fill);
                drawText(c, "TIENS LE TÉLÉPHONE DROIT", mid, 42 * U, 4.5f * U, YELLOW, Paint.Align.CENTER);
                drawPhone(c, mid, 60 * U, 9 * U, clamp(tilt.getSteer() * 6f, -60, 60), CYAN);
            } else {
                drawNeonTitle(c, String.valueOf(Math.max(1, (int) Math.ceil(countdown))), mid, 60 * U, 26 * U);
            }
            if (resumeCountdown) drawText(c, "REPRISE", mid, 28 * U, 5 * U, WHITE, Paint.Align.CENTER);
            else drawText(c, "Incline = tourner   ·   Souffle = boost", mid, 95 * U, 3.4f * U, WHITE, Paint.Align.CENTER);
        }
        if (state == State.PLAYING && world.speedUpTime > 0) {
            float t = world.speedUpTime / 1.5f;
            drawNeonTitle(c, "SPEED UP", mid, 42 * U, (9 + 5 * t) * U);
        }
        // Mode secret : message au début de la partie (dossier §3d)
        if (secretMode && (state == State.COUNTDOWN || invertMsgTime > 0)) {
            drawText(c, "⚠ COMMANDES INVERSÉES ⚠", mid, 72 * U, 5 * U, YELLOW, Paint.Align.CENTER);
        }
    }

    private void drawPause(Canvas c) {
        c.drawColor(0xB0000000); // assombrit le jeu figé derrière (sketch "Menu pause")
        drawNeonTitle(c, "PAUSE", getWidth() / 2f, 25 * U, 12 * U);
        drawButtons(c, pauseButtons);
    }

    private void drawGameOver(Canvas c) {
        float mid = getWidth() / 2f;
        c.drawColor(0xFF000000); // sketch : écran noir
        drawNeonTitle(c, "GAME OVER", mid, 20 * U, 13 * U);
        String ini = save.getInitials() != null ? save.getInitials() : "---";
        drawText(c, ini + "   " + stars(lastStars), mid, 34 * U, 6 * U, YELLOW, Paint.Align.CENTER);
        drawText(c, "SCORE     " + lastScore, mid, 46 * U, 6 * U, WHITE, Paint.Align.CENTER);
        drawText(c, "DISTANCE  " + lastDistance + " M", mid, 56 * U, 6 * U, WHITE, Paint.Align.CENTER);
        if (lastRecord && ((int) (time * 3)) % 2 == 0) {
            drawText(c, "NOUVEAU RECORD !", mid, 66 * U, 5 * U, CYAN, Paint.Align.CENTER);
        }
        drawButtons(c, gameOverButtons);
    }

    // =====================================================================
    // 7. DESSIN DES MENUS
    // =====================================================================

    private void drawMenu(Canvas c) {
        int w = getWidth();
        drawNeonTitle(c, GAME_NAME, w / 2f, 28 * U, 15 * U);
        if (secretMsgTime > 0) {
            drawText(c, secretMode ? "MODE SECRET ACTIVÉ" : "MODE SECRET DÉSACTIVÉ",
                    w / 2f, 36 * U, 4.5f * U, YELLOW, Paint.Align.CENTER);
        }
        drawButtons(c, menuButtons);
        if (secretMode) drawText(c, "◆ MODE SECRET", w - 3 * U, 95 * U, 3.5f * U, PINK, Paint.Align.RIGHT);
    }

    private void drawLevelSelect(Canvas c) {
        drawText(c, "SÉLECTION DU NIVEAU", getWidth() / 2f, 13 * U, 6 * U, WHITE, Paint.Align.CENTER);
        drawButtons(c, levelButtons);
        UiButton[] cards = {btnLevel1, btnLevel2};
        for (int i = 0; i < 2; i++) {
            RectF r = cards[i].rect;
            Level l = Level.get(i + 1);
            drawText(c, l.name, r.centerX(), r.bottom - 11 * U, 4.5f * U, WHITE, Paint.Align.CENTER);
            drawText(c, "RECORD " + save.getBestScore(i + 1), r.centerX(), r.bottom - 5 * U, 3.5f * U, DARK, Paint.Align.CENTER);
        }
    }

    private void drawLeaderboard(Canvas c) {
        int w = getWidth();
        drawText(c, "CLASSEMENT", w / 2f, 13 * U, 6 * U, WHITE, Paint.Align.CENTER);
        drawButtons(c, backOnly);
        for (int lv = 1; lv <= 2; lv++) {
            float left = lv == 1 ? w * 0.10f : w * 0.52f;
            float right = left + w * 0.38f;
            tmpRect.set(left, 22 * U, right, 94 * U);
            fill.setColor(0x99140433);
            c.drawRoundRect(tmpRect, 2 * U, 2 * U, fill);
            stroke.setColor(lv == 1 ? CYAN : PINK);
            stroke.setStrokeWidth(0.4f * U);
            c.drawRoundRect(tmpRect, 2 * U, 2 * U, stroke);
            drawText(c, "NIVEAU " + lv, (left + right) / 2, 31 * U, 5 * U, lv == 1 ? CYAN : PINK, Paint.Align.CENTER);

            List<SaveManager.ScoreEntry> list = save.getScores(lv);
            if (list.isEmpty()) {
                drawText(c, "Aucun score", (left + right) / 2, 55 * U, 4 * U, WHITE, Paint.Align.CENTER);
            }
            for (int i = 0; i < list.size(); i++) {
                SaveManager.ScoreEntry e = list.get(i);
                float y = (43 + i * 11) * U;
                drawText(c, (i + 1) + ". " + e.initials, left + 3 * U, y, 4 * U, WHITE, Paint.Align.LEFT);
                drawText(c, "★" + e.stars, left + (right - left) * 0.52f, y, 4 * U, YELLOW, Paint.Align.LEFT);
                drawText(c, String.valueOf(e.score), right - 3 * U, y, 4 * U, WHITE, Paint.Align.RIGHT);
            }
        }
    }

    private void drawSettings(Canvas c) {
        int w = getWidth();
        drawText(c, "PARAMÈTRES", w / 2f, 13 * U, 6 * U, WHITE, Paint.Align.CENTER);
        drawButtons(c, backOnly);
        String[] labels = {"SON", "MUSIQUE", "VIBRATIONS", "SENSIBILITÉ MICRO", "INITIALES"};
        for (int i = 0; i < 5; i++) {
            drawText(c, labels[i], w * 0.12f, rowY[i] + 1.5f * U, 4 * U, WHITE, Paint.Align.LEFT);
        }
        drawSlider(c, sliderTracks[0], save.getSoundVolume());
        drawSlider(c, sliderTracks[1], save.getMusicVolume());
        drawSlider(c, sliderTracks[2], save.getMicSensitivity());
        if (!BreathDetector.hasPermission(getContext())) {
            drawText(c, "micro refusé : boost désactivé", w * 0.12f, rowY[3] + 5.5f * U, 2.8f * U, YELLOW, Paint.Align.LEFT);
        }

        // Interrupteur vibrations
        boolean on = save.isVibrationOn();
        float sx = w * 0.46f, sy = rowY[2];
        tmpRect.set(sx, sy - 3 * U, sx + 12 * U, sy + 3 * U);
        fill.setColor(on ? PINK : 0xFF3A2A5A);
        c.drawRoundRect(tmpRect, 3 * U, 3 * U, fill);
        fill.setColor(WHITE);
        c.drawCircle(on ? sx + 9 * U : sx + 3 * U, sy, 2.4f * U, fill);
        drawText(c, on ? "OUI" : "NON", sx + 15 * U, sy + 1.5f * U, 4 * U, on ? PINK : WHITE, Paint.Align.LEFT);

        String ini = save.getInitials() != null ? save.getInitials() : "---";
        drawText(c, ini, w * 0.46f, rowY[4] + 1.5f * U, 5 * U, CYAN, Paint.Align.LEFT);
        drawText(c, "(toucher pour modifier)", w * 0.46f + 16 * U, rowY[4] + 1.5f * U, 2.8f * U, WHITE, Paint.Align.LEFT);
    }

    private void drawSlider(Canvas c, RectF r, int value) {
        float y = r.centerY();
        float x = r.left + (r.right - r.left) * value / 100f;
        stroke.setStrokeWidth(1 * U);
        stroke.setColor(0xFF3A2A5A);
        c.drawLine(r.left, y, r.right, y, stroke);
        stroke.setColor(PINK);
        c.drawLine(r.left, y, x, y, stroke);
        fill.setColor(WHITE);
        c.drawCircle(x, y, 2.6f * U, fill);
        drawText(c, String.valueOf(value), r.right + 3 * U, y + 1.5f * U, 4 * U, WHITE, Paint.Align.LEFT);
    }

    private void drawHelp(Canvas c) {
        int w = getWidth();
        drawText(c, "AIDE", w / 2f, 13 * U, 6 * U, WHITE, Paint.Align.CENTER);
        drawButtons(c, backOnly);
        // Les gestes "invisibles" sont montrés en image (dossier §6b)
        float angle = (float) Math.sin(time * 2) * 20;
        drawPhone(c, w * 0.3f, 46 * U, 11 * U, angle, CYAN);
        drawText(c, "INCLINER = CHANGER DE VOIE", w * 0.3f, 68 * U, 3.6f * U, CYAN, Paint.Align.CENTER);
        drawMic(c, w * 0.7f, 46 * U, 14 * U, 0.5f + 0.5f * (float) Math.sin(time * 4));
        drawText(c, "SOUFFLER = BOOST", w * 0.7f, 68 * U, 3.6f * U, PINK, Paint.Align.CENTER);
        drawText(c, "Pause : bouton en haut à droite", w / 2f, 75 * U, 3 * U, WHITE, Paint.Align.CENTER);
        drawButtons(c, helpButtons);
    }

    private void drawTutorial(Canvas c) {
        int w = getWidth();
        float mid = w / 2f;
        drawText(c, "TUTORIEL", mid, 12 * U, 5 * U, WHITE, Paint.Align.CENTER);
        if (waitingPermission) {
            drawText(c, "Autorise le micro pour pouvoir booster", mid, 50 * U, 4.5f * U, YELLOW, Paint.Align.CENTER);
            return;
        }
        if (tutoStep <= 1) {
            boolean left = tutoStep == 0;
            drawText(c, "ÉTAPE " + (tutoStep + 1) + "/3", mid, 20 * U, 3.5f * U, CYAN, Paint.Align.CENTER);
            drawNeonTitle(c, left ? "INCLINE À GAUCHE" : "INCLINE À DROITE", mid, 34 * U, 8 * U);
            drawText(c, "comme un volant", mid, 41 * U, 3.5f * U, WHITE, Paint.Align.CENTER);
            // Le téléphone dessiné suit TON inclinaison en direct + flèche vers la cible
            drawPhone(c, mid, 60 * U, 10 * U, clamp(tilt.getSteer() * 6f, -60, 60), WHITE);
            drawText(c, left ? "◄" : "►", left ? mid - 28 * U : mid + 28 * U, 64 * U, 10 * U, PINK, Paint.Align.CENTER);
            drawTiltBar(c, 84 * U, tilt.getSteer(), left ? -1 : 1);
        } else if (tutoStep == 2) {
            drawText(c, "ÉTAPE 3/3", mid, 20 * U, 3.5f * U, CYAN, Paint.Align.CENTER);
            drawText(c, "CALIBRATION DU MICRO...", mid, 40 * U, 5 * U, WHITE, Paint.Align.CENTER);
            drawText(c, "ne fais pas de bruit 2 secondes", mid, 48 * U, 3.5f * U, WHITE, Paint.Align.CENTER);
            drawProgress(c, 60 * U, tutoTimer / 2f, CYAN);
        } else if (tutoStep == 3) {
            drawText(c, "ÉTAPE 3/3", mid, 20 * U, 3.5f * U, CYAN, Paint.Align.CENTER);
            drawNeonTitle(c, "SOUFFLE DANS LE MICRO", mid, 34 * U, 7 * U);
            drawText(c, "un souffle long (le micro est souvent sur la tranche du bas)", mid, 41 * U, 3 * U, WHITE, Paint.Align.CENTER);
            float ratio = clamp(breath.getRatio(), 0, 1);
            drawMic(c, mid, 60 * U, 12 * U, ratio);
            drawProgress(c, 82 * U, ratio * 0.75f + clamp(blowTime / BLOW_HOLD, 0, 1) * 0.25f, PINK);
            if (tutoTimer > TUTO_SKIP_DELAY) drawButtons(c, new UiButton[]{btnTutoSkip});
        } else {
            drawNeonTitle(c, "BRAVO !", mid, 38 * U, 13 * U);
            drawText(c, "Tu sais tourner et booster. Évite les obstacles !", mid, 52 * U, 3.8f * U, WHITE, Paint.Align.CENTER);
            drawButtons(c, new UiButton[]{btnTutoNext});
        }
    }

    // =====================================================================
    // 8. PETITS OUTILS DE DESSIN
    // =====================================================================

    private void drawText(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
        c.drawText(s, x, y, text);
    }

    /** Texte rose avec une "ombre" cyan décalée => effet néon rétro. */
    private void drawNeonTitle(Canvas c, String s, float x, float y, float size) {
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(CYAN);
        c.drawText(s, x + size * 0.05f, y + size * 0.05f, text);
        text.setColor(PINK);
        c.drawText(s, x, y, text);
    }

    private void drawButtons(Canvas c, UiButton[] buttons) {
        for (UiButton b : buttons) b.draw(c, fill, stroke, text, b == pressed, U);
    }

    /** Cœur = 2 cercles + 1 triangle, dans un carré de taille 1 (agrandi au dessin). */
    private void buildHeart() {
        heart.reset();
        heart.addCircle(-0.25f, -0.1f, 0.27f, Path.Direction.CW);
        heart.addCircle(0.25f, -0.1f, 0.27f, Path.Direction.CW);
        heart.moveTo(-0.51f, -0.02f);
        heart.lineTo(0.51f, -0.02f);
        heart.lineTo(0f, 0.55f);
        heart.close();
    }

    private void drawHeart(Canvas c, float x, float y, float size, int color, int alpha) {
        c.save();
        c.translate(x, y);
        c.scale(size, size);
        fill.setColor(color);
        fill.setAlpha(alpha);
        c.drawPath(heart, fill);
        c.restore();
    }

    /** Téléphone en paysage, tourné de "angle" degrés. */
    private void drawPhone(Canvas c, float x, float y, float s, float angle, int color) {
        c.save();
        c.rotate(angle, x, y);
        tmpRect.set(x - s, y - s * 0.55f, x + s, y + s * 0.55f);
        fill.setColor(0xFF140433);
        c.drawRoundRect(tmpRect, s * 0.15f, s * 0.15f, fill);
        stroke.setColor(color);
        stroke.setStrokeWidth(0.6f * U);
        c.drawRoundRect(tmpRect, s * 0.15f, s * 0.15f, stroke);
        fill.setColor(color);
        c.drawCircle(x - s * 0.85f, y, s * 0.05f, fill);
        c.restore();
    }

    /** Micro avec des "ondes" dont l'intensité suit le volume capté. */
    private void drawMic(Canvas c, float x, float y, float s, float intensity) {
        fill.setColor(CYAN);
        tmpRect.set(x - s * 0.22f, y - s * 0.6f, x + s * 0.22f, y + s * 0.05f);
        c.drawRoundRect(tmpRect, s * 0.22f, s * 0.22f, fill);
        stroke.setColor(CYAN);
        stroke.setStrokeWidth(0.6f * U);
        tmpRect.set(x - s * 0.36f, y - s * 0.35f, x + s * 0.36f, y + s * 0.25f);
        c.drawArc(tmpRect, 0, 180, false, stroke);
        c.drawLine(x, y + s * 0.25f, x, y + s * 0.5f, stroke);
        c.drawLine(x - s * 0.2f, y + s * 0.5f, x + s * 0.2f, y + s * 0.5f, stroke);
        stroke.setColor(PINK);
        for (int i = 1; i <= 3; i++) {
            stroke.setAlpha((int) (255 * clamp(intensity * 1.5f - (i - 1) * 0.35f, 0, 1)));
            float r = s * (0.45f + i * 0.18f);
            tmpRect.set(x - r, y - s * 0.3f - r, x + r, y - s * 0.3f + r);
            c.drawArc(tmpRect, -40, 80, false, stroke);
            c.drawArc(tmpRect, 140, 80, false, stroke);
        }
    }

    /** Barre d'inclinaison du tuto : zone cible colorée + curseur = inclinaison actuelle. */
    private void drawTiltBar(Canvas c, float y, float steer, int targetDir) {
        float mid = getWidth() / 2f, half = getWidth() * 0.25f;
        float scale = half / 6f; // 6 m/s² = bout de la barre
        stroke.setStrokeWidth(1.2f * U);
        stroke.setColor(0xFF3A2A5A);
        c.drawLine(mid - half, y, mid + half, y, stroke);
        stroke.setColor(PINK);
        if (targetDir < 0) c.drawLine(mid - half, y, mid - TILT_TRIGGER * scale, y, stroke);
        else c.drawLine(mid + TILT_TRIGGER * scale, y, mid + half, y, stroke);
        fill.setColor(WHITE);
        c.drawCircle(mid + clamp(steer, -6, 6) * scale, y, 2.5f * U, fill);
    }

    private void drawProgress(Canvas c, float y, float ratio, int color) {
        float mid = getWidth() / 2f, half = getWidth() * 0.25f;
        tmpRect.set(mid - half, y - 2 * U, mid + half, y + 2 * U);
        fill.setColor(0xFF3A2A5A);
        c.drawRoundRect(tmpRect, 2 * U, 2 * U, fill);
        tmpRect.right = tmpRect.left + 2 * half * clamp(ratio, 0, 1);
        fill.setColor(color);
        c.drawRoundRect(tmpRect, 2 * U, 2 * U, fill);
    }

    private static String stars(int n) {
        if (n == 0) return "-";
        if (n > 6) return "★ x" + n;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('★');
        return sb.toString();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // =====================================================================
    // 9. TOUCH (sujet §4.6)
    // =====================================================================

    private UiButton[] currentButtons() {
        switch (state) {
            case MENU: return menuButtons;
            case LEVEL_SELECT: return levelButtons;
            case LEADERBOARD:
            case SETTINGS: return backOnly;
            case HELP: return helpButtons;
            case PAUSED: return pauseButtons;
            case GAME_OVER: return gameOverButtons;
            case TUTORIAL:
                if (tutoStep == 4) return new UiButton[]{btnTutoNext};
                if (tutoStep == 3 && tutoTimer > TUTO_SKIP_DELAY) return new UiButton[]{btnTutoSkip};
                return none;
            default: return none;
        }
    }

    private UiButton findButton(float x, float y) {
        for (UiButton b : currentButtons()) if (b.contains(x, y)) return b;
        return null;
    }

    /**
     * Dossier §6a : un bouton s'active quand on RELÂCHE le doigt dessus (ACTION_UP), pas quand on
     * appuie. Si on glisse hors du bouton avant de lâcher, l'action est annulée => moins d'erreurs.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (waitingPermission) return true;
        float x = e.getX(), y = e.getY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = null;
                if (state == State.PLAYING || state == State.COUNTDOWN) {
                    // En jeu : seul tap = pause (zone élargie de 4 U autour du bouton : cible facile)
                    tmpRect.set(pauseRect);
                    tmpRect.inset(-4 * U, -4 * U);
                    if (tmpRect.contains(x, y)) {
                        audio.click();
                        pauseGame();
                    } else if (state == State.PLAYING && !tilt.isAvailable()) {
                        // Cas limite : pas d'accéléromètre => moitié gauche/droite de l'écran
                        world.moveLane(x < getWidth() / 2f ? -1 : 1);
                    }
                    return true;
                }
                if (state == State.SETTINGS) {
                    for (int i = 0; i < 3; i++) {
                        if (sliderTracks[i].contains(x, y)) {
                            draggingSlider = i;
                            setSliderFromX(i, x);
                            return true;
                        }
                    }
                }
                pressed = findButton(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (draggingSlider >= 0) setSliderFromX(draggingSlider, x);
                else if (pressed != null && !pressed.contains(x, y)) pressed = null; // annulation
                return true;

            case MotionEvent.ACTION_UP:
                if (draggingSlider >= 0) {
                    draggingSlider = -1;
                    audio.applySettings();
                    audio.click(); // aperçu du nouveau volume
                    return true;
                }
                UiButton b = findButton(x, y);
                if (b != null && b == pressed) {
                    audio.click();
                    onButton(b);
                } else if (state == State.SETTINGS && pressed == null) {
                    if (vibRow.contains(x, y)) {
                        save.setVibrationOn(!save.isVibrationOn());
                        audio.vibrate(80); // on sent tout de suite si c'est activé
                    } else if (initialsRow.contains(x, y)) {
                        showInitialsDialog("Tes initiales :", null);
                    }
                }
                pressed = null;
                return true;

            case MotionEvent.ACTION_CANCEL:
                pressed = null;
                draggingSlider = -1;
                return true;
        }
        return true;
    }

    private void setSliderFromX(int index, float x) {
        RectF r = sliderTracks[index];
        int v = Math.round(clamp((x - r.left) / (r.right - r.left), 0, 1) * 100);
        if (index == 0) save.setSoundVolume(v);
        else if (index == 1) { save.setMusicVolume(v); audio.applyMusicVolume(); }
        else { save.setMicSensitivity(v); breath.setSensitivity(v); }
    }

    private void onButton(UiButton b) {
        if (b == btnPlay) setState(State.LEVEL_SELECT);
        else if (b == btnRanking) setState(State.LEADERBOARD);
        else if (b == btnSettings) setState(State.SETTINGS);
        else if (b == btnHelp) setState(State.HELP);
        else if (b == btnBack) setState(State.MENU);
        else if (b == btnLevel1) startGame(1);
        else if (b == btnLevel2) startGame(2);
        else if (b == btnTuto) startTutorial(true);
        else if (b == btnTutoNext || b == btnTutoSkip) finishTutorial();
        else if (b == btnResume) resumeGame();
        else if (b == btnRestart || b == btnReplay) startGame(selectedLevel);
        else if (b == btnMenu || b == btnGoMenu) goToMenu();
    }
}
