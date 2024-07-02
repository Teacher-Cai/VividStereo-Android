package com.example.vividstereo;

import static java.lang.Thread.sleep;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    ArrayList<String> files = new ArrayList<String>();// 存歌曲名字的数组

    ArrayList<String> filePaths = new ArrayList<String>();// 存歌曲名字的数组

    ListView lv;// 定义一个listview并且和布局里面的listview联系起来

    private static final MediaPlayer mediaPlayer = new MediaPlayer();

    public ServerSocket serverSocket;
    public Socket clientTcp;

    public JSONObject config = new JSONObject();

    public Integer systemPlayDelay = 0;

    public String musicFolderPath = "";

    public int currentPlayMusicIndex = 0;
    public TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        verifyMultiPermissions(this);

        setContentView(R.layout.activity_main);
        EditText systemDelayEditText = findViewById(R.id.system_dalay);

        // setting value
        try {
            String stringFile = ReadWriteUtils.load(this, "config");
            if (!TextUtils.isEmpty(stringFile)) {
                config = new JSONObject(stringFile);
                if (!config.isNull("systemPlayDelay")) {
                    systemPlayDelay = config.getInt("systemPlayDelay");
                    systemDelayEditText.setText(systemPlayDelay.toString());
                }
                if (!config.isNull("musicFolderPath")) {
                    musicFolderPath = config.getString("musicFolderPath");
                    Log.d("folderPath", "onCreate: " + musicFolderPath);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        lv = (ListView) findViewById(R.id.listView);
        /* 用于ListView的适配器 */
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_expandable_list_item_1, getData());
        /* 将ArrayAdapter添加到ListView对象中 */
        lv.setAdapter(adapter);

        Button play = findViewById(R.id.play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!GlobalInfo.getInstance().synchronization) {
                play(filePaths.get(currentPlayMusicIndex), 0L);
                Toast.makeText(MainActivity.this, "play music", Toast.LENGTH_SHORT).show();}
                else {
                Long nowTs = System.currentTimeMillis();
                Long preSetTs = nowTs + 1000;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UdpCommu udpCommu = UdpCommu.getInstance();

                        Long masterDeviceTsMs = Double.valueOf(Objects.toString(GlobalInfo.getInstance().ipWithDelay
                                .getOrDefault(GlobalInfo.getInstance().localIp, 0L))).longValue() + preSetTs;

                        for (Map.Entry<String, Long> entry : GlobalInfo.getInstance().ipWithDelay.entrySet()) {
                            if (Objects.equals(entry.getKey(), GlobalInfo.getInstance().localIp)) {
                                continue;
                            }

                            JSONObject jsonObject = new JSONObject();
                            try {
                                Long specificDeviceTsMs =  masterDeviceTsMs - Double.valueOf(Objects.toString(entry.getValue())).longValue();
                                jsonObject.put("reason", "music_play");
                                jsonObject.put("music_name", files.get(currentPlayMusicIndex));
                                jsonObject.put("timeMs", specificDeviceTsMs);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }

                            String mess = jsonObject.toString();
                            udpCommu.send(mess, entry.getKey());
                        }
                    }
                }).start();
                play(filePaths.get(currentPlayMusicIndex), preSetTs - systemPlayDelay);}
            }
        });


        // select dir button
        Button selectDir = findViewById(R.id.select_dir);
        selectDir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, 1);
            }
        });


        // pause
        Button pause = findViewById(R.id.pause);
        pause.setOnClickListener(new View.OnClickListener() {
            int pauseOrUnpause = 0;

            @Override
            public void onClick(View v) {
                if (pauseOrUnpause % 2 == 0) {
                    pauseOrUnpause += 1;
                    mediaPlayer.pause();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            UdpCommu udpCommu = UdpCommu.getInstance();
                            JSONObject jsonObject = new JSONObject();
                            try {
                                jsonObject.put("reason", "music_play");
                                jsonObject.put("music_name", files.get(currentPlayMusicIndex));
                                jsonObject.put("timeMs", 0L);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }

                            String mess = jsonObject.toString();

                            for (Map.Entry<String, Long> entry : GlobalInfo.getInstance().ipWithDelay.entrySet()) {
                                if (Objects.equals(entry.getKey(), GlobalInfo.getInstance().localIp)) {
                                    continue;
                                }
                                udpCommu.send(mess, entry.getKey());
                            }
                        }
                    }).start();

                } else {
                    pauseOrUnpause -= 1;
                    mediaPlayer.start();
                }
            }
        });

        Button stop = findViewById(R.id.stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.stop();
            }
        });

        systemDelayEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(s.toString())) {
                    systemPlayDelay = Integer.parseInt(s.toString());
                } else {
                    systemPlayDelay = 0;
                }
            }
        });


        // next
        Button next = findViewById(R.id.next);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentPlayMusicIndex += 1;
                if (currentPlayMusicIndex >= files.size()) {
                    currentPlayMusicIndex = 0;
                }
                Long nowTs = System.currentTimeMillis();
                Long preSetTs = nowTs + 1000;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UdpCommu udpCommu = UdpCommu.getInstance();

                        Long masterDeviceTsMs = Double.valueOf(Objects.toString(GlobalInfo.getInstance().ipWithDelay
                                .getOrDefault(GlobalInfo.getInstance().localIp, 0L))).longValue() + preSetTs;

                        for (Map.Entry<String, Long> entry : GlobalInfo.getInstance().ipWithDelay.entrySet()) {
                            if (Objects.equals(entry.getKey(), GlobalInfo.getInstance().localIp)) {
                                continue;
                            }

                            JSONObject jsonObject = new JSONObject();
                            try {
                                Long specificDeviceTsMs =  masterDeviceTsMs - Double.valueOf(Objects.toString(entry.getValue())).longValue();
                                jsonObject.put("reason", "music_play");
                                jsonObject.put("music_name", files.get(currentPlayMusicIndex));
                                jsonObject.put("timeMs", specificDeviceTsMs);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }

                            String mess = jsonObject.toString();
                            udpCommu.send(mess, entry.getKey());
                        }
                    }
                }).start();
                play(filePaths.get(currentPlayMusicIndex), preSetTs - systemPlayDelay);
            }
        });

        // prev
        Button prev = findViewById(R.id.prev);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentPlayMusicIndex -= 1;
                if (currentPlayMusicIndex < 0) {
                    currentPlayMusicIndex = files.size() - 1;
                }

                Long nowTs = System.currentTimeMillis();
                Long preSetTs = nowTs + 1000;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UdpCommu udpCommu = UdpCommu.getInstance();
                        Long masterDeviceTsMs = Double.valueOf(Objects.toString(GlobalInfo.getInstance().ipWithDelay
                                .getOrDefault(GlobalInfo.getInstance().localIp, 0L))).longValue() + preSetTs;

                        for (Map.Entry<String, Long> entry : GlobalInfo.getInstance().ipWithDelay.entrySet()) {
                            if (Objects.equals(entry.getKey(), GlobalInfo.getInstance().localIp)) {
                                continue;
                            }

                            JSONObject jsonObject = new JSONObject();
                            try {
                                Long specificDeviceTsMs =  masterDeviceTsMs - Double.valueOf(Objects.toString(entry.getValue())).longValue();
                                jsonObject.put("reason", "music_play");
                                jsonObject.put("music_name", files.get(currentPlayMusicIndex));
                                jsonObject.put("timeMs", specificDeviceTsMs);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }

                            String mess = jsonObject.toString();
                            udpCommu.send(mess, entry.getKey());

                        }
                    }
                }).start();
                play(filePaths.get(currentPlayMusicIndex), preSetTs - systemPlayDelay);
            }
        });

        // sync state text view
        textView = findViewById(R.id.sync_state);

        // sync
        Button sync = findViewById(R.id.sync);
        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlobalInfo.getInstance().masterDevice = true;
                GlobalInfo.getInstance().synchronization = true;
