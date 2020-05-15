package com.andymao.everything;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;
import java.util.List;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity {
    private static final int CHUNK_SIZE = 8;
    private final String TAG = "MainActivity";
    private boolean VERBOSE = true;

    private volatile UartDevice mDevice;

    private HandlerThread mInputThread;
    private Handler mInputHandler;

    private Runnable mTransferUartRunnable = new Runnable() {
        @Override
        public void run() {
            transferUartData();
//            mInputHandler.postDelayed(mTransferUartRunnable, 1000);
        }
    };

    private void transferUartData() {
        if (mDevice != null) {
            // Loop until there is no more data in the RX buffer.
            try {
                byte[] buffer = new byte[9];

                while (mDevice.read(buffer, buffer.length) > 0) {

                    /**
                     * 100 ppb = 0.1 ppm = 0.12 mg/m3.
                        国家标准室内0.08mg/m3 = 833 * 0.08 = 66.64 ppb。
                     */
                    int ppbCh2o;
                    final double ch2o;
                    boolean checkSum = checkSum(buffer);
                    if (checkSum) {
                        ppbCh2o = Byte.toUnsignedInt(buffer[4]) * 256 + Byte.toUnsignedInt(buffer[5]);
                        ch2o = ppbCh2o / 66.64 * 0.08;
                    } else {
//                        ch2o = ppbCh2o = 0;

                        ppbCh2o = Byte.toUnsignedInt(buffer[4]) * 256 + Byte.toUnsignedInt(buffer[5]);
                        ch2o = ppbCh2o / 66.64 * 0.08;
                    }
                    Log.d(TAG, "ch2o: " + ch2o + ",checkSumPass=" + checkSum);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView viewById = (TextView) findViewById(R.id.textView);
                            viewById.setText(ch2o + "");
                        }
                    });
                }

            } catch (IOException e) {
                Log.w(TAG, "Unable to transfer data over UART", e);
            }
        }
    }

    private boolean checkSum(byte[] buffer) {
        int sum = 0;
        for (int i = 1; i < buffer.length - 1; i++) {
            sum += Byte.toUnsignedInt(buffer[i]);
        }
        int result = Byte.toUnsignedInt((byte) ~sum) + 1;
        return result == Byte.toUnsignedInt(buffer[8]);
    }

    private UartDeviceCallback mCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            if (VERBOSE) Log.e(TAG, "onUartDeviceDataAvailable");
            // Queue up a data transfer
            transferUartData();
            //Continue listening for more interrupts
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mInputThread = new HandlerThread("InputThread");
        mInputThread.start();
        mInputHandler = new Handler(mInputThread.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        PeripheralManager manager = PeripheralManager.getInstance();
        List<String> uartDeviceList = manager.getUartDeviceList();
        if (uartDeviceList.isEmpty()) {
            if (VERBOSE) Log.e(TAG, "device list is empty");
        } else {
            if (VERBOSE) Log.e(TAG, "device list is not empty");
            try {
                mDevice = manager.openUartDevice(uartDeviceList.get(0));
                if (mDevice != null) {
                    configureUartFrame(mDevice);
                    mInputHandler.post(mTransferUartRunnable);
                    mDevice.registerUartDeviceCallback(mInputHandler, mCallback);
                }

            } catch (IOException e) {
                if (VERBOSE) Log.e(TAG, "打开设备失败！");
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDevice != null) {
            try {
                mDevice.close();
                mDevice = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close UART device", e);
            }
        }
    }

    public void configureUartFrame(UartDevice uart) throws IOException {
        // Configure the UART port
        uart.setBaudrate(9600);
        uart.setDataSize(8);
        uart.setParity(UartDevice.PARITY_NONE);
        uart.setStopBits(1);
    }
}
