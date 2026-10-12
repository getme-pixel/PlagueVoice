package com.plaguevoice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 100;

    private Spinner inputSpinner;
    private Spinner outputSpinner;
    private SeekBar pitchBar;
    private SeekBar growlBar;
    private SeekBar reverbBar;
    private TextView pitchLabel;
    private TextView growlLabel;
    private TextView reverbLabel;
    private TextView status;
    private Button startButton;

    private final List<DeviceUtils.Dev> inputs = new ArrayList<>();
    private final List<DeviceUtils.Dev> outputs = new ArrayList<>();
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputSpinner = findViewById(R.id.inputSpinner);
        outputSpinner = findViewById(R.id.outputSpinner);
        pitchBar = findViewById(R.id.pitchBar);
        growlBar = findViewById(R.id.growlBar);
        reverbBar = findViewById(R.id.reverbBar);
        pitchLabel = findViewById(R.id.pitchLabel);
        growlLabel = findViewById(R.id.growlLabel);
        reverbLabel = findViewById(R.id.reverbLabel);
        status = findViewById(R.id.status);
        startButton = findViewById(R.id.startButton);

        pitchBar.setMax(12);
        pitchBar.setProgress(7);
        growlBar.setMax(100);
        growlBar.setProgress(55);
        reverbBar.setMax(100);
        reverbBar.setProgress(35);

        pitchBar.setOnSeekBarChangeListener(simpleListener(this::updateLabels));
        growlBar.setOnSeekBarChangeListener(simpleListener(this::updateLabels));
        reverbBar.setOnSeekBarChangeListener(simpleListener(this::updateLabels));

        startButton.setOnClickListener(v -> onStartStopClicked());
        findViewById(R.id.refreshButton).setOnClickListener(v -> reloadDevices());

        reloadDevices();
        updateLabels();
        updateUi();
    }

    private void reloadDevices() {
        inputs.clear();
        outputs.clear();

        inputs.add(new DeviceUtils.Dev(-1, getString(R.string.auto), -1));
        inputs.addAll(DeviceUtils.inputs(this));

        outputs.add(new DeviceUtils.Dev(-1, getString(R.string.auto), -1));
        outputs.addAll(DeviceUtils.outputs(this));

        ArrayAdapter<DeviceUtils.Dev> inAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, inputs);
        inAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        inputSpinner.setAdapter(inAdapter);

        ArrayAdapter<DeviceUtils.Dev> outAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, outputs);
        outAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        outputSpinner.setAdapter(outAdapter);

        preselectBluetoothOutput();
    }

    private void preselectBluetoothOutput() {
        for (int i = 0; i < outputs.size(); i++) {
            int t = outputs.get(i).type;
            if (t == 8 || t == 7) { // TYPE_BLUETOOTH_A2DP=8, TYPE_BLUETOOTH_SCO=7
                outputSpinner.setSelection(i);
                return;
            }
        }
    }

    private void onStartStopClicked() {
        if (running) {
            Intent i = new Intent(this, VoiceService.class);
            i.setAction(VoiceService.ACTION_STOP);
            startService(i);
            running = false;
            updateUi();
            return;
        }

        if (!hasMicPermission()) {
            requestNeededPermissions();
            return;
        }
        startEngine();
    }

    private void startEngine() {
        int inId = selectedId(inputs, inputSpinner.getSelectedItemPosition());
        int outId = selectedId(outputs, outputSpinner.getSelectedItemPosition());

        Intent i = new Intent(this, VoiceService.class);
        i.setAction(VoiceService.ACTION_START);
        i.putExtra(VoiceService.EXTRA_IN, inId);
        i.putExtra(VoiceService.EXTRA_OUT, outId);
        i.putExtra(VoiceService.EXTRA_PITCH, -(float) pitchBar.getProgress());
        i.putExtra(VoiceService.EXTRA_GROWL, growlBar.getProgress() / 100f);
        i.putExtra(VoiceService.EXTRA_REVERB, reverbBar.getProgress() / 100f);

        ContextCompat.startForegroundService(this, i);
        running = true;
        updateUi();
    }

    private int selectedId(List<DeviceUtils.Dev> list, int pos) {
        if (pos < 0 || pos >= list.size()) {
            return -1;
        }
        return list.get(pos).id;
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        List<String> need = new ArrayList<>();
        if (!hasMicPermission()) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS) {
            return;
        }
        if (hasMicPermission()) {
            startEngine();
        } else {
            Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_LONG).show();
        }
    }

    private void updateLabels() {
        pitchLabel.setText(String.format(Locale.US, "%s: -%d semitones",
                getString(R.string.label_pitch), pitchBar.getProgress()));
        growlLabel.setText(String.format(Locale.US, "%s: %d%%",
                getString(R.string.label_growl), growlBar.getProgress()));
        reverbLabel.setText(String.format(Locale.US, "%s: %d%%",
                getString(R.string.label_reverb), reverbBar.getProgress()));
    }

    private void updateUi() {
        if (running) {
            startButton.setText(R.string.stop);
            startButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.accent_dark));
            status.setText(R.string.status_running);
        } else {
            startButton.setText(R.string.start);
            startButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.accent));
            status.setText(R.string.status_idle);
        }
    }

    private SeekBar.OnSeekBarChangeListener simpleListener(Runnable onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onChange.run();
                if (running) {
                    sendUpdate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    private void sendUpdate() {
        Intent i = new Intent(this, VoiceService.class);
        i.setAction(VoiceService.ACTION_UPDATE);
        i.putExtra(VoiceService.EXTRA_PITCH, -(float) pitchBar.getProgress());
        i.putExtra(VoiceService.EXTRA_GROWL, growlBar.getProgress() / 100f);
        i.putExtra(VoiceService.EXTRA_REVERB, reverbBar.getProgress() / 100f);
        startService(i);
    }
}
