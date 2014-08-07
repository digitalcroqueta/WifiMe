package andreasancho.wifime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

/**
 * Created by andrea on 7/7/14.
 */
public class InitialActivity extends Activity implements View.OnClickListener {
    public final static String EXTRA_MESSAGE = "andreasancho.wifime.MESSAGE";
    public static final String MyPREFERENCES = "MyPrefs" ;
    public static final int MyPREFERENCES_MODE = Context.MODE_PRIVATE;
    public SharedPreferences mySharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_initial);

        // Set up click listeners for all the buttons
        View continueButton = findViewById(R.id.discover_button);
        continueButton.setOnClickListener(this);
        View aboutButton = findViewById(R.id.settings_button);
        aboutButton.setOnClickListener(this);
        View exitButton = findViewById(R.id.exit_button);
        exitButton.setOnClickListener(this);
        mySharedPreferences = getSharedPreferences(MyPREFERENCES, MyPREFERENCES_MODE);
    }

    protected void savePreferences(String username){
        // Create or retrieve the shared preference object.
        mySharedPreferences = getSharedPreferences(MyPREFERENCES, MyPREFERENCES_MODE);
        SharedPreferences.Editor editor = mySharedPreferences.edit();
        editor.putString("my_username", username);
        // Commit the changes.
        editor.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        if (item.getItemId()==(R.id.action_settings)) {
            // Since this is the system wireless settings activity, it's
            // not going to send us a result. We will be notified by
            // WiFiDeviceBroadcastReceiver instead.
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            return true;
        }
        else{
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        String message;
        switch (v.getId()) {
            case R.id.discover_button:
                Intent i = new Intent(this, Discover.class);
                EditText editText = (EditText) findViewById(R.id.editUsername);
                if(editText.getText().toString().equals("")){
                    message = "Username";
                }
                else {
                    message = editText.getText().toString();
                }
                savePreferences(message);
                i.putExtra(EXTRA_MESSAGE, message);
                startActivity(i);
                break;
            case R.id.settings_button:
                // Since this is the system wireless settings activity, it's
                // not going to send us a result. We will be notified by
                // WiFiDeviceBroadcastReceiver instead.
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                break;
            case R.id.exit_button:
                finish();
                break;
        }
    }
}
