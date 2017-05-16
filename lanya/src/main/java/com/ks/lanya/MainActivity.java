package com.ks.lanya;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.UUID;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

import static android.provider.Settings.Global.DEVICE_NAME;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {
    Button btnSerach;
    Button btnSend;
    Button btnStartPlay;
    Button btnStopPlay;
    Button btnAccept;
    ListView listView;
    BluetoothAdapter bluetoothAdapter;
    BlueAdapter adapter;
    IntentFilter filter;
    // UUID，蓝牙建立链接需要的
    private final UUID MY_UUID = UUID
            .fromString("db764ac8-4b08-7f25-aafe-59d03c27bae3");
    // 为其链接创建一个名称
    private final String NAME = "Bluetooth_Socket";
    // 选中发送数据的蓝牙设备，全局变量，否则连接在方法执行完就结束了
    private BluetoothDevice selectDevice;
    // 获取到选中设备的客户端串口，全局变量，否则连接在方法执行完就结束了
    private BluetoothSocket clientSocket;
    // 获取到向设备写的输出流，全局变量，否则连接在方法执行完就结束了
    private OutputStream os;
    // 服务端利用线程不断接受客户端信息
    private AcceptThread thread;
    private BluetoothA2dp mBluetoothA2dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnSerach = (Button) findViewById(R.id.search);
        btnSend = (Button) findViewById(R.id.send);
        btnAccept = (Button) findViewById(R.id.accept);
        btnStartPlay = (Button) findViewById(R.id.startPlay);
        btnStopPlay = (Button) findViewById(R.id.stopPlay);
        listView = (ListView) findViewById(R.id.devices);
        btnSerach.setOnClickListener(this);
        btnSend.setOnClickListener(this);
        btnAccept.setOnClickListener(this);
        btnStartPlay.setOnClickListener(this);
        btnStopPlay.setOnClickListener(this);
        adapter = new BlueAdapter(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        //打开蓝牙适配器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.enable()) {
            bluetoothAdapter.enable();
        }
        //注册广播接收
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
//        并且可以利用意图过滤器设置广播的优先级
        filter.setPriority(Integer.MAX_VALUE);
        registerReceiver(receiver, filter);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.search: {
                adapter.datas.clear();
                adapter.notifyDataSetChanged();
                MainActivityPermissionsDispatcher.doSearchWithCheck(this);
            }
            break;
            case R.id.accept:
//                accept();
                getMessage();
                break;
            case R.id.send:
                send();
                break;
            case R.id.startPlay:
                startPlay();
                break;
            case R.id.stopPlay:
                stopPlay();
                break;
        }
    }

    void accept() {
        if (thread == null) {
            thread = new AcceptThread();
        }
        thread.start();
    }

    void send() {
        // 这里需要try catch一下，以防异常抛出
        try {
            // 判断客户端接口是否为空
            if (clientSocket == null) {
                // 获取到客户端接口
                clientSocket = selectDevice
                        .createRfcommSocketToServiceRecord(MY_UUID);
                // 向服务端发送连接
                clientSocket.connect();
                // 获取到输出流，向外写数据
                os = clientSocket.getOutputStream();

            }
            // 判断是否拿到输出流
            if (os != null) {
                // 需要发送的信息
                String text = "成功发送信息";
                // 以utf-8的格式发送出去
                os.write(text.getBytes("UTF-8"));
            }
            // 吐司一下，告诉用户发送成功
            Toast.makeText(this, "发送信息成功，请查收", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            // 如果发生异常则告诉用户发送失败
            Toast.makeText(this, "发送信息失败", Toast.LENGTH_SHORT).show();
        }
    }

    @NeedsPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    void doSearch() {
        setTitle("搜索中...");
        // 点击搜索周边设备，如果正在搜索，则暂停搜索
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
        getBluetoothA2DP();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    //广播接受者
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                BluetoothDevice device;
                switch (intent.getAction()) {
                    case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                        switch (intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)) {
                            case BluetoothA2dp.STATE_CONNECTING:
                                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                Log.i("TAG", "device: " + device.getName() + " connecting");
                                break;
                            case BluetoothA2dp.STATE_CONNECTED:
                                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                Log.i("TAG", "device: " + device.getName() + " connected");
                                //连接成功，开始播放
                                startPlay();
                                break;
                            case BluetoothA2dp.STATE_DISCONNECTING:
                                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                Log.i("TAG", "device: " + device.getName() + " disconnecting");
                                break;
                            case BluetoothA2dp.STATE_DISCONNECTED:
                                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                Log.i("TAG", "device: " + device.getName() + " disconnected");
//                                setResultPASS();
                                break;
                            default:
                                break;
                        }
                        break;
                    case BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED:
                        int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1);
                        switch (state) {
                            case BluetoothA2dp.STATE_PLAYING:
                                Log.i("TAG", "state: playing.");
                                break;
                            case BluetoothA2dp.STATE_NOT_PLAYING:
                                Log.i("TAG", "state: not playing");
                                break;
                            default:
                                Log.i("TAG", "state: unkown");
                                break;
                        }
                        break;
                    case BluetoothDevice.ACTION_FOUND:
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (device != null) {
                            int deviceClassType = device.getBluetoothClass().getDeviceClass();
                            //找到指定的蓝牙设备
                            if ((deviceClassType == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
                                    || deviceClassType == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
                                    && device.getName().equals(DEVICE_NAME)) {
                                Log.i("TAG", "Found device:" + device.getName());
//                            mBluetoothDevice = device;
                                //start bond，开始配对
//                            createBond();
                            }
                            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

//                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            Log.d("a", device.getName() == null ? "未知设备" : device.getName());
                            DeviceInfo m = new DeviceInfo(device.getName() == null ? "未知设备" : device.getName(), device.getAddress());
                            adapter.datas.add(m);
                            adapter.notifyDataSetChanged();
//                        }
                        }
                        break;

                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                        int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        switch (device.getBondState()) {
                            case BluetoothDevice.BOND_BONDING:
                                Log.d("BlueToothTestActivity", "正在配对......");
                                break;
                            case BluetoothDevice.BOND_BONDED:
                                Log.d("BlueToothTestActivity", "完成配对");
                                int deviceClassType = device.getBluetoothClass().getDeviceClass();
                                if (deviceClassType == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
                                        || deviceClassType == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) {
                                    connect();
                                } else {
                                    connect(device);//连接设备
                                }
                                break;
                            case BluetoothDevice.BOND_NONE:
                                Log.d("BlueToothTestActivity", "取消配对");
                            default:
                                break;
                        }
                        break;
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        //<editor-fold>
                        state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                        switch (state) {
                            case BluetoothAdapter.STATE_TURNING_ON:
                                Log.i("TAG", "BluetoothAdapter is turning on.");
                                break;
                            case BluetoothAdapter.STATE_ON:
                                Log.i("TAG", "BluetoothAdapter is on.");
                                //蓝牙已打开，开始搜索并连接service
//                                startDiscovery();
                                getBluetoothA2DP();
                                break;
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                Log.i("TAG", "BluetoothAdapter is turning off.");
                                break;
                            case BluetoothAdapter.STATE_OFF:
                                Log.i("TAG", "BluetoothAdapter is off.");
                                break;
                        }
                        //</editor-fold>
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        setTitle("搜索结束");
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        setTitle("蓝牙已连接");
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        setTitle("蓝牙已断开");
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // 判断当前是否还是正在搜索周边设备，如果是则暂停搜索
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        // 如果选择设备为空则代表还没有选择设备
//        if (selectDevice == null) {
        //通过地址获取到该设备
        selectDevice = bluetoothAdapter.getRemoteDevice(adapter.datas.get(position).getAddr());
//        }
        if (selectDevice.getBondState() == BluetoothDevice.BOND_NONE) {
            ClsUtils.pair(selectDevice.getAddress(), "");
        } else if (selectDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            //send();
            sendMessage(position);
        }
    }

    private static final int startService = 0;
    private static final int getMessageOk = 1;
    private static final int sendOver = 2;

    private void getMessage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream is = null;
                try {
                    BluetoothServerSocket serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("serverSocket", MY_UUID);
                    mHandler.sendEmptyMessage(startService);
                    BluetoothSocket accept = serverSocket.accept();
                    is = accept.getInputStream();

                    byte[] bytes = new byte[1024];
                    int length = is.read(bytes);

                    Message msg = new Message();
                    msg.what = getMessageOk;
                    msg.obj = new String(bytes, 0, length);
                    mHandler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendMessage(final int i) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream os = null;
                try {
                    BluetoothSocket socket = selectDevice.createRfcommSocketToServiceRecord(MY_UUID);
                    socket.connect();
                    os = socket.getOutputStream();
                    os.write("testMessage".getBytes());
                    os.flush();
                    mHandler.sendEmptyMessage(sendOver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case startService:
                    setTitle("服务已打开");
                    Log.i("msg","服务已打开");
                    break;
                case getMessageOk:
                    setTitle(msg.obj.toString());
                    Log.i("msg",msg.obj.toString());
                    break;
                case sendOver:
                    Toast.makeText(MainActivity.this, "发送成功", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private void connect(BluetoothDevice btDev) {
        UUID uuid = UUID.fromString(SPP_UUID);
        try {
            clientSocket = btDev.createRfcommSocketToServiceRecord(uuid);
            Log.d("BlueToothTestActivity", "开始连接...");
            clientSocket.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 创建handler，因为我们接收是采用线程来接收的，在线程中无法操作UI，所以需要handler
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            // 通过msg传递过来的信息，吐司一下收到的信息
            Toast.makeText(MainActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
        }
    };

    // 服务端接收信息线程
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;// 服务端接口
        private BluetoothSocket socket;// 获取到客户端的接口
        private InputStream is;// 获取到输入流
        private OutputStream os;// 获取到输出流

        public AcceptThread() {
            try {
                // 通过UUID监听请求，然后获取到对应的服务端接口
                serverSocket = bluetoothAdapter
                        .listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                // 接收其客户端的接口
                socket = serverSocket.accept();
                // 获取到输入流
                is = socket.getInputStream();
                // 获取到输出流
                os = socket.getOutputStream();

                // 无线循环来接收数据
                while (true) {
                    // 创建一个128字节的缓冲
                    byte[] buffer = new byte[128];
                    // 每次读取128字节，并保存其读取的角标
                    int count = is.read(buffer);
                    // 创建Message类，向handler发送数据
                    Message msg = new Message();
                    // 发送一个String的数据，让他向上转型为obj类型
                    msg.obj = new String(buffer, 0, count, "utf-8");
                    // 发送数据
                    handler.sendMessage(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void getBluetoothA2DP() {
        Log.i("a", "getBluetoothA2DP");
        if (bluetoothAdapter == null) {
            return;
        }

        if (mBluetoothA2dp != null) {
            return;
        }

        bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.A2DP) {
                    //Service连接成功，获得BluetoothA2DP
                    mBluetoothA2dp = (BluetoothA2dp) proxy;
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.A2DP);
    }

    private MediaPlayer mMediaPlayer;

    private void startPlay() {
//        getBluetoothA2DP();
//        connect();
        Log.i("TAG", "startPlay");
        AudioManager mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (mAudioManager != null) {
            int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
        }

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.test);
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.reset();
        try {
            mMediaPlayer.setDataSource(this, uri);
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    //播放完成，可以考虑断开连接
                    disconnect();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e("TAG", "Playback error.");
                    return false;
                }
            });
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (IllegalStateException | IOException e) {
            Log.e("TAG", "Exception: prepare or start mediaplayer");
//            setResultFAIL();
        }
    }

    //程序退出前，要release播放器
    private void stopPlay() {
        Log.i("TAG", "stopPlay");
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void connect() {
        Log.i("TAG", "connect");
        if (mBluetoothA2dp == null) {
            return;
        }
        if (selectDevice == null) {
            return;
        }

        try {
            Method connect = mBluetoothA2dp.getClass().getDeclaredMethod("connect", BluetoothDevice.class);
            connect.setAccessible(true);
            connect.invoke(mBluetoothA2dp, selectDevice);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Log.e("TAG", "connect exception:" + e);
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void disconnect() {
        Log.i("TAG", "disconnect");
        if (mBluetoothA2dp == null) {
            return;
        }
        if (selectDevice == null) {
            return;
        }

        try {
            Method disconnect = mBluetoothA2dp.getClass().getDeclaredMethod("disconnect", BluetoothDevice.class);
            disconnect.setAccessible(true);
            disconnect.invoke(mBluetoothA2dp, selectDevice);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Log.e("TAG", "connect exception:" + e);
            e.printStackTrace();
        }
    }

    //取消配对
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void unPairAllDevices() {
        Log.i("TAG", "unPairAllDevices");
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            try {
                Method removeBond = device.getClass().getDeclaredMethod("removeBond");
                removeBond.invoke(device);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    //注意，在程序退出之前（OnDestroy），需要断开蓝牙相关的Service
    //否则，程序会报异常：service leaks
    private void disableAdapter() {
        Log.i("TAG", "disableAdapter");
        if (bluetoothAdapter == null) {
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        //关闭ProfileProxy，也就是断开service连接
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
        if (bluetoothAdapter.isEnabled()) {
            boolean ret = bluetoothAdapter.disable();
            Log.i("TAG", "disable adapter:" + ret);
        }
    }

    /**
     * @author li
     *         负责监听启动应用程序 后的接收数据
     */
    public class ReceiveThread extends Service {

        private Socket socket;
        private String workStatus;// 当前工作状况，null表示正在处理，success表示处理成功，failure表示处理失败
        public Boolean mainThreadFlag = true;
        ;  //状态

        @Override
        public IBinder onBind(Intent intent) {
            // TODO Auto-generated method stub
            return null;
        }

        private void doListen() {
            Log.d("chl", "doListen()");
            //开始监听
            while (mainThreadFlag) {
                //开始监听数据
                new Thread(new ThreadReadWriterSocketServer(ReceiveThread.this, socket));
            }
        }
    }

    public class ThreadReadWriterSocketServer implements Runnable {
        private Socket client = null;
        private Context context = null;

        public ThreadReadWriterSocketServer(Context context, Socket client) {
            this.context = context;
            this.client = client;
        }


        @Override
        public void run() {
            Receive();
        }


        private void Receive() {
            //处理数据
        }
    }
}
