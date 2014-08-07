package andreasancho.wifime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;


public class Splash extends Activity {

    /** Duration of wait **/
    private final int SPLASH_DISPLAY_LENGTH = 3000;
    protected boolean _active = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        Thread splashTread = new Thread() {
            @Override
            public void run() {
                try {
                    int waited = 0;
                    while (_active && (waited < SPLASH_DISPLAY_LENGTH)) {
                        sleep(100);
                        if (_active) {
                            waited += 100;
                        }
                    }
                } catch (Exception e) {

                } finally {

                    startActivity(new Intent(Splash.this,
                            InitialActivity.class));
                    finish();
                }
            }
        };
        splashTread.start();
    }
}
