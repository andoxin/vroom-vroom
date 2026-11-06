package com.example.vroomvroom;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

// Bouton dessiné sur le Canvas ; primary = action fréquente, plus visible.
public class UiButton {
    public final RectF rect = new RectF();
    public final String label;
    public final boolean primary;
    public float textScale = 0.42f;

    // Crée un bouton.
    public UiButton(String label, boolean primary) {
        this.label = label;
        this.primary = primary;
    }

    // Place le bouton.
    public void set(float left, float top, float right, float bottom) {
        rect.set(left, top, right, bottom);
    }

    // Point dans le bouton ?
    public boolean contains(float x, float y) {
        return rect.contains(x, y);
    }

    // Dessine le bouton.
    public void draw(Canvas c, Paint fill, Paint stroke, Paint text, boolean pressed, float u) {
        float r = 2.5f * u;
        fill.setStyle(Paint.Style.FILL);
        if (primary) {
            fill.setColor(pressed ? 0xFFFF85C8 : GameView.PINK);
            c.drawRoundRect(rect, r, r, fill);
        } else {
            fill.setColor(pressed ? 0x662DE2E6 : 0x99140433);
            c.drawRoundRect(rect, r, r, fill);
            stroke.setColor(GameView.CYAN);
            stroke.setStrokeWidth(0.5f * u);
            c.drawRoundRect(rect, r, r, stroke);
        }
        float size = rect.height() * textScale;
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        float maxW = rect.width() * 0.88f;
        float tw = text.measureText(label);
        if (tw > maxW) text.setTextSize(size * maxW / tw);
        text.setColor(primary ? 0xFFFFFFFF : GameView.CYAN);
        c.drawText(label, rect.centerX(), rect.centerY() - (text.descent() + text.ascent()) / 2f, text);
    }
}