//                GlobalInfo.getInstance().allDeviceIp = List.of("192.168.0.111");
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("reason", "get_ip_address");
                    jsonObject.put("ip_address", GlobalInfo.getInstance().localIp);

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                String mess = jsonObject.toString();

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // 子线程要执行的代码
                        UdpCommu.getInstance().send(mess, "255.255.255.255");  // 192.168.0.255
                    }
                });
                thread.start();

                try {
                    sleep(1000);
                } catch (Exception e) {
                    Log.e("sleep", "onClick: ", e);
                }
                Log.d("sync", "upd: "+ GlobalInfo.getInstance().allDeviceIp);
                GlobalInfo.getInstance().allDeviceIp.remove(GlobalInfo.getInstance().localIp);
                Log.d("sync", "upd: "+ GlobalInfo.getInstance().allDeviceIp);

                GlobalInfo.getInstance().ipWithDelay = TcpCommu.getDevicesDelay(GlobalInfo.getInstance().allDeviceIp);
                Log.d("sync", "onClick: "+ GlobalInfo.getInstance().ipWithDelay);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UdpCommu udpCommu = UdpCommu.getInstance();
                        List<String> ips = GlobalInfo.getInstance().allDeviceIp;
                        for (String ip : ips) {
                            JSONObject jsonObject = new JSONObject();
                            try {
                                jsonObject.put("reason", "all_device_delay");
                                jsonObject.put("ip_with_delay", new JSONObject(GlobalInfo.getInstance().ipWithDelay));

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }

                            String mess = jsonObject.toString();
                            udpCommu.send(mess, ip);
                        }
                    }
                }).start();

                textView.setText("sync state:synchronized~");
            }
        });

        // start tcp service
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 子线程要执行的代码
                initTcp();
                tcpReceiver(clientTcp);
