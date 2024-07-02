package com.example.vividstereo;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class ReadWriteUtils {
    /***
     * 从文件中加载
     * @return
     */
    public static String load(Activity activity, String fileName) {
        FileInputStream fis = null;
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();
        try {
            fis = activity.openFileInput(fileName + ".txt");
            reader = new BufferedReader(new InputStreamReader(fis));
            String str;

            while ((str = reader.readLine()) != null) {
                content.append(str);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return URLDecoder.decode(content.toString());
    }

    /***
     * 保存到文件
     * @param str
     */
    public static void saveFile(String str, Activity activity, Context context, String fileName) {
        FileOutputStream fos = null;
        BufferedWriter writer = null;

        try {
            fos = activity.openFileOutput(fileName+".txt", Context.MODE_PRIVATE);
            writer = new BufferedWriter(new OutputStreamWriter(fos));
            try {
                str = URLEncoder.encode(str);
                writer.write(str);
                Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
