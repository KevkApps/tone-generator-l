/*
 * Copyright (C) 2017 Kevin Kasamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sourceliteapps.tonegeneratorl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.support.v7.widget.AppCompatImageView;

public class RotaryKnobView extends AppCompatImageView implements View.OnTouchListener {

    private float angle = 0f;
    private float theta_old=0f;

    Bitmap bitmap;
    Bitmap background;

    float width = 100;
    float height = 100;

    RotaryKnobListener listener;

    public void setWidthAndHeight(float width, float height) {

        this.width = width;
        this.height = height;

        Bitmap bitmapTmp = BitmapFactory.decodeResource(getResources(), R.drawable.jog);
        bitmap = Bitmap.createScaledBitmap(bitmapTmp, (int) width, (int) height, false);
        Bitmap backgroundTmp = BitmapFactory.decodeResource(getResources(), R.drawable.backjog);
        background = Bitmap.createScaledBitmap(backgroundTmp, (int) width, (int) height, false);
        bitmapTmp.recycle();
        backgroundTmp.recycle();
        bitmapTmp = null;
        backgroundTmp = null;

    }

    public void cleanUp() {

        bitmap.recycle();
        background.recycle();
        bitmap = null;
        background= null;

    }

    public void setAngle(float angleSaved) {

        angle = angleSaved;

    }

    public RotaryKnobView(Context context) {
        super(context);

        setOnTouchListener(this);

    }

    public RotaryKnobView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnTouchListener(this);

    }

    public RotaryKnobView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOnTouchListener(this);

    }

    protected void onDraw(Canvas canvas) {

        Matrix matrix = new Matrix();

        matrix.postRotate(angle, ((float)bitmap.getWidth()/2), ((float)bitmap.getHeight()/2));

        canvas.drawBitmap(background, new Matrix(), new Paint());

        canvas.drawBitmap(bitmap, matrix, new Paint());

        canvas.getWidth();

        super.onDraw(canvas);

    } // onDraw

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        int action = event.getAction();
        int actionCode = action & MotionEvent.ACTION_MASK;
        if (actionCode == MotionEvent.ACTION_POINTER_DOWN) {

            float x = event.getX(0);
            float y = event.getY(0);
            theta_old = getTheta(x, y);

        } else if (actionCode == MotionEvent.ACTION_MOVE) {

            invalidate();

            float x = event.getX(0);
            float y = event.getY(0);

            float theta = getTheta(x,y);
            float delta_theta = theta - theta_old;

            theta_old = theta;

            int direction = (delta_theta > 0) ? 1 : -1;
            angle += 3*direction;

            notifyListener(direction, angle);

        }

        return true;
    } // onTouch

    private float getTheta(float x, float y) {

        float sx = x - (width / 2.0f);
        float sy = y - (height / 2.0f);

        float length = (float)Math.sqrt( sx*sx + sy*sy);
        float nx = sx / length;
        float ny = sy / length;
        float theta = (float)Math.atan2( ny, nx );

        final float rad2deg = (float)(180.0/Math.PI);
        float theta2 = theta*rad2deg;

        return (theta2 < 0) ? theta2 + 360.0f : theta2;

    } // getTheta

    public interface RotaryKnobListener {

        public void onKnobChanged(int arg, float angle);

    } // RotaryKnobListener

    public void setKnobListener(RotaryKnobListener l) {

        listener = l;

    } // setKnobListener

    private void notifyListener(int arg, float angle) {

        if (listener != null) {

            listener.onKnobChanged(arg, angle);

        }
    }

} // RotaryKnobView