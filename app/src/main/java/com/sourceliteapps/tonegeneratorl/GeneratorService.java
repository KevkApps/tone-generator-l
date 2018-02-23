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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GeneratorService extends Service {

    private PlaySound playSound = null;
    private AudioTrack audioTrack;
    private final int duration = 10; // seconds
    private final int sampleRate = 44100;
    private final int numSamples = duration * sampleRate;
    private final double sample[] = new double[numSamples];
    private final byte generatedSnd[] = new byte[2 * numSamples];
    private double freqOfTone = 5000; // frequency in hz
    private Future<?> future;
    private boolean playing = false;
    private ScheduledThreadPoolExecutor stpe;
    NotificationManager notificaitonManager;
    private int mId = 69;

    final Messenger mServiceMessenger = new Messenger(new IncomingHandler());

    @Override
    public void onCreate() {
        super.onCreate();

        stpe = new ScheduledThreadPoolExecutor(3);


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {

            // create and launch notificaion

            Notification.Builder mBuilder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Tone Generator")
                    .setTicker("meter calibration")
                    .setContentText("tone generator is running")
                    .setAutoCancel(false);

            mBuilder.setOngoing(true);

            Intent resultIntent = new Intent(this, ToneGeneratorActivity.class);

            TaskStackBuilder stackBuilder = null;
            stackBuilder = TaskStackBuilder.create(this);

            stackBuilder.addParentStack(ToneGeneratorActivity.class);

            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                    0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);

            notificaitonManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            Notification notification = mBuilder.build();

            // notificaitonManager.notify(mId, notification);

            startForeground(mId, notification);

        } else { // api level 15 or earlier
            // old version of Notification

            notificaitonManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            int icon = R.drawable.ic_launcher;
            CharSequence tickerText = "tone generator is running";
            long when = System.currentTimeMillis();

            Notification notification = new Notification(icon, tickerText, when);

            // alternate way to set flags using bitwise OR
//            notification.flags |= Notification.FLAG_NO_CLEAR;
//            notification.flags |= Notification.FLAG_ONGOING_EVENT;

            Intent notificationIntent = new Intent(this, ToneGeneratorActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            Notification.Builder builder = new Notification.Builder(getApplicationContext());
            builder.setOngoing(true);
            builder.setAutoCancel(false);
            builder.setContentTitle("Tone Generator");
            builder.setTicker("meter calibration");
            builder.setContentText("tone generator is running");
            builder.setSmallIcon(R.drawable.ic_launcher);
            builder.setContentIntent(pendingIntent);

            notification = builder.getNotification();
            // notificaitonManager.notify(mId, notification);

            startForeground(mId, notification);

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();


        Toast.makeText(getApplicationContext(), "ON DESTROY", Toast.LENGTH_SHORT).show();

        if(audioTrack != null){
            audioTrack.pause();
            audioTrack.release();
            audioTrack = null;
        }

        playSound = null;
        if(future != null) {
            future.cancel(true);
        }

    }

    @Override
    public IBinder onBind(Intent intent) {

        // startSound();
        return mServiceMessenger.getBinder();

    } // onBind

    @Override
    public boolean onUnbind(Intent intent) {

        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        stopForeground(true);

        if(audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
            playing = false;
        }

        // stop the Service
        stopSelf();
    }

    public void stopSound() {

        if(audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
            playing = false;
        }
    }

    class IncomingHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);


            Bundle bund = msg.getData();

            switch (msg.what) {
                case 1: {

                    double freq = bund.getDouble("freqOfTone", 0);
                    freqOfTone = freq;

                } // case 1

                break;

                case 2: {

                    genTone();
                    startAndStopSound();

                    // send message back to Activity

                    Messenger messenger = msg.replyTo; //retrieves messenger from the message
                    Message m = new Message(); //create the message to send back to the client
                    m.what = 1;
                    Bundle bundOut = new Bundle(); //Just to show how to send other objects with it
                    bundOut.putBoolean("playing", playing); //this could be any parcelable object
                    m.setData(bundOut); //adds the bundle to the message
                    try {
                        messenger.send(m);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    // start service
                } // case 2

                break;

                case 3: {

                    stopSound();

                    // stop service

                } // case 3

                break;

                case 4: {

                    Messenger messenger = msg.replyTo;
                    Message m = new Message();
                    m.what = 1;
                    Bundle bundOut = new Bundle();
                    bundOut.putBoolean("playing", playing);
                    m.setData(bundOut); //adds the bundle to the message
                    try {
                        messenger.send(m);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    // get playing status

                } // case 4

                break;

                default:
                    super.handleMessage(msg);

            } // switch for msg.what

        } // handleMessage

    } // IncomingHandler

    public void startAndStopSound() {

        if(audioTrack == null) {

            genTone();

            playSound = new PlaySound();
            future = stpe.schedule(playSound, 0, TimeUnit.MILLISECONDS);
            playing = true;

        } else if (audioTrack != null){

            if(audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING){

                audioTrack.pause();
                audioTrack.release();
                audioTrack = null;

            }

            playing = false;
        }

    }

    public class PlaySound implements Runnable {

        @Override
        public void run() {

            if(audioTrack != null) {
                audioTrack.release();
                audioTrack = null;
            }

            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, numSamples, AudioTrack.MODE_STATIC);
            audioTrack.write(generatedSnd, 0, generatedSnd.length);
            audioTrack.setLoopPoints(0, generatedSnd.length/4, -1);
            audioTrack.play();

        } // run

    } // playSound

    public void genTone() {

        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }

        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    } // getTone

} // GeneratorService