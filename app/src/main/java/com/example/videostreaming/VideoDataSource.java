package com.example.videostreaming;

import android.media.MediaDataSource;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

@RequiresApi(api = Build.VERSION_CODES.M)
public class VideoDataSource extends MediaDataSource {

    private volatile byte[] videoBuffer = new byte[40000000];

    private volatile VideoDownloadListener listener;
    private volatile  boolean isDownloading;


    Runnable downloadVideoRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                Socket socket = SocketHandler.getSocket();
                InputStream inputStream = socket.getInputStream();
                //For appending incoming bytes
                int curr_len = 0;
                int count = 0;
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int read = 0;
                int x = 307200;
                boolean flag = true;
                while (read != -1){ //While there is more data
                    //Read in bytes to data buffer
                    read = inputStream.read();
                    count ++;
                    //Write to output stream
                    byteArrayOutputStream.write(read);

                    if (count > x || read == -1){
                        x = 25600;
                        Log.e("FILE_READ", "READing from output stream");
                        byteArrayOutputStream.flush();
                        byte[] temp = byteArrayOutputStream.toByteArray();
                        byteArrayOutputStream.reset();
                        System.arraycopy(temp, 0, videoBuffer, curr_len, temp.length);
                        curr_len += temp.length;
                        Log.d("buffer", "flushed data "+curr_len);
                        if (flag) {
                            flag = false;
                            listener.onVideoDownloaded();
                        }
                        count = 0;
                    }
                }
                inputStream.close();

                //Flush and set buffer.
                byteArrayOutputStream.flush();
                videoBuffer = byteArrayOutputStream.toByteArray();

                byteArrayOutputStream.close();
//                listener.onVideoDownloaded();
            }catch (Exception e){
                listener.onVideoDownloadError(e);
            }finally {
                isDownloading = false;
            }
        }
    };

    public VideoDataSource(){
        isDownloading = false;
    }

    public void downloadVideo(VideoDownloadListener videoDownloadListener){
        if(isDownloading)
            return;
        listener = videoDownloadListener;
        Thread downloadThread = new Thread(downloadVideoRunnable);
        downloadThread.start();
        isDownloading = true;
    }

    @Override
    public synchronized int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        synchronized (videoBuffer){
            int length = videoBuffer.length;
            Log.d("buffer", "position: "+position);
            Log.d("buffer", "size: "+size);
            if (position >= length) {
                Log.d("buffer", "buffer end, position "+position+" len"+length);
                return -1; // -1 indicates EOF
            }
            if (position + size > length) {
                size -= (position + size) - length;
            }
            System.arraycopy(videoBuffer, (int)position, buffer, offset, size);
            return size;
        }
    }

    @Override
    public synchronized long getSize() throws IOException {
        return -1;
    }

    @Override
    public synchronized void close() throws IOException {

    }
}
