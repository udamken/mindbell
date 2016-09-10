/*******************************************************************************
 * MindBell - Aims to give you a support for staying mindful in a busy life -
 *            for remembering what really counts
 *
 *     Copyright (C) 2014-2016 Uwe Damken
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.googlecode.mindbell;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.googlecode.mindbell.accessors.ContextAccessor;
import com.googlecode.mindbell.accessors.PrefsAccessor;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Regularly show current state of meditation by updating a time slice drawing regularly.
 */
public class CountdownView extends View {
    public static final long ONE_SECOND = 1000;

    private long rampUpStartingTimeMillis;
    private long meditationStartingTimeMillis;
    private long meditationEndingTimeMillis;

    // Style information about the time slice to be drawn
    private Paint timeSlicePaint = new Paint();
    private Paint backgroundPaint = new Paint();

    // Style information about the countdown string to be displayed during ramp-up
    private TextPaint textPaintRampUp;

    // Style information about the countdown string to be displayed during meditation
    private TextPaint textPaintMeditation;

    // Bound of the text to be displayed
    Rect textBounds = new Rect();

    // Time to update the display with a time slice drawing regularly
    private Timer displayUpdateTimer;

    // Dimensions of the bowl circle to be drawn
    private RectF bowlDimensions;

    // Dimensions of the gap circle to be drawn "between" bowl and time slice
    private RectF gapDimensions;

    // Dimensions of the time slice to be drawn
    private RectF timeSliceDimensions;

    // Dimesions ot the rectangle to be drawn to crop the of the bowl circle
    private RectF cropDimensions;

    // X part of the center
    private int centerX;

    // Y part of the center
    private int centerY;

    public CountdownView(Context context) {
        super(context);
        init(null, 0);
    }

    public CountdownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public CountdownView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {

        // Load application and system attributes
        final TypedArray app = getContext().obtainStyledAttributes(attrs, R.styleable.CountdownView, defStyle, 0);
        final TypedArray sys = getContext().obtainStyledAttributes(attrs, new int[] { android.R.attr.background });

        // Set up a TextPaint object for writing the remaining time onto the time slice
        timeSlicePaint.setColor(Color.WHITE);
        backgroundPaint.setColor(sys.getColor(0, Color.GREEN));

        // Set up a TextPaint object for writing the remaining time onto the time slice during ramp-up
        textPaintRampUp = new TextPaint();
        textPaintRampUp.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaintRampUp.setColor(app.getColor(R.styleable.CountdownView_rampUpColor, Color.RED));
        textPaintRampUp.setTextSize(app.getDimension(R.styleable.CountdownView_rampUpSize, 100));

        // Set up a TextPaint object for writing the remaining time onto the time slice during meditation
        textPaintMeditation = new TextPaint();
        textPaintMeditation.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaintMeditation.setColor(app.getColor(R.styleable.CountdownView_meditationColor, Color.RED));
        textPaintMeditation.setTextSize(app.getDimension(R.styleable.CountdownView_meditationSize, 100));
    }

