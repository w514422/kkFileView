package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.utils.DownloadUtils;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author : kl
 * @authorboke : kailing.pub
 * @create : 2018-03-25 上午11:58
 * @description:
 **/
@Service
public class MediaFilePreviewImpl implements FilePreview {

    private static final Logger logger = LoggerFactory.getLogger(MediaFilePreviewImpl.class);
    private final FileHandlerService fileHandlerService;
    private final OtherFilePreviewImpl otherFilePreview;
    private static final String mp4 = "mp4";

    // 添加线程池管理视频转换任务
    private static final ExecutorService videoConversionExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // 添加转换任务缓存，避免重复转换
    private static final Map<String, Future<String>> conversionTasks = new HashMap<>();

    public MediaFilePreviewImpl(FileHandlerService fileHandlerService, OtherFilePreviewImpl otherFilePreview) {
        this.fileHandlerService = fileHandlerService;
        this.otherFilePreview = otherFilePreview;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        String fileName = fileAttribute.getName();
        String suffix = fileAttribute.getSuffix();
        String cacheName = fileAttribute.getCacheName();
        String outFilePath = fileAttribute.getOutFilePath();
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();
        FileType type = fileAttribute.getType();
        String[] mediaTypesConvert = FileType.MEDIA_CONVERT_TYPES;  //获取支持的转换格式
        boolean mediaTypes = false;
        for (String temp : mediaTypesConvert) {
            if (suffix.equals(temp)) {
                mediaTypes = true;
                break;
            }
        }

        if (!url.toLowerCase().startsWith("http") || checkNeedConvert(mediaTypes)) {  //不是http协议的 //   开启转换方式并是支持转换格式的
            if (forceUpdatedCache || !fileHandlerService.listConvertedFiles().containsKey(cacheName) || !ConfigConstants.isCacheEnabled()) {  //查询是否开启缓存
                ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
                if (response.isFailure()) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                }
                String filePath = response.getContent();
                String convertedUrl = null;
                try {
                    if (mediaTypes) {
                        // 检查是否已有正在进行的转换任务
                        Future<String> conversionTask = conversionTasks.get(cacheName);
                        if (conversionTask != null && !conversionTask.isDone()) {
                            // 等待现有转换任务完成
                            convertedUrl = conversionTask.get();
                        } else {
                            // 提交新的转换任务
                            conversionTask = videoConversionExecutor.submit(() -> {
                                return convertToMp4(filePath, outFilePath, fileAttribute);
                            });
                            conversionTasks.put(cacheName, conversionTask);
                            convertedUrl = conversionTask.get();
                        }
                    } else {
                        convertedUrl = outFilePath;  //其他协议的  不需要转换方式的文件 直接输出
                    }
                } catch (Exception e) {
                    logger.error("Failed to convert media file: {}", filePath, e);
                    // 清理失败的任务
                    conversionTasks.remove(cacheName);
                }
                if (convertedUrl == null) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, "视频转换异常，请联系管理员");
                }
                if (ConfigConstants.isCacheEnabled()) {
                    // 加入缓存
                    fileHandlerService.addConvertedFile(cacheName, fileHandlerService.getRelativePath(outFilePath));
                }
                // 转换完成后清理任务缓存
                conversionTasks.remove(cacheName);
                model.addAttribute("mediaUrl", fileHandlerService.getRelativePath(outFilePath));
            } else {
                model.addAttribute("mediaUrl", fileHandlerService.listConvertedFiles().get(cacheName));
            }
            return MEDIA_FILE_PREVIEW_PAGE;
        }
        if (type.equals(FileType.MEDIA)) {  // 支持输出 只限默认格式
            model.addAttribute("mediaUrl", url);
            return MEDIA_FILE_PREVIEW_PAGE;
        }
        return otherFilePreview.notSupportedFile(model, fileAttribute, "系统还不支持该格式文件的在线预览");
    }

    /**
     * 检查视频文件转换是否已开启，以及当前文件是否需要转换
     *
     * @return
     */
    private boolean checkNeedConvert(boolean mediaTypes) {
        //1.检查开关是否开启
        if ("true".equals(ConfigConstants.getMediaConvertDisable())) {
            return mediaTypes;
        }
        return false;
    }

    private static String convertToMp4(String filePath, String outFilePath, FileAttribute fileAttribute) throws Exception {
        FFmpegFrameGrabber frameGrabber = null;
        FFmpegFrameRecorder recorder = null;
        try {
            File desFile = new File(outFilePath);
            //判断一下防止重复转换
            if (desFile.exists()) {
                return outFilePath;
            }

            if (fileAttribute.isCompressFile()) { //判断 是压缩包的创建新的目录
                int index = outFilePath.lastIndexOf("/");  //截取最后一个斜杠的前面的内容
                String folder = outFilePath.substring(0, index);
                File path = new File(folder);
                //目录不存在 创建新的目录
                if (!path.exists()) {
                    path.mkdirs();
                }
            }

            frameGrabber = FFmpegFrameGrabber.createDefault(filePath);
            frameGrabber.start();

            // 优化：使用更快的编码预设
            recorder = new FFmpegFrameRecorder(outFilePath,
                    frameGrabber.getImageWidth(),
                    frameGrabber.getImageHeight(),
                    frameGrabber.getAudioChannels());

            // 设置快速编码参数
            recorder.setFormat(mp4);
            recorder.setFrameRate(frameGrabber.getFrameRate());
            recorder.setSampleRate(frameGrabber.getSampleRate());

            // 视频编码属性配置 - 使用快速编码预设
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setVideoOption("preset", "veryfast");  // 添加快速编码预设
            recorder.setVideoOption("tune", "zerolatency"); // 降低延迟
            recorder.setVideoBitrate(frameGrabber.getVideoBitrate());
            recorder.setAspectRatio(frameGrabber.getAspectRatio());

            // 音频编码设置
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setAudioBitrate(frameGrabber.getAudioBitrate());
            recorder.setAudioChannels(frameGrabber.getAudioChannels());

            // 优化：当源文件已经是h264/aac编码时，尝试直接复制流
            if (isCompatibleCodec(frameGrabber)) {
                recorder.setVideoOption("c:v", "copy");
                recorder.setAudioOption("c:a", "copy");
            }

            recorder.start();

            // 批量处理帧，提高处理效率
            Frame capturedFrame;
            int batchSize = 100; // 批量处理的帧数
            int frameCount = 0;
            Frame[] frameBatch = new Frame[batchSize];

            while (true) {
                capturedFrame = frameGrabber.grabFrame();
                if (capturedFrame == null) {
                    break;
                }

                frameBatch[frameCount % batchSize] = capturedFrame;
                frameCount++;

                // 批量记录帧
                if (frameCount % batchSize == 0 || capturedFrame == null) {
                    for (int i = 0; i < Math.min(batchSize, frameCount); i++) {
                        if (frameBatch[i] != null) {
                            recorder.record(frameBatch[i]);
                            frameBatch[i] = null; // 释放引用
                        }
                    }
                }
            }

            // 记录剩余的帧
            for (int i = 0; i < frameBatch.length; i++) {
                if (frameBatch[i] != null) {
                    recorder.record(frameBatch[i]);
                }
            }

            logger.info("视频转码完成: {} -> {}", filePath, outFilePath);
            return outFilePath;

        } catch (Exception e) {
            logger.error("Failed to convert video file to mp4: {}", filePath, e);
            // 删除可能已创建的失败文件
            try {
                File failedFile = new File(outFilePath);
                if (failedFile.exists()) {
                    failedFile.delete();
                }
            } catch (SecurityException ex) {
                logger.warn("无法删除失败的转换文件: {}", outFilePath, ex);
            }
            throw e;
        } finally {
            // 确保资源被正确释放
            if (recorder != null) {
                try {
                    recorder.stop();
                    recorder.close();
                } catch (Exception e) {
                    logger.warn("关闭recorder时发生异常", e);
                }
            }
            if (frameGrabber != null) {
                try {
                    frameGrabber.stop();
                    frameGrabber.close();
                } catch (Exception e) {
                    logger.warn("关闭frameGrabber时发生异常", e);
                }
            }

            // 强制垃圾回收，释放FFmpeg相关资源
            System.gc();
        }
    }

    /**
     * 检查源文件是否已经是兼容的编码格式（H264/AAC）
     */
    private static boolean isCompatibleCodec(FFmpegFrameGrabber grabber) {
        try {
            String videoCodec = grabber.getVideoCodecName();
            String audioCodec = grabber.getAudioCodecName();

            boolean videoCompatible = videoCodec != null &&
                    (videoCodec.toLowerCase().contains("h264") ||
                            videoCodec.toLowerCase().contains("h.264"));

            boolean audioCompatible = audioCodec != null &&
                    (audioCodec.toLowerCase().contains("aac") ||
                            audioCodec.toLowerCase().contains("mp3"));

            return videoCompatible && audioCompatible;
        } catch (Exception e) {
            logger.debug("无法获取编解码器信息", e);
            return false;
        }
    }

    /**
     * 清理所有转换任务（应用关闭时调用）
     */
    public static void shutdown() {
        if (!CollectionUtils.isEmpty(conversionTasks)) {
            conversionTasks.values().forEach(task -> task.cancel(true));
            conversionTasks.clear();
        }
        videoConversionExecutor.shutdownNow();
    }
}