package andreasancho.wifime;

/**
 * Created by andrea on 7/6/14.
 */

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.andrea.wifime.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

    public FileTransferService(String name) {
        super(name);
    }

    public FileTransferService() {
        super("FileTransferService");
    }

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent intent) {


        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            File file = new File(fileUri);
            Uri u = Uri.fromFile(file);
            String host = intent.getExtras().getString(EXTRAS_ADDRESS);
            Socket socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            try {
                Log.d(Discover.TAG, "Opening client socket - ");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                Log.d(Discover.TAG, "Client socket - " + socket.isConnected());

                OutputStream stream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                InputStream is = null;
                try {
                    is = cr.openInputStream(u);
                } catch (FileNotFoundException e) {
                    Log.d(Discover.TAG, e.toString());
                    Log.d(Discover.TAG, "problem opening cr");
                }
                DataInputStream in = new DataInputStream(is);
                BufferedOutputStream out = new BufferedOutputStream(stream);
                DataOutputStream d = new DataOutputStream(out);
                Log.d(Discover.TAG, "File Uri: " + fileUri);
                String fileName = file.getName();
                Log.d(Discover.TAG, "File name: " + fileName);
                String filenameArray[] = fileName.split("\\.");
                String extension = filenameArray[filenameArray.length-1];
                Log.d(Discover.TAG, "File type: " + extension);
                d.writeUTF(fileName);
                DeviceDetailFragment.copyFile(in, d);
                Log.d(Discover.TAG, "Client: Data written");
            } catch (IOException e) {
                Log.e(Discover.TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }

        }
    }
}