    /**
     * Starts displaying state of meditation by resetting and starting timers.
     */
    public void startDisplayUpdateTimer(ContextAccessor contextAccessor) {

        // Retrieve and store meditation times
        PrefsAccessor prefs = contextAccessor.getPrefs();
        this.rampUpStartingTimeMillis = prefs.getRampUpStartingTimeMillis();
        this.meditationStartingTimeMillis = prefs.getMeditationStartingTimeMillis();
        this.meditationEndingTimeMillis = prefs.getMeditationEndingTimeMillis();

        displayUpdateTimer = new Timer();

        displayUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                postInvalidate(); // => onDraw()
            }
        }, 0, ONE_SECOND); // draw at once and then every second

        MindBell.logDebug("Countdown timers started");
    }

    /**
     * Stops a meditation by stopping timers and releasing wake lock
     */
    public void stopDisplayUpdateTimer() {
        if (displayUpdateTimer != null) {
            displayUpdateTimer.cancel();
            displayUpdateTimer = null;
        }
        MindBell.logDebug("Countdown timers stopped");
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Retrieve view dimensions and calculate dimensions of all shapes to be drawn
        calculateDimensions();
    }

    /**
     * Retrieve view dimensions and calculate dimensions of all shapes to be drawn
     */
    private void calculateDimensions() {

        // Get view dimensions
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int contentHeight = getHeight() - paddingTop - paddingBottom;

        // Derive inner square dimensions for the rectangular view
        if (contentHeight == contentWidth) {
            // nothing to correct for a square canvas
        } else if (contentHeight > contentWidth) {
            int difference = contentHeight - contentWidth;
            paddingTop += difference / 2;
            paddingBottom += difference - difference / 2;
            contentHeight = getHeight() - paddingTop - paddingBottom;
        } else {
            int difference = contentWidth - contentHeight;
            paddingLeft += difference / 2;
            paddingRight += difference - difference / 2;
            contentWidth = getWidth() - paddingLeft - paddingRight;
        }

        // Calculate effective outer bounds of the square to be drawn into
        int paddedLeft = getLeft() + paddingLeft;
        int paddedTop = getTop() + paddingTop;
        int paddedRight = getRight() - paddingRight;
        int paddedBottom = getBottom() - paddingBottom;

        // Calculate center ot the square
        centerX = paddedLeft + contentWidth / 2;
        centerY = paddedTop + contentHeight / 2;

        // Calculate insets for the gap and time slice circle
        float gapInset = Math.min(paddingLeft, paddingTop) * 1.2f;
        float timeSliceInset = gapInset * 1.7f;

        // Calculate dimensions for the bowl, gap and time slice circle plus the crop rectangle
        bowlDimensions = new RectF(paddedLeft, paddedTop, paddedRight, paddedBottom);
        gapDimensions = inset(bowlDimensions, gapInset);
        timeSliceDimensions = inset(bowlDimensions, timeSliceInset);
        cropDimensions = new RectF(paddedLeft, paddedTop, paddedRight, centerY - gapInset);
    }

    /**
     * Returns a rectangle based on the given one but inset as specified.
     */
    private RectF inset(RectF outer, float inset) {
        return new RectF(outer.left + inset, outer.top + inset, outer.right - inset, outer.bottom - inset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean rampUp;
        long currentTimeMillis = Math.min(System.currentTimeMillis(), meditationEndingTimeMillis);
        long meditationSeconds;
        long elapsedMeditationSeconds;
        if (currentTimeMillis < meditationStartingTimeMillis) {
            rampUp = true;
            meditationSeconds = (meditationStartingTimeMillis - rampUpStartingTimeMillis) / ONE_SECOND;
            elapsedMeditationSeconds = (currentTimeMillis - rampUpStartingTimeMillis) / ONE_SECOND;
        } else if (currentTimeMillis < meditationEndingTimeMillis) {
            rampUp = false;
            meditationSeconds = (meditationEndingTimeMillis - meditationStartingTimeMillis) / ONE_SECOND;
            elapsedMeditationSeconds = (currentTimeMillis - meditationStartingTimeMillis) / ONE_SECOND;
        } else  {
            stopDisplayUpdateTimer(); // meditation is over therefore stop further drawing
            rampUp = false;
            meditationSeconds = (meditationEndingTimeMillis - meditationStartingTimeMillis) / ONE_SECOND;
            elapsedMeditationSeconds = meditationSeconds;
        }

        // Draw bowl by drawing a bowl circle, a gap circle and a rectangle to crop the top
        canvas.drawArc(bowlDimensions, -90, 360, true, timeSlicePaint);
        canvas.drawArc(gapDimensions, -90, 360, true, backgroundPaint);
        canvas.drawRect(cropDimensions, backgroundPaint);

        // Always draw a vertical line just in case elapsedMeditationSeconds doesn't make a sector with more than zero degrees
        canvas.drawLine(centerX, centerY, centerX, timeSliceDimensions.top, timeSlicePaint);

        // Draw sector that represents the elapsed seconds versus the total number of seconds
        if (!rampUp && elapsedMeditationSeconds > 0) {
            canvas.drawArc(timeSliceDimensions, -90, elapsedMeditationSeconds * 360 / (float) meditationSeconds, true, timeSlicePaint);
        }

        // Draw the text on top of the circle
        String countdownString = getCountdownString(meditationSeconds, elapsedMeditationSeconds, rampUp);
        TextPaint textPaint = (rampUp) ? textPaintRampUp : textPaintMeditation;
        textPaint.getTextBounds(countdownString, 0, countdownString.length(), textBounds);
        float textX = centerX - textBounds.exactCenterX();
        float textY = centerY - textBounds.exactCenterY();
        canvas.drawText(countdownString, textX, textY, textPaint);
    }

    /**
     * Returns the string with the remaining time to be displayed.
     */
    private String getCountdownString(long seconds, long elapsedSeconds, boolean rampUp) {
        StringBuilder sb = new StringBuilder();
        if (rampUp) {
            sb.append(seconds - elapsedSeconds);
        } else {
            long remainingBeganMinutes = (long) Math.ceil((seconds - elapsedSeconds) / 60d);
            sb.append(remainingBeganMinutes);
        }
        return sb.toString();
    }

}
