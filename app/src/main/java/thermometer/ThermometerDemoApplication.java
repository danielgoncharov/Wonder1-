package thermometer;

import android.app.Application;

public class ThermometerDemoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        RelayrSdkInitializer.initSdk(this);
    }
}