//                udpReceiver();
            }
        });
        thread.start();

        // get local ip and start upd service
        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                // 子线程要执行的代码
                GlobalInfo.getInstance().localIp = UdpCommu.getInstance().getLocalIp();
                udpReceiver();
            }
        });
        thread1.start();

        // auto playing next
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (GlobalInfo.getInstance().masterDevice) {
                currentPlayMusicIndex += 1;
                if (currentPlayMusicIndex >= files.size()) {
                    currentPlayMusicIndex = 0;
                }

                    Long nowTs = System.currentTimeMillis();
                    Long preSetTs = nowTs + 1000;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            UdpCommu udpCommu = UdpCommu.getInstance();
                            Long masterDeviceTsMs = Double.valueOf(Objects.toString(GlobalInfo.getInstance().ipWithDelay
                                    .getOrDefault(GlobalInfo.getInstance().localIp, 0L))).longValue() + preSetTs;

                            for (Map.Entry<String, Long> entry : GlobalInfo.getInstance().ipWithDelay.entrySet()) {
                                if (Objects.equals(entry.getKey(), GlobalInfo.getInstance().localIp)) {
                                    continue;
                                }

                                JSONObject jsonObject = new JSONObject();
                                try {
                                    Long specificDeviceTsMs =  masterDeviceTsMs - Double.valueOf(Objects.toString(entry.getValue())).longValue();
                                    jsonObject.put("reason", "music_play");
                                    jsonObject.put("music_name", files.get(currentPlayMusicIndex));
                                    jsonObject.put("timeMs", specificDeviceTsMs);

                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }

                                String mess = jsonObject.toString();
                                udpCommu.send(mess, entry.getKey());

                            }
                        }
                    }).start();

                play(filePaths.get(currentPlayMusicIndex), preSetTs - systemPlayDelay);
                }

            }
        });

