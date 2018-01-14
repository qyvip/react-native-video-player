package com.ksyun.media.player;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.ksyun.media.player.misc.ITrackInfo;
import com.ksyun.media.player.misc.KSYTrackInfo;
import com.ksyun.media.player.recorder.KSYMediaRecorderConfig;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * 由金山云提供实现边播边录功能的核心类，只可在软解时使用，要求API Level 18及以上
 * 在视频已经开始播放之后才可使用类KSYMediaRecorder录制视频内容
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class KSYMediaRecorder {

    private final static String TAG = "KSYMediaRecorder";

    private final String VIDEO_MIME_TYPE = "video/avc";
    private final String AUDIO_MIME_TYPE = "audio/mp4a-latm";

    private final static int RAW_DATA_NUM = 20;
    private final int TIMEOUT_USEC = 10000;

    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mVideoBufferInfo;
    private MediaCodec.BufferInfo mAudioBufferInfo;
    private BlockingQueue<QueueItem> mVideoRawDataQueue;
    private BlockingQueue<QueueItem> mAudioRawDataQueue;

    private Thread mVideoDequeueInputThread;
    private Thread mVideoDequeueOutputThread;

    private Thread mAudioDequeueInputThread;
    private Thread mAudioDequeueOutputThread;

    private int mVideoEncoderColorFormat;

    private String mMediaOutputPath;
    private int mVideoWidth;
    private int mVideoHeight;

    private boolean mAbort;
    private boolean mMuxerStarted;
    private boolean mFindVideoPts;
    private boolean mFindAudioPts;
    private boolean mOnlyVideo;
    private long mFirstVideoPts;
    private long mFirstAudioPts;

    private ByteBuffer[] mVideoRawDataBuffer = new ByteBuffer[RAW_DATA_NUM];

    private WeakReference<KSYMediaPlayer> mReferencePlayer;
    private KSYMediaRecorderConfig mRecorderConfig;

    private int mOrigSampleRate = 44100;
    private int mChannelNum = 2;
    private long mPrevWriteAudioPTS = 0l;
    private long mPrevWriteVideoPTS = -1l;
    private int mTargetFPS = 25;

    private Thread mMediaMuxThread;
    private BlockingQueue<QueueItem> mKeyInfoQueue;
    private BlockingQueue<QueueItem> mOutputQueue;
    // 标志是否获取视频sps/pps和音频adts(如果录制音频)
    private volatile boolean mWriteKeyInfo = false;

    private final int KSY_SAMPLE_DATA_TYPE_VIDEO = 110;
    private final int KSY_SAMPLE_DATA_TYPE_AUDIO = 111;

    private final int KSY_FRAME_TYPE_SEQUENCE_HEADER = 1;
    private final int KSY_FRAME_TYPE_KEY_FRAME = 2;
    private final int KSY_FRAME_TYPE_OTHER = 4;
    private final int KSY_FRAME_TYPE_ADTS = 8;

    private KSYMediaPlayer.OnVideoRawDataListener mRawVideoDataListener = new KSYMediaPlayer.OnVideoRawDataListener() {
        @Override
        public void onVideoRawDataAvailable(IMediaPlayer mp, byte[] buf, int size, int width, int height, int format, long pts) {

            putVideoQueueItem(buf, pts);

            if (mReferencePlayer != null && mReferencePlayer.get() != null)
            {
                KSYMediaPlayer player = mReferencePlayer.get();
                player.addVideoRawBuffer(buf);
            }

            if (mVideoDequeueInputThread == null)
            {
                mVideoDequeueInputThread = new Thread(new MediaRecorderWrapper(KSYMediaRecorder.this, true, false));
                mVideoDequeueInputThread.start();
            }
        }
    };

    private KSYMediaPlayer.OnAudioPCMListener mRawAudioListener = new KSYMediaPlayer.OnAudioPCMListener() {
        @Override
        public void onAudioPCMAvailable(IMediaPlayer mp, ByteBuffer buffer, long timestamp, int channels, int samplerate, int fmt) {
            putAudioQueueItem(buffer, timestamp);
        }
    };

    public KSYMediaRecorder(KSYMediaRecorderConfig config, String outputPath) {
        if (TextUtils.isEmpty(outputPath) || config == null)
            throw new IllegalArgumentException("VideoRecorderConfig or outputPath can't be NULL.");

        mMediaOutputPath = outputPath;
        mRecorderConfig = config;
    }

    /**
     * 初始化接口
     *
     * @param player 播放器对象
     * @throws IOException
     */
    public void init(KSYMediaPlayer player) throws IOException {
        if (mReferencePlayer != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
            throw new RuntimeException("Input parameter is null or Android version is too low.");


        boolean bool;
        if (TextUtils.isEmpty(KSYLibraryManager.getLocalLibraryPath()))
        {
            bool = e.a("ksylive");
            if (!bool) {
                e.a("ksyplayer");
            }
        }
        else
        {
            bool = e.a(KSYLibraryManager.getLocalLibraryPath(), "ksylive");
            if (!bool) {
                e.a(KSYLibraryManager.getLocalLibraryPath(), "ksyplayer");
            }
        }

        boolean hasVideo = false;

        mAbort = false;
        mFindAudioPts = false;
        mFindVideoPts = false;
        mOnlyVideo = true;
        mReferencePlayer = new WeakReference<KSYMediaPlayer>(player);

        mVideoWidth = player.getVideoWidth();
        mVideoHeight = player.getVideoHeight();

        KSYTrackInfo[] infos = player.getTrackInfo();
        for (KSYTrackInfo info : infos) {
            switch (info.getTrackType()) {
                case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO:
                    hasVideo = true;
                    break;
                case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO:
                    mOnlyVideo = false;
                    break;
                default:
                    break;
            }
        }

        KSYMediaMeta mediaMeta = player.getMediaInfo().mMeta;

        Log.d(TAG,"KSYMediaPlayer-init");
        Log.d(TAG,"mediaMeta:"+mediaMeta.toString());
        Log.d(TAG,"mediaMeta-mVCodec:"+mediaMeta.mVCodec);
        Log.d(TAG,"mediaMeta-mFormat:"+mediaMeta.mFormat);
        Log.d(TAG,"mediaMeta-mBitrate:"+mediaMeta.mBitrate);
        Log.d(TAG,"mediaMeta-mACodec:"+mediaMeta.mACodec);
        Log.d(TAG,"mediaMeta-mFpsNum:"+mediaMeta.mVideoStream.mFpsNum);
        Log.d(TAG,"mediaMeta-mFpsDen:"+mediaMeta.mVideoStream.mFpsDen);

        if (mediaMeta != null) {
            if (mediaMeta.mVideoStream != null
                    && mediaMeta.mVideoStream.mFpsNum > 0
                    && mediaMeta.mVideoStream.mFpsDen > 0)
            {
                int fps = mediaMeta.mVideoStream.mFpsNum / mediaMeta.mVideoStream.mFpsDen;
                if (fps > 0)
                    mTargetFPS = fps;
            }
            Log.d(TAG,"mediaMeta-mTargetFPS:"+mTargetFPS);
            if (mediaMeta.mAudioStream != null) {
                if (mediaMeta.mAudioStream.mSampleRate > 0)
                    mOrigSampleRate = mediaMeta.mAudioStream.mSampleRate;
                if (mediaMeta.mAudioStream.mChannelNumber > 0 && mediaMeta.mAudioStream.mChannelNumber <= 2)
                    mChannelNum = mediaMeta.mAudioStream.mChannelNumber;
            }
        }

        if (!hasVideo)
            throw new RuntimeException("This media file has no video!");

        if (mVideoWidth <=0 || mVideoHeight <= 0)
            throw new RuntimeException("Video width or height is wrong!");

        mOutputQueue = new LinkedBlockingQueue<>();
        mKeyInfoQueue = new LinkedBlockingQueue<>();

        mOnlyVideo = !mRecorderConfig.getAudioRecordState();
        if (mOnlyVideo)
            mRecorderConfig.setAudioBitrate(-1);

        mVideoRawDataQueue = new LinkedBlockingQueue<>();
        if (!mOnlyVideo)
            mAudioRawDataQueue = new LinkedBlockingQueue<>();

        File file = new File(mMediaOutputPath);
        if (file.exists())
            file.delete();

        initRawData(player);
        initInternal();
    }

    /**
     * 开始录制视
     */
    public void start()
    {
        mVideoDequeueOutputThread = new Thread(new MediaRecorderWrapper(this, false, false));
        mVideoDequeueOutputThread.start();

        if (mReferencePlayer != null && mReferencePlayer.get() != null) {
            mReferencePlayer.get().setVideoRawDataListener(mRawVideoDataListener);
            if (!mOnlyVideo)
                mReferencePlayer.get().setOnAudioPCMAvailableListener(mRawAudioListener);
        }
    }

    /**
     * 结束录制
     */
    public void stop() {
        mAbort = true;

        if (mReferencePlayer != null && mReferencePlayer.get() != null)
            mReferencePlayer.get().setVideoRawDataListener(null);

        try {
            if (mVideoDequeueInputThread != null)
                mVideoDequeueInputThread.join();
            mVideoDequeueInputThread = null;

            if (mVideoDequeueOutputThread != null)
                mVideoDequeueOutputThread.join();
            mVideoDequeueOutputThread = null;

            if (mAudioDequeueInputThread != null)
                mAudioDequeueInputThread.join();
            mAudioDequeueInputThread = null;

            if (mAudioDequeueOutputThread != null)
                mAudioDequeueOutputThread.join();
            mAudioDequeueOutputThread = null;

            if (mMediaMuxThread != null)
                mMediaMuxThread.join();
            mMediaMuxThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mVideoEncoder != null)
            mVideoEncoder.release();
        mVideoEncoder = null;

        if (mAudioEncoder != null)
            mAudioEncoder.release();
        mAudioEncoder = null;

        native_stop();

        if (mVideoRawDataQueue != null)
            mVideoRawDataQueue.clear();
        mVideoRawDataQueue = null;

        if (mAudioRawDataQueue != null)
            mAudioRawDataQueue.clear();
        mAudioRawDataQueue = null;
    }

    private void initInternal() throws IOException {
        if (mReferencePlayer != null && mReferencePlayer.get() != null)
        {
            KSYMediaPlayer player = (KSYMediaPlayer) mReferencePlayer.get();
            player.initRecorderMuxer(mMediaOutputPath, mRecorderConfig.getVideoBitrate(), mRecorderConfig.getAudioBitrate());
        }

        MediaFormat videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mVideoWidth, mVideoHeight);
//        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, getVideoEncodeColorFormat());
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mRecorderConfig.getVideoBitrate());
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mTargetFPS);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mRecorderConfig.getKeyFrameIntervalSecond());
        videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);

        Log.d(TAG,VIDEO_MIME_TYPE);
        Log.d(TAG,mVideoWidth+"");
        Log.d(TAG,mVideoHeight+"");
        Log.d(TAG,getVideoEncodeColorFormat()+"");
        Log.d(TAG,mTargetFPS+"");
        Log.d(TAG,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline+"");
        Log.d(TAG,MediaCodecInfo.CodecProfileLevel.AVCLevel31+"");


        mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
        mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mVideoEncoder.start();
        mVideoBufferInfo = new MediaCodec.BufferInfo();

        if (!mOnlyVideo) {
            int mChannelConfig;
            switch (mChannelNum) {
                case 1:
                    mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
                    break;
                case 2:
                    mChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid channel count. Must be 1 or 2");
            }
            MediaFormat audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mOrigSampleRate, mChannelConfig);
            audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, mOrigSampleRate);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannelNum);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mRecorderConfig.getAudioBitrate());

            mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioEncoder.start();
            mAudioBufferInfo = new MediaCodec.BufferInfo();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private int getVideoEncodeColorFormat() {
        boolean isAfterLollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        Log.d(TAG,"isAfterLollipop:"+isAfterLollipop);
        if (!isAfterLollipop)
            mVideoEncoderColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
        else {
            mVideoEncoderColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mVideoWidth, mVideoHeight);
            String encoderName = mcl.findEncoderForFormat(format);


            Log.d(TAG,"mcl:"+mcl);
            Log.d(TAG,"format:"+format);
            Log.d(TAG,"encoderName:"+encoderName);

            MediaCodecInfo.CodecCapabilities codecCapabilities = null;
            for (MediaCodecInfo info : mcl.getCodecInfos()) {
                Log.d(TAG,"info.getName():"+encoderName+" - "+info.getName());
                if (info.getName().equals(encoderName)) {
                    codecCapabilities = info.getCapabilitiesForType(VIDEO_MIME_TYPE);
                    break;
                }
            }

            if (codecCapabilities == null || codecCapabilities.colorFormats == null)
                return mVideoEncoderColorFormat;

            for (int color : codecCapabilities.colorFormats) {
                Log.d(TAG,"color:"+color+" - "+MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                    mVideoEncoderColorFormat = color;
                    return mVideoEncoderColorFormat;
                }
            }
        }

        Log.d(TAG,"mVideoEncoderColorFormat:"+mVideoEncoderColorFormat);

        return mVideoEncoderColorFormat;
    }

    private void initRawData(KSYMediaPlayer player) {
        int width = (mVideoWidth + 15) / 16 * 16;
        int size = mVideoHeight * width * 3 / 2;

        for(int i = 0; i < RAW_DATA_NUM; i++)
        {
            mVideoRawDataBuffer[i] = ByteBuffer.allocate(size);
            player.addVideoRawBuffer(mVideoRawDataBuffer[i].array());
        }
    }

    private void putAudioQueueItem(ByteBuffer buffer, long pts)
    {
        long realPts;
        if (!mFindAudioPts) {
            mFirstAudioPts = pts;
            mFindAudioPts = true;
        }

        realPts = (pts - mFirstAudioPts) * 1000;
        if (mAudioRawDataQueue != null) {
            QueueItem item = new QueueItem(buffer.limit(), realPts);
            item.mByteBuffer.put(buffer.array(), 0, buffer.limit());
            item.mByteBuffer.flip();
            mAudioRawDataQueue.offer(item);
        }
    }

    private QueueItem getAudioQueueItem() {
        QueueItem item = null;
        if (mAudioRawDataQueue != null)
            item = mAudioRawDataQueue.poll();

        return item;
    }

    private void putVideoQueueItem(byte[] buffer, long pts)
    {
        long realPts;
        if (!mFindVideoPts) {
            mFirstVideoPts = pts;
            mFindVideoPts = true;
        }
        realPts = (pts - mFirstVideoPts) * 1000;

        if (mVideoRawDataQueue != null) {
            QueueItem item = new QueueItem(buffer.length, realPts);
            item.mByteBuffer.put(buffer);
            item.mByteBuffer.flip();
            mVideoRawDataQueue.offer(item);
        }
    }

    private QueueItem getVideoQueueItem() {
        QueueItem item = null;
        if (mVideoRawDataQueue != null)
            item = mVideoRawDataQueue.poll();

        return item;
    }

    private void putEncodedQueueItem(ByteBuffer buffer, int type, long pts, int frameType) {
        if (mOutputQueue != null)
        {
            QueueItem item = new QueueItem(buffer, type, pts, frameType);
            mOutputQueue.offer(item);
        }
    }

    private void putKeyInfoQueueItem(ByteBuffer buffer, int type, long pts, int frameType) {
        if (mKeyInfoQueue != null)
        {
            QueueItem item = new QueueItem(buffer, type, pts, frameType);
            mKeyInfoQueue.offer(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void dequeueInputBufferForNonPlanarYUV(QueueItem item, int index) {
        Log.d(TAG,"dequeueInputBufferForNonPlanarYUV");
        Image image = mVideoEncoder.getInputImage(index);
        ByteBuffer rawBuffer = item.mByteBuffer;
        int stride = (mVideoWidth + 15) / 16 * 16;
        int height = image.getHeight();
        int width = image.getWidth();
        int position = 0;

        for (int i = 0; i < image.getPlanes().length; i++)
        {
            ByteBuffer buffer = image.getPlanes()[i].getBuffer();
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            int srcStride = w;
            int dstStride = image.getPlanes()[i].getRowStride();
            int length = h * srcStride;
            int dstPixelStride = image.getPlanes()[i].getPixelStride();

            if (dstStride == srcStride) {
                buffer.put(rawBuffer.array(), position, length);
            } else {
                int dstPosition = 0;
                int srcPosition = position;
                byte[] srcArray = rawBuffer.array();

                for (int j = 0; j < h; j++) {
                    dstPosition = j * dstPixelStride * w;
                    for (int col = 0; col < w; col++)
                        buffer.put(dstPosition + col * dstPixelStride, srcArray[srcPosition + col]);
                    srcPosition += w;
                }
            }

            position += length;
        }
        mVideoEncoder.queueInputBuffer(index, 0, item.mByteBuffer.array().length, item.mDataPts, 0);
    }

    private void dequeueVideoInputBuffer() {

        while(!mAbort) {
            QueueItem item = getVideoQueueItem();
            if (item != null && item.mByteBuffer != null)
            {
                int encoderIndex = mVideoEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (encoderIndex >= 0)
                {
                    if (!isSupportPlanarYUV())
                    {
                        dequeueInputBufferForNonPlanarYUV(item, encoderIndex);
                    }
                    else
                    {
                        ByteBuffer inputBuffer = mVideoEncoder.getInputBuffers()[encoderIndex];
                        if (inputBuffer == null)
                            throw new RuntimeException("Video Encoder Input Buffer is null!");
                        inputBuffer.clear();
                        inputBuffer.put(item.mByteBuffer.array());

                        mVideoEncoder.queueInputBuffer(encoderIndex, 0, item.mByteBuffer.array().length, item.mDataPts, 0);
                    }
                }
            } else {
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void dequeueVideoOutputBuffer() {
        ByteBuffer[] outputBuffers = mVideoEncoder.getOutputBuffers();

        while (!mAbort) {
            int encoderIndex = mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, TIMEOUT_USEC);
            if (encoderIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
            } else if (encoderIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
            } else if (encoderIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                if (mMediaMuxThread == null) {
                    mMediaMuxThread = new Thread(new MediaMuxWrapper());
                    mMediaMuxThread.start();
                }
                mMuxerStarted = true;
                if (!mOnlyVideo) {
                    if (mAudioDequeueInputThread == null) {
                        mAudioDequeueInputThread = new Thread(new MediaRecorderWrapper(this, true, true));
                        mAudioDequeueInputThread.start();
                    }

                    if (mAudioDequeueOutputThread == null) {
                        mAudioDequeueOutputThread = new Thread(new MediaRecorderWrapper(this, false, true));
                        mAudioDequeueOutputThread.start();
                    }
                }
            } else if (encoderIndex < 0) {
                Log.w("KSYMediaRecorder", "unexpected result from encoder.dequeueVideoOutputBuffer: " +
                        encoderIndex);
                // let's ignore it
            } else {
                ByteBuffer encodedData = outputBuffers[encoderIndex];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderIndex +
                            " was null");
                }

                if (mVideoBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    int frameType = KSY_FRAME_TYPE_OTHER;
                    // adjust the ByteBuffer values to match BufferInfo
                    encodedData.position(mVideoBufferInfo.offset);
                    encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);

                    if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        frameType = KSY_FRAME_TYPE_SEQUENCE_HEADER;
                        putKeyInfoQueueItem(encodedData, KSY_SAMPLE_DATA_TYPE_VIDEO, mVideoBufferInfo.presentationTimeUs, frameType);
                        if (mOnlyVideo)
                            mWriteKeyInfo = true;
                    } else {
                        if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) > 0) {
                            frameType = KSY_FRAME_TYPE_KEY_FRAME;
                        }

                        putEncodedQueueItem(encodedData, KSY_SAMPLE_DATA_TYPE_VIDEO, mVideoBufferInfo.presentationTimeUs, frameType);
                    }

                    mPrevWriteVideoPTS = mVideoBufferInfo.presentationTimeUs;
                }

                mVideoEncoder.releaseOutputBuffer(encoderIndex, false);
            }
        }
    }

    private void dequeueAudioInputBuffer() {
        ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();

        while (!mAbort) {
            QueueItem item = getAudioQueueItem();
            if (item != null && item.mByteBuffer != null) {
                ByteBuffer buffer = item.mByteBuffer;
                long pts;
                int offset = buffer.position();

                while (offset < buffer.limit() && !mAbort) {
                    int encoderInputIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                    if (encoderInputIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[encoderInputIndex];
                        int copySize = buffer.limit() - offset;
                        inputBuffer.clear();

                        if (copySize > (inputBuffer.limit() - inputBuffer.position()))
                            copySize = inputBuffer.limit() - inputBuffer.position();

                        pts = item.mDataPts + 1000000 / mOrigSampleRate * offset;

                        inputBuffer.put(buffer.array(), offset, copySize);
                        offset += copySize;
                        mAudioEncoder.queueInputBuffer(encoderInputIndex, 0, copySize, pts, 0);
                    }
                }
            }
        }
    }

    private void dequeueAudioOutputBuffer() {
        ByteBuffer[] outputBuffers = mAudioEncoder.getOutputBuffers();

        while (!mAbort) {
            int encoderOutputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, TIMEOUT_USEC);
            if (encoderOutputIndex >= 0) {
                ByteBuffer buffer = outputBuffers[encoderOutputIndex];

                buffer.position(mAudioBufferInfo.offset);
                buffer.limit(mAudioBufferInfo.offset + mAudioBufferInfo.size);

                if (mPrevWriteAudioPTS == 0)
                    mPrevWriteAudioPTS = mAudioBufferInfo.presentationTimeUs;

                if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    putKeyInfoQueueItem(buffer, KSY_SAMPLE_DATA_TYPE_AUDIO, mAudioBufferInfo.presentationTimeUs, KSY_FRAME_TYPE_ADTS);
                    mWriteKeyInfo = true;
                } else {
                    if (mPrevWriteAudioPTS <= mAudioBufferInfo.presentationTimeUs) {
                        mPrevWriteAudioPTS = mAudioBufferInfo.presentationTimeUs;
                        putEncodedQueueItem(buffer, KSY_SAMPLE_DATA_TYPE_AUDIO, mAudioBufferInfo.presentationTimeUs, KSY_FRAME_TYPE_OTHER);
                    }
                }
                mAudioEncoder.releaseOutputBuffer(encoderOutputIndex, false);
            }
        }
    }

    private class MediaRecorderWrapper implements Runnable {
        private KSYMediaRecorder mRecorder;
        private boolean mIsInput;
        private boolean mIsAudio;

        public MediaRecorderWrapper(KSYMediaRecorder recorder, boolean input, boolean isAudio) {
            mRecorder = recorder;
            mIsInput = input;
            mIsAudio = isAudio;
        }

        @Override
        public void run() {
            if (mRecorder != null)
            {
                if (mIsAudio) {
                    if (mIsInput)
                        mRecorder.dequeueAudioInputBuffer();
                    else
                        mRecorder.dequeueAudioOutputBuffer();
                } else {
                    if (mIsInput)
                        mRecorder.dequeueVideoInputBuffer();
                    else
                        mRecorder.dequeueVideoOutputBuffer();
                }
            }
        }
    }

    private class MediaMuxWrapper implements Runnable {

        @Override
        public void run() {
            while (!mAbort) {
                if (!mWriteKeyInfo) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                QueueItem item = mKeyInfoQueue.poll();
                if (item == null)
                    break;

                int flag = mKeyInfoQueue.size() > 0 ? 0 : 1;
                native_writeKeyData(item.mByteBuffer.array(), item.mType, item.mFrameType, flag);
                if (flag > 0)
                    break;
            }

            while (!mAbort) {
                QueueItem item = mOutputQueue.poll();

                if (item == null) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                native_writeSampleData(item.mByteBuffer.array(), item.mType, item.mDataPts, item.mFrameType);
            }
        }
    }

    private boolean isSupportPlanarYUV() {
        return mVideoEncoderColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private class QueueItem {
        private ByteBuffer mByteBuffer;
        private long mDataPts;
        private int mType;
        private int mFrameType;

        public QueueItem(int capacity, long pts) {
            mByteBuffer = ByteBuffer.allocate(capacity);
            mDataPts = pts;
        }

        public QueueItem(ByteBuffer buffer, int type, long pts, int frameType) {
            mByteBuffer = ByteBuffer.allocate(buffer.limit());
            buffer.get(mByteBuffer.array());
            mByteBuffer.flip();
            mType = type;
            mDataPts = pts;
            mFrameType = frameType;
        }
    }

    private native void native_writeKeyData(byte[] data, int type, int frameType, int writeHead);
    private native void native_writeSampleData(byte[] data, int type, long pts, int frameType);
    private native void native_stop();
}
