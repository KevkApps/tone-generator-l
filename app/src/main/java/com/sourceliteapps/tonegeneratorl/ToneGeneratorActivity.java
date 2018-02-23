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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class ToneGeneratorActivity extends Activity implements RotaryKnobView.RotaryKnobListener {

    private double freqOfTone = 1000; // frequency in hz
    private SharedPreferences sharedPrefs;
    private SharedPreferences.Editor editor;
    private FrameLayout rootFrameLayout;
    private LinearLayout linearLayoutOne;
    private ImageView imageViewOne;
    private FrameLayout frameLayoutJogView;
    private ImageView imageViewTwo;
    private Bitmap buttonBitmapOff;
    private Bitmap buttonBitmapOn;
    private RotaryKnobView rotaryKnobView;
    public static int KNOB_INTERVAL = 5; // interval between when the knob changes the frequency
    public static int INPUT_REPITITION = 3;
    private int knobRotationCounter = 0;
    private float angle;
    private int frequencyPosition = 0;
    private int inputRepetitionCounter = 0;
    private static HashMap<Integer, FrequencyAndName> frequencySets = new HashMap<Integer, FrequencyAndName>();
    private TextView textView;
    private IncomingHandler handler;
    private HandlerThread handlerThread;
    boolean mBound = false;
    private Messenger mServiceMessenger = null; // messenger for sending messages to the service
    private Messenger mClientMessenger = null; // messenger for receiving messages from the service
    private boolean playing = false;

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);

    } // onSavedInstanceState

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_generator);

        // messenger for sending messages to the service
        Messenger mServiceMessenger = null;

        if(savedInstanceState != null) {

            playing = savedInstanceState.getBoolean("playing", false);

        }

        handlerThread = new HandlerThread("IPChandlerThread");
        handlerThread.start();
        handler = new IncomingHandler(handlerThread);
        mClientMessenger = new Messenger(handler);

        bindService();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buttonBitmapOff = BitmapFactory.decodeResource(getResources(), R.drawable.logo_off);
        buttonBitmapOn = BitmapFactory.decodeResource(getResources(), R.drawable.logo_on);

        frequencySets.put(0, new FrequencyAndName(31, "31 Hz"));
        frequencySets.put(1, new FrequencyAndName(63, "63 Hz"));
        frequencySets.put(2, new FrequencyAndName(125, "125 Hz"));
        frequencySets.put(3, new FrequencyAndName(250, "250 Hz"));
        frequencySets.put(4, new FrequencyAndName(500, "500 Hz"));
        frequencySets.put(5, new FrequencyAndName(1000, "1000 Hz"));
        frequencySets.put(6, new FrequencyAndName(2000, "2000 Hz"));
        frequencySets.put(7, new FrequencyAndName(4000, "4000 Hz"));
        frequencySets.put(8, new FrequencyAndName(8000, "8000 Hz"));
        frequencySets.put(9, new FrequencyAndName(16000, "16000 Hz"));

        rootFrameLayout = (FrameLayout) findViewById(R.id.rootFrameLayout);

        linearLayoutOne = (LinearLayout) findViewById(R.id.linearLayout1);

        frameLayoutJogView = (FrameLayout) findViewById(R.id.jog_view_container);

        imageViewTwo = (ImageView) findViewById(R.id.imageView2);

        rotaryKnobView = new RotaryKnobView(ToneGeneratorActivity.this);

        textView = (TextView) findViewById(R.id.textView1);
        textView.setMaxLines(1);

        sharedPrefs = getSharedPreferences("toneGenerator", MODE_PRIVATE);

        if (!sharedPrefs.getBoolean("previouslyInstalled", false)) {

            editor = sharedPrefs.edit();
            frequencyPosition = 4;
            editor.putBoolean("previouslyInstalled", true);
            editor.putInt("frequencyPosition", 4);
            textView.setText(frequencySets.get(4).label);
            editor.commit();

        }


        imageViewOne = (ImageView) findViewById(R.id.imageView1);

        imageViewOne.setImageBitmap(buttonBitmapOff);


        rotaryKnobView = new RotaryKnobView(this);
        frameLayoutJogView.addView(rotaryKnobView);
        rotaryKnobView.setKnobListener(ToneGeneratorActivity.this);

        rootFrameLayout.post(new Runnable() {

            @Override
            public void run() {

                int screenState = getResources().getConfiguration().orientation;
                int screenWidth = rootFrameLayout.getWidth();
                int screenHeight = rootFrameLayout.getHeight();

                linearLayoutOne.getLayoutParams().width = (int) Math.round(0.85 * (double) screenWidth);
                linearLayoutOne.getLayoutParams().height = (int) Math.round(0.85 * (double) screenHeight);

                if (screenState == Configuration.ORIENTATION_PORTRAIT) {

                    imageViewOne.getLayoutParams().width = (int) Math.round(0.75 * (double) screenWidth);
                    imageViewOne.getLayoutParams().height = (int) Math.round(imageViewOne.getLayoutParams().width);


                    imageViewTwo.getLayoutParams().width = (int) Math.round(0.75 * (double) screenWidth);
                    imageViewTwo.getLayoutParams().height = (int) Math.round((imageViewOne.getLayoutParams().width - ((int) Math.round(0.18 * (double) screenWidth))));

                    rotaryKnobView.getLayoutParams().width = (int) Math.round(0.3 * (double) screenWidth);
                    rotaryKnobView.getLayoutParams().height = rotaryKnobView.getLayoutParams().width;

                    rotaryKnobView.setWidthAndHeight(rotaryKnobView.getLayoutParams().width,rotaryKnobView.getLayoutParams().height);

                    int numberInPX = (int) Math.round(0.08 * (double) screenWidth);

                    float scale = getResources().getDisplayMetrics().density;
                    int numberInDensityPixels = (int) Math.round(numberInPX / scale);
                    textView.setTextSize(numberInDensityPixels);


                } else if (screenState == Configuration.ORIENTATION_LANDSCAPE) {

                    imageViewOne.getLayoutParams().width = (int) Math.round(0.75 * (double) screenHeight);
                    imageViewOne.getLayoutParams().height = (int) Math.round(imageViewOne.getLayoutParams().height);


                    imageViewTwo.getLayoutParams().width = (int) Math.round(0.75 * (double) screenHeight);
                    imageViewTwo.getLayoutParams().height = (int) Math.round((imageViewTwo.getLayoutParams().width - ((int) Math.round(0.18 * (double) screenHeight))));

                    rotaryKnobView.getLayoutParams().width = (int) Math.round(0.45 * (double) screenHeight);
                    rotaryKnobView.getLayoutParams().height = rotaryKnobView.getLayoutParams().width;

                    rotaryKnobView.setWidthAndHeight(rotaryKnobView.getLayoutParams().width,rotaryKnobView.getLayoutParams().height);

                    int numberInPX = (int) Math.round(0.08 * (double) screenHeight);

                    float scale = getResources().getDisplayMetrics().density;
                    int numberInDensityPixels = (int) Math.round(numberInPX / scale);
                    textView.setTextSize(numberInDensityPixels);

                }
            }

        });

        imageViewTwo.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

            if(mBound) {

                setFreqOfTone();
                startSound();

            }

            } // onClick
        });

    } // onCreate

    // Method used for binding with the service
    public boolean bindService() {

        // implicit start, Intent that must be defined in AndroidManifest.xml
        Intent intent = new Intent("com.example.ipcserviceseparateprocessexample.ACTION_BIND");
        intent.setPackage(this.getPackageName());

        // explicit start, same result as above however this is safer for security than the implicit start
        // Intent intent = new Intent(ToneGeneratorActivity.this, GeneratorService.class);
        // intent.setPackage(this.getPackageName());

        startService(intent); // must call start service before bind service or the onTaskRemoved() method in the Service will not be called

        return getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    } // bindService

    public void unbindService() {
        if (mBound) {
            getApplicationContext().unbindService(mConnection);

            Toast.makeText(ToneGeneratorActivity.this, "unbindService called",
                    Toast.LENGTH_SHORT).show();

            mBound = false;
        }

    } // unbindService

    public void checkPlayingStatus() {

        if(!mBound) {
            return;
        }

        Message msg = Message.obtain(null, 4, 0, 0);

        msg.replyTo = mClientMessenger;

        Bundle bund = new Bundle();
        bund.putDouble("freqOfTone", freqOfTone);
        msg.setData(bund);

        try {

            mServiceMessenger.send(msg);

        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    // Handler for incoming messages
    class IncomingHandler extends Handler {

        public IncomingHandler(HandlerThread thr) {
            super(thr.getLooper());

        } // IncomingHandler

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Bundle bund = msg.getData();

            switch (msg.what) {
                case 1: {

                    playing = msg.getData().getBoolean("playing", false);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if(playing) {

                                imageViewOne.setImageBitmap(buttonBitmapOn);

                            } else {

                                imageViewOne.setImageBitmap(buttonBitmapOff);
                            }

                        }
                    });

                } // case 1

                break;

                case 2: {

                    playing = msg.getData().getBoolean("playing", false);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if(playing) {

                                imageViewOne.setImageBitmap(buttonBitmapOn);

                            } else {

                                imageViewOne.setImageBitmap(buttonBitmapOff);
                            }

                        }
                    });

                } // case 2

                default:
                    super.handleMessage(msg);

            } // switch for msg.what

        } // handleMessage
    }

    // Class used for interacting with the main service interface
    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {

            mServiceMessenger = new Messenger(service);

            mBound = true;

            checkPlaying();

        } // onServiceConnected

        public void onServiceDisconnected(ComponentName className) {

            mServiceMessenger = null;

        }
    };

    @Override
    public void onKnobChanged(int arg, float angle) {

        this.angle = angle;

        moveMent(); // if dial is moved while tone is playing

        if (knobRotationCounter == KNOB_INTERVAL) {

            if(inputRepetitionCounter == INPUT_REPITITION) {

                if (arg > 0) {

                    // rotated right

                    if (frequencyPosition < 9) {

                        frequencyPosition++;

                        if(frequencySets.get(frequencyPosition) != null) {
                            textView.setText(frequencySets.get(frequencyPosition).label);
                            freqOfTone = (double) frequencySets.get(frequencyPosition).frequency;
                        }

                    } else {

                        if(frequencySets.get(frequencyPosition) != null) {
                            textView.setText(frequencySets.get(frequencyPosition).label);
                            freqOfTone = (double) frequencySets.get(frequencyPosition).frequency;
                        }

                    }


                } else {

                    // rotated left

                    if (frequencyPosition > 0) {

                        frequencyPosition--;

                        if(frequencySets.get(frequencyPosition) != null) {
                            textView.setText(frequencySets.get(frequencyPosition).label);
                            freqOfTone = (double) frequencySets.get(frequencyPosition).frequency;
                        }

                    } else {

                        if(frequencySets.get(frequencyPosition) != null) {
                            textView.setText(frequencySets.get(frequencyPosition).label);
                            freqOfTone = (double) frequencySets.get(frequencyPosition).frequency;
                        }

                    }

                } // if arg > 0

                inputRepetitionCounter = 0;

            } else {

                inputRepetitionCounter++;
            }

            knobRotationCounter = 0;

        } else {

            knobRotationCounter++;

        }

    } // onKnobChanged

    // using a int of 1 for the what variable
    public void setFreqOfTone() {

        if(!mBound) {
            return;
        }

        Message msg = Message.obtain(null, 1, 0, 0);

        // data is sent in bundle
        Bundle bund = new Bundle();
        bund.putDouble("freqOfTone", freqOfTone);
        msg.setData(bund);

        try {

                mServiceMessenger.send(msg);


        } catch (RemoteException e) {

        }

    } // sendOtherMessage

    // using a int of 2 for the what variable
    // a different action can be taken
    public void startSound() {

        if(!mBound) {
            return;
        }

        Message msg = Message.obtain(null, 2, 0, 0);

        msg.replyTo = mClientMessenger;

        Bundle bund = new Bundle();
        bund.putDouble("freqOfTone", freqOfTone);
        msg.setData(bund);

        try {

            mServiceMessenger.send(msg);

        } catch (RemoteException e) {
            e.printStackTrace();
        }

    } // sendOtherMessage

    public void checkPlaying() {

        if(!mBound) {
            return;
        }

        Message msg = Message.obtain(null, 4, 0, 0);

        msg.replyTo = mClientMessenger;

        Bundle bund = new Bundle();
        bund.putDouble("freqOfTone", freqOfTone);
        msg.setData(bund);

        try {

            mServiceMessenger.send(msg);

        } catch (RemoteException e) {
            e.printStackTrace();
        }

    } // sendOtherMessage

    public void stopSound() {

        if(!mBound) {
            return;
        }

        Message msg = Message.obtain(null, 3, 0, 0);

        msg.replyTo = mClientMessenger;

        Bundle bund = new Bundle();
        msg.setData(bund);

        try {

            mServiceMessenger.send(msg);

        } catch (RemoteException e) {
            e.printStackTrace();
        }

    } // sendOtherMessage

    @Override
    protected void onResume() {
        super.onResume();

        inputRepetitionCounter = 0;
        knobRotationCounter = 0;
        frequencyPosition = sharedPrefs.getInt("frequencyPosition", 0);
        angle = sharedPrefs.getFloat("angle", 0.0f);

        if(frequencySets.get(frequencyPosition) != null) {
            textView.setText(frequencySets.get(frequencyPosition).label);
            freqOfTone = (double) frequencySets.get(frequencyPosition).frequency;
        }

        if(rotaryKnobView!=null) {
            rotaryKnobView.setAngle(angle);
        }

    } // onResume

    @Override
    protected void onPause() {
        super.onPause();

        checkPlaying();

        editor = sharedPrefs.edit();
        editor.putInt("frequencyPosition", frequencyPosition);
        editor.putFloat("angle", angle);
        editor.commit();

        imageViewOne.setImageBitmap(buttonBitmapOff);

    } // onPause

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //unbindService();

        buttonBitmapOff.recycle();
        buttonBitmapOn.recycle();
        buttonBitmapOff = null;
        buttonBitmapOn = null;
        rotaryKnobView.cleanUp();

    }

    public void moveMent() {

            stopSound();

        if(playing) {
            imageViewOne.setImageBitmap(buttonBitmapOff);
            playing = false;
        }

    } // Movement

    public class FrequencyAndName {

        int frequency = 0;
        String label;

        FrequencyAndName(int frequency, String label) {

            this.frequency = frequency;
            this.label = label;
        }

    } // FrequencyAndName

} // ToneGeneratorActivity