//        Thread thread1 = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                // 子线程要执行的代码
//                try {
//                    printTs();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        thread1.start();
    }

    private void initTcp() {
        try {
            serverSocket = new ServerSocket(4000);
            clientTcp = serverSocket.accept();
            clientTcp.setKeepAlive(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> getData() {
        if (!Objects.equals(musicFolderPath, "")) {

            File directory = new File(musicFolderPath);

            // 确保目录存在且确实是一个目录
            if (directory.exists() && directory.isDirectory()) {
                // 获取目录下所有文件
                File[] files = directory.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            // 打印文件名
                            this.files.add(file.getName());
                            filePaths.add(file.getAbsolutePath());
                        }
                    }
                }
            } else {
                Toast.makeText(MainActivity.this, "目录不存在或不是一个目录", Toast.LENGTH_LONG).show();
            }
        }
        else {
            Uri allSongsUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            Cursor cursor = getContentResolver().query(allSongsUri, null, null, null, selection);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
//                    Song song = new Song(cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media._ID)),
//                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)),
//                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)),
//                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)));
                        filePaths.add(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)));
                        files.add(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)));
//                    album_name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
//                    int album_id = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
//                    int artist_id = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
//        Log.d("folderPath", "getData: " + filePaths.get(filePaths.size() - 1));
//        Log.d("folderPath", "getData: " + files.get(0));

        return files;
    }

    private void play(String musicPath, Long preSetTs) {
        try {
            Long ts1 = System.currentTimeMillis();
            // 设置类型
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            // 这里要reset一下啊 （当已经设置过音乐后，再调用此方法时，没有reset就会异常）
            mediaPlayer.reset();
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();
            mediaPlayer.setAudioAttributes(aa);
            mediaPlayer.setDataSource(musicPath);// 设置文件源
            mediaPlayer.prepare();// 解析文件

            mediaPlayer.setVolume(0.0f, 0.0f);
            mediaPlayer.start(); // 开始播放，如果已经在播放不会有什么效果

            if (preSetTs > 0) {
                Long remain_ms = preSetTs - System.currentTimeMillis();
                sleep(remain_ms);
            }

            mediaPlayer.seekTo(0);
            mediaPlayer.setVolume(1.0f, 1.0f);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void tcpReceiver(Socket clientTcp) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientTcp.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter pout = new PrintWriter(new OutputStreamWriter(clientTcp.getOutputStream(), StandardCharsets.UTF_8));
            while (true) {
                String str = in.readLine();
                if (Objects.equals(str, "return")) {
                    pout.print(System.currentTimeMillis() + "\r\n");
                    pout.flush();
                    GlobalInfo.getInstance().masterDevice = false;
                } else {
                    String[] message = str.split(",");
                    String ts = message[0];
                    long preSetTs = Long.parseLong(ts);
                    String musicName = message[1];
//                    Long remain_ms = preSetTs - System.currentTimeMillis();
//            Thread.sleep(remain_ms);
                    int musicIdx = files.indexOf(musicName);
                    if (musicIdx > -1 && preSetTs > 0) {
                        preSetTs -= systemPlayDelay;
                        play(filePaths.get(musicIdx), preSetTs);
                    } else if (preSetTs == 0) {
                        mediaPlayer.pause();
                    }
                }
//                if (Objects.equals(str, "play")) {
//                    play(filePaths.get(filePaths.size() - 1));
//                }
//                pout.print("" + System.currentTimeMillis());
//                pout.flush();
//                pout.close();
//                Thread.currentThread().sleep(100);
//                in.close();
            }


        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e("hey", "tcpReceiver: error", ex);
        }
    }

    public void udpReceiver() {
        try {
            UdpCommu udpCommu = UdpCommu.getInstance();
            while (true) {
                String str = udpCommu.listen();
                JSONObject udpMessage = new JSONObject(str);

                String reason = udpMessage.getString("reason");
                if ("music_play".equals(reason)) {
                    long preSetTs = udpMessage.getLong("timeMs");
                    String musicName = udpMessage.getString("music_name");

                    int musicIdx = files.indexOf(musicName);

                    if (musicIdx > -1 && preSetTs > 0) {
                        currentPlayMusicIndex = musicIdx;
                        preSetTs -= systemPlayDelay;
                        play(filePaths.get(musicIdx), preSetTs);
                    } else if (preSetTs == 0) {
                        mediaPlayer.pause();
                    }
                } else if ("all_device_delay".equals(reason)) {
                    Gson gson = new Gson();
                    GlobalInfo globalInfo = GlobalInfo.getInstance();
                    globalInfo.ipWithDelay.putAll(gson.fromJson(udpMessage.getString("ip_with_delay"), Map.class));
                    Thread thread1 = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // 子线程要执行的代码
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    textView.setText("sync state:synchronized~");
                                }
                            });
                        }
                    });
                    thread1.start();
                    GlobalInfo.getInstance().synchronization = true;

