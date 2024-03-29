package andreasancho.wifime;

/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ChatConnection {

    private Handler mUpdateHandler;
    private ChatServer mChatServer;
    private ChatClient mChatClient;
    private Context mContext;
    private Boolean connectionEstablished = false;

    public static final String MyPREFERENCES = "MyPrefs" ;
    public static final int MyPREFERENCES_MODE = Context.MODE_PRIVATE;
    private static final String TAG = "-------------WifiMe.ChatConnection";

    private Socket mSocket;
    private int mPort;

    public void setConnectionEstablished(Boolean connected){
        this.connectionEstablished = connected;
    }
    public Boolean getConnectionEstablished(){
        return this.connectionEstablished;
    }

    public ChatConnection(Handler handler, Context context) {
        mUpdateHandler = handler;
        mChatServer = new ChatServer(handler);
        this.mPort = -1;
        this.mContext = context;
    }

    public void tearDown() {
        if(mChatServer!=null)
        mChatServer.tearDown();
        if(mChatClient!=null)
        mChatClient.tearDown();
    }

    public void connectToServer(InetAddress address, int port) {
        mChatClient = new ChatClient(address, port);
    }

    public void sendMessage(String msg) {
        if (mChatClient != null) {
            mChatClient.sendMessage(msg);
        }
    }

    public int getLocalPort() {
        return this.mPort;
    }

    public void setLocalPort(int port) {
        this.mPort = port;
    }


    public synchronized void updateMessages(String msg, boolean local) {
        Log.e(TAG, "Updating message: " + msg);
        String two = "";
        SharedPreferences sharedPrefs = mContext.getSharedPreferences(MyPREFERENCES, MyPREFERENCES_MODE);

        if (sharedPrefs.contains("other_username")){
            two = sharedPrefs.getString("other_username", "");
        } else if(two.equals("")){
            two = "Other";
        }

        Calendar c = Calendar.getInstance();
        int minutes = c.get(Calendar.MINUTE);
        int h = c.get(Calendar.HOUR_OF_DAY);
        int hour;
        String am_pm;
        if(h > 12) {
            hour = c.get(Calendar.HOUR);
            am_pm = "PM";
        } else {
            hour = c.get(Calendar.HOUR);
            am_pm = "AM";
        }
        String time = "[" + hour + ":" + minutes+ " "+ am_pm +"]";
        if (local) {
            msg = time + " Me:  " + msg;
        } else {
            msg = time + " " + two + ":  " + msg;
        }

        Bundle messageBundle = new Bundle();
        messageBundle.putString("msg", msg);

        Message message = new Message();
        message.setData(messageBundle);
        mUpdateHandler.sendMessage(message);

    }

    private synchronized void setSocket(Socket socket) {
        Log.d(TAG, "setSocket being called.");
        if (socket == null) {
            Log.d(TAG, "Setting a null socket.");
        }
        if (mSocket != null) {
            if (mSocket.isConnected()) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        mSocket = socket;
    }

    private Socket getSocket() {
        return mSocket;
    }

    private class ChatServer {
        ServerSocket mServerSocket = null;
        Thread mThread = null;

        public ChatServer(Handler handler) {
            mThread = new Thread(new ServerThread());
            mThread.start();
        }

        public void tearDown() {
            mThread.interrupt();
            try {
                mServerSocket.close();
                setConnectionEstablished(false);
            } catch (IOException ioe) {
                Log.e(TAG, "Error when closing server socket.");
            }
        }

        class ServerThread implements Runnable {

            @Override
            public void run() {

                try {
                    // Since discovery will happen via Nsd, we don't need to care which port is
                    // used.  Just grab an available one  and advertise it via Nsd.
                    mServerSocket = new ServerSocket(0);
                    setLocalPort(mServerSocket.getLocalPort());
                    Log.d(TAG, "Local Port: " + mServerSocket.getLocalPort());

                    while (!Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "ServerSocket Created, awaiting connection...");
                        if(!mServerSocket.isClosed()) {
                            setSocket(mServerSocket.accept());
                            Log.d(TAG, "Connected.");
                            setConnectionEstablished(true);
                            if (mChatClient == null) {
                                int port = mSocket.getPort();
                                InetAddress address = mSocket.getInetAddress();
                                Log.d(TAG, "Address to which connect client: " + address);
                                connectToServer(address, port);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error creating ServerSocket: ", e);
                    e.printStackTrace();
                }
            }
        }
    }

    private class ChatClient {

        private InetAddress mAddress;
        private int PORT;

        private final String CLIENT_TAG = "\"-------------WifiMe.ChatConnection.ChatClient";

        private Thread mSendThread;
        private Thread mRecThread;

        public ChatClient(InetAddress address, int port) {

            Log.d(CLIENT_TAG, "Creating chatClient");
            this.mAddress = address;
            this.PORT = port;

            mSendThread = new Thread(new SendingThread());
            mSendThread.start();
        }

        class SendingThread implements Runnable {

            BlockingQueue<String> mMessageQueue;
            private int QUEUE_CAPACITY = 10;

            public SendingThread() {
                mMessageQueue = new ArrayBlockingQueue<String>(QUEUE_CAPACITY);
            }

            @Override
            public void run() {
                try {
                    if (getSocket() == null) {
                        setSocket(new Socket(mAddress, PORT));
                        Log.d(CLIENT_TAG, "Client-side socket initialized.");

                    } else {
                        Log.d(CLIENT_TAG, "Socket already initialized. skipping!");
                    }

                    mRecThread = new Thread(new ReceivingThread());
                    mRecThread.start();

                } catch (UnknownHostException e) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, UHE", e);
                } catch (IOException e) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, IOE.", e);
                }

                while (true) {
                    try {
                        String msg = mMessageQueue.take();
                        sendMessage(msg);
                    } catch (InterruptedException ie) {
                        Log.d(CLIENT_TAG, "Message sending loop interrupted, exiting");
                    }
                }
            }
        }

        class ReceivingThread implements Runnable {

            @Override
            public void run() {
                setConnectionEstablished(true);

                BufferedReader input;
                try {
                    input = new BufferedReader(new InputStreamReader(
                            mSocket.getInputStream()));
                    while (!Thread.currentThread().isInterrupted()) {

                        String messageStr;
                        messageStr = input.readLine();
                        if (messageStr != null) {
                            Log.d(CLIENT_TAG, "Read from the stream: " + messageStr);
                            updateMessages(messageStr, false);
                        } else {
                            Log.d(CLIENT_TAG, "The nulls! The nulls!");
                            break;
                        }
                    }
                    input.close();

                } catch (IOException e) {
                    Log.e(CLIENT_TAG, "Server loop error: ", e);
                }
            }
        }

        public void tearDown() {
            try {
                if(getSocket()!= null)
                getSocket().close();
                setConnectionEstablished(false);
            } catch (IOException ioe) {
                Log.e(CLIENT_TAG, "Error when closing server socket.");
            }
        }

        public void sendMessage(String msg) {
            try {
                Socket socket = getSocket();
                if (socket == null) {
                    Log.d(CLIENT_TAG, "Socket is null, wtf?");
                } else if (socket.getOutputStream() == null) {
                    Log.d(CLIENT_TAG, "Socket output stream is null, wtf?");
                }

                PrintWriter out = new PrintWriter(
                        new BufferedWriter(
                                new OutputStreamWriter(getSocket().getOutputStream())), true);
                out.println(msg);
                out.flush();
                updateMessages(msg, true);
            } catch (UnknownHostException e) {
                Log.d(CLIENT_TAG, "Unknown Host", e);
            } catch (IOException e) {
                Log.d(CLIENT_TAG, "I/O Exception", e);
            } catch (Exception e) {
                Log.d(CLIENT_TAG, "Error3", e);
            }
            Log.d(CLIENT_TAG, "Client sent message: " + msg);
        }
    }
}
