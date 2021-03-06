package thermometer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.gson.internal.LinkedTreeMap;
import com.philips.lighting.hue.SimpleHueController;
import com.philips.lighting.quickstart.R;
import com.philips.lighting.quickstart.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.relayr.RelayrSdk;
import io.relayr.model.DeviceModel;
import io.relayr.model.Reading;
import io.relayr.model.Transmitter;
import io.relayr.model.TransmitterDevice;
import io.relayr.model.User;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class ThermometerDemoActivity extends Activity {

    private static final int UPPER_THRESHOLD = 75;
    private static final int LOWER_THRESHOLD = 10;
    private static final double SOUND_THRESHOLD = 120;
    private TextView mWelcomeTextView;
    private TextView mTemperatureValueTextView;
    private TextView mTemperatureNameTextView;
    private TextView tvPercentage;
    private TransmitterDevice mDevice;
    private Subscription mUserInfoSubscription = Subscriptions.empty();
    private Subscription mTemperatureDeviceSubscription = Subscriptions.empty();
    private SimpleHueController simpleHueController;
    private TextView mNoiseValueTextView;
    private boolean mAboveSoundThreshold = false;

    private SoundManager soundManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = View.inflate(this, R.layout.activity_thermometer_demo, null);

        mWelcomeTextView = (TextView) view.findViewById(R.id.txt_welcome);
        mTemperatureValueTextView = (TextView) view.findViewById(R.id.txt_temperature_value);
        mTemperatureNameTextView = (TextView) view.findViewById(R.id.txt_temperature_name);
        mNoiseValueTextView = (TextView) view.findViewById(R.id.txt_noise_value);
        tvPercentage = (TextView) view.findViewById(R.id.tv_percentage);

        setContentView(view);

        if (RelayrSdk.isUserLoggedIn()) {
            updateUiForALoggedInUser();
        } else {
            updateUiForANonLoggedInUser();
            logIn();
        }

        simpleHueController = new SimpleHueController();

        ((ToggleButton) findViewById(R.id.switchOn))
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                        simpleHueController
                                .manageBrightness(!isChecked ? SimpleHueController.MY_MAX_BRIGHTNESS : SimpleHueController.MY_MIN_BRIGHTNESS);
                    }
                });

        soundManager = new SoundManager();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (RelayrSdk.isUserLoggedIn())
            getMenuInflater().inflate(R.menu.thermometer_demo_logged_in, menu);
        else getMenuInflater().inflate(R.menu.thermometer_demo_not_logged_in, menu);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        if (item.getItemId() == R.id.action_log_in) {
            logIn();
            return true;
        } else if (item.getItemId() == R.id.action_log_out) {
            logOut();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logIn() {
        RelayrSdk.logIn(this).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<User>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        showToast(R.string.unsuccessfully_logged_in);
                        updateUiForANonLoggedInUser();
                    }

                    @Override
                    public void onNext(User user) {
                        showToast(R.string.successfully_logged_in);
                        invalidateOptionsMenu();
                        updateUiForALoggedInUser();
                    }
                });
    }

    private void logOut() {
        unSubscribeToUpdates();
        RelayrSdk.logOut();
        invalidateOptionsMenu();
        Toast.makeText(this, R.string.successfully_logged_out, Toast.LENGTH_SHORT).show();
        updateUiForANonLoggedInUser();
    }

    private void updateUiForANonLoggedInUser() {
        mTemperatureValueTextView.setVisibility(View.GONE);
        mTemperatureNameTextView.setVisibility(View.GONE);
        mWelcomeTextView.setText(R.string.hello_relayr);
    }

    private void updateUiForALoggedInUser() {
        mTemperatureValueTextView.setVisibility(View.VISIBLE);
        mTemperatureNameTextView.setVisibility(View.VISIBLE);
        loadUserInfo();
    }

    private void loadUserInfo() {
        mUserInfoSubscription = RelayrSdk.getRelayrApi().getUserInfo().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<User>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(User user) {
                        String hello = String.format(getString(R.string.hello), user.getName());
                        mWelcomeTextView.setText(hello);
                        loadTemperatureDevice(user);
                    }
                });

    }

    private void loadTemperatureDevice(User user) {
        mTemperatureDeviceSubscription = user.getTransmitters()
                .flatMap(new Func1<List<Transmitter>, Observable<List<TransmitterDevice>>>() {
                    @Override
                    public Observable<List<TransmitterDevice>> call(List<Transmitter> transmitters) {
                        // This is a naive implementation. Users may own many WunderBars or other
                        // kinds of transmitter.
                        if (transmitters.isEmpty())
                            return Observable.from(new ArrayList<List<TransmitterDevice>>());
                        return RelayrSdk.getRelayrApi()
                                .getTransmitterDevices(transmitters.get(0).id);
                    }
                }).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<List<TransmitterDevice>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(List<TransmitterDevice> devices) {
                        for (TransmitterDevice device : devices) {
                            if (device.model.equals(DeviceModel.LIGHT_PROX_COLOR.getId())) {
                                subscribeForTemperatureUpdates(device);
                            } else if (device.model.equals(DeviceModel.MICROPHONE.getId())) {
                                subscribeForNoiseUpdates(device);
                            }
                        }
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unSubscribeToUpdates();
    }

    private void unSubscribeToUpdates() {
        if (!mUserInfoSubscription.isUnsubscribed()) mUserInfoSubscription.unsubscribe();

        if (!mTemperatureDeviceSubscription.isUnsubscribed())
            mTemperatureDeviceSubscription.unsubscribe();

        if (mDevice != null) RelayrSdk.getWebSocketClient().unSubscribe(mDevice.id);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (RelayrSdk.isUserLoggedIn()) updateUiForALoggedInUser();
        else updateUiForANonLoggedInUser();
    }

    private void subscribeForTemperatureUpdates(TransmitterDevice device) {
        mDevice = device;
        device.subscribeToCloudReadings().observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Reading>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Reading reading) {
                        System.out.println("!! onNext");
                        if (reading.meaning.equals("luminosity")) {
                            double readingValue = (Double) reading.value;
                            int percentage = processLuminosityPercentage(readingValue);

                            tvPercentage.setText(percentage + "%");
                            mTemperatureValueTextView.setText(reading.value.toString());

                            simpleHueController.manageBrightness(percentage);
                            soundManager.playSound(percentage);
                        } else if (reading.meaning.equals("color")) {
                            LinkedTreeMap<String, Double> color = (LinkedTreeMap<String, Double>) reading.value;

                            int red = color.get("red").intValue();
                            int blue = color.get("blue").intValue();
                            int green = color.get("green").intValue();

                            int hue = getHue(red, green, blue);
                            // simpleHueController.manageHue(hue);
                        }
                    }
                });
    }

    private void subscribeForNoiseUpdates(TransmitterDevice device) {
        mDevice = device;
        device.subscribeToCloudReadings().observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Reading>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Reading reading) {
                        System.out.println("!! onNext");
                        if (reading.meaning.equals("noiseLevel")) {
                            mNoiseValueTextView.setText(reading.value.toString());
                            System.out
                                    .println("Noise level = "
                                                     + reading.value
                                                     + ", class is "
                                                     + reading.value
                                            .getClass().getSimpleName());
                            double readingValue = (Double) reading.value;

                            if (readingValue > SOUND_THRESHOLD && !mAboveSoundThreshold) {
                                mAboveSoundThreshold = true;
                                onSoundThresholdCrossed();
                            } else if (readingValue <= SOUND_THRESHOLD) {
                                mAboveSoundThreshold = false;
                            }
                        }
                    }
                });
    }

    private void onSoundThresholdCrossed() {
        // TODO: do stuff
        System.out.println("Threshold crossed");
        simpleHueController.manageHue(new Random().nextInt(SimpleHueController.MY_MAX_HUE));
        soundManager.playNormalSound();
    }

    int processLuminosityPercentage(double readingValue) {
        System.out.println("Luminosity value=" + readingValue);
        int percent = (int) (readingValue / SimpleHueController.MAX_LUMINOSITY * 100);
        System.out.println("Luminosity percentage=" + percent);
        return percent;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        simpleHueController.destroy();
    }

    private void showToast(int stringId) {
        Toast.makeText(ThermometerDemoActivity.this, stringId, Toast.LENGTH_SHORT).show();
    }


    public int getHue(int red, int green, int blue) {

        float min = Math.min(Math.min(red, green), blue);
        float max = Math.max(Math.max(red, green), blue);

        float hue = 0f;
        if (max == red) {
            hue = (green - blue) / (max - min);

        } else if (max == green) {
            hue = 2f + (blue - red) / (max - min);

        } else {
            hue = 4f + (red - green) / (max - min);
        }

        hue = hue * 60;
        if (hue < 0) hue = hue + 360;

        return Math.round(hue);
    }

}