//                    Log.d("hey", "udpReceiver: " + globalInfo.ipWithDelay);
                } else if ("get_ip_address".equals(reason)) {
                    String ipAddress = udpMessage.getString("ip_address");
                    Log.d("broadcast", "udpReceiver: " + ipAddress);
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("reason", "return_ip_address");
                    jsonObject.put("ip_address", GlobalInfo.getInstance().localIp);
                    String mess = jsonObject.toString();

                    udpCommu.send(mess, ipAddress);
                } else if ("return_ip_address".equals(reason)) {
                    String ipAddress = udpMessage.getString("ip_address");
                    GlobalInfo.getInstance().allDeviceIp.add(ipAddress);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e("hey", "ucpReceiver: error", ex);
        }
    }

    public void printTs() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            Long ts1 = System.currentTimeMillis();
            sleep(1000);
            Log.d("hey", "printTs: " + (System.currentTimeMillis() - ts1));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            config.put("systemPlayDelay", systemPlayDelay);
            config.put("musicFolderPath", musicFolderPath);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        ReadWriteUtils.saveFile(config.toString(), this, getBaseContext(), "config");
    }

    @Override
    protected void onDestroy() {
        mediaPlayer.release();
        super.onDestroy();
    }

    private static final int REQUEST_PERMISSION_CODE = 1;
    private final static String[] PERMISSIONS_LIST = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_AUDIO,
    };


    public void verifyMultiPermissions(Activity activity) {
        try {
            List<Integer> permissionStatues = new ArrayList<>();
            List<String> requestPermissionName = new ArrayList<>();

            for (int i = 0; i < PERMISSIONS_LIST.length; i++) {
                //判断当前系统是否是Android6.0(对应API 23)以及以上，如果是则判断是否含有了权限
                int permission = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_LIST[i]);
                /***
                 * checkSelfPermission返回两个值
                 * 有权限: PackageManager.PERMISSION_GRANTED
                 * 无权限: PackageManager.PERMISSION_DENIED
                 */
                permissionStatues.add(permission);
            }

            for (int i = 0; i < permissionStatues.size(); i++) {
                if (permissionStatues.get(i) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionName.add(PERMISSIONS_LIST[i]);
                }
            }

            ActivityCompat.requestPermissions(this, requestPermissionName.toArray(new String[requestPermissionName.size()]), REQUEST_PERMISSION_CODE);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //回调函数，申请权限后回调onRequestPermissionResult函数，第一个参数为请求码，第二个参数是刚刚请求的权限集，第三个参数是请求结果，0表示授权成功，-1表示授权失败
//requestCode：这个对应请求状态码，permissions：这个是前面你要申请的权限集合，grantResults：这个是申请权限的结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                //grantResults[i]：返回0，表示授权成功，返回-1，表示授权失败
                Log.i("111333", "申请的权限为：" + permissions[i] + ",申请结果：" + grantResults[i]);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // get user select folder
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    String path = uri.getPath();
                    Toast.makeText(MainActivity.this, path, Toast.LENGTH_LONG).show();
                    musicFolderPath =  Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path.split(":")[1];

                    Log.d("folderPath", "onActivityResult: " + musicFolderPath);
                }
            }
        }
    }

    private String getRealPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = this.getContentResolver().query(uri, projection, null, null, null);
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(columnIndex);
        cursor.close();
        return path;
    }
}
