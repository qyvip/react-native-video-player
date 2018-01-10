//
//  RCTKSYVideo.m
//  RCTKSYVideo
//
//  Created by mayudong on 2017/11/27.
//  Copyright © 2017年 mayudong. All rights reserved.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventDispatcher.h>
#import "FileManager.h"
#import "RCTKSYVideo.h"
#import "KSYAVWriter.h"
#import <KSYMediaPlayer/KSYMediaPlayer.h>

@implementation RCTKSYVideo {
    RCTEventDispatcher *_eventDispatcher;
    KSYMoviePlayerController *_player;
    
    NSMutableArray *registeredNotifications;
    float prepareTimeout;
    float readTimeout;
    
    BOOL _paused;
    BOOL _playInBackground;
    BOOL _playWhenInactive;
    
    
}

- (instancetype)initWithEventDispatcher:(RCTEventDispatcher *)eventDispatcher {
    if ((self = [super init])) {
        _eventDispatcher = eventDispatcher;
        _player = [[KSYMoviePlayerController alloc]initWithContentURL:nil];
        registeredNotifications = [[NSMutableArray alloc] init];
        [self setupObservers:_player];
        [_player addObserver:self forKeyPath:@"currentPlaybackTime" options:nil context:nil];
        
        [self addSubview:_player.view];
        
        _playInBackground = false;
        _playWhenInactive = false;
        
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(applicationWillResignActive:)
                                                     name:UIApplicationWillResignActiveNotification
                                                   object:nil];
        
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(applicationDidEnterBackground:)
                                                     name:UIApplicationDidEnterBackgroundNotification
                                                   object:nil];
        
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(applicationWillEnterForeground:)
                                                     name:UIApplicationWillEnterForegroundNotification
                                                   object:nil];
        
        __weak typeof(self) weakSelf = self;
        
        _player.videoDataBlock = ^(CMSampleBufferRef sampleBuffer){
            //写入视频sampleBuffer
            if(weakSelf && weakSelf.avWriter && weakSelf.isRecording)
                [weakSelf.avWriter processVideoSampleBuffer:sampleBuffer];
        };
        
        _player.audioDataBlock = ^(CMSampleBufferRef sampleBuffer){
            //写入音频sampleBuffer
            if(weakSelf && weakSelf.avWriter && weakSelf.isRecording)
                [weakSelf.avWriter processAudioSampleBuffer:sampleBuffer];
        };
    }
    return self;
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

-(void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event{
    if(self.onVideoTouch){
        self.onVideoTouch(@{});
    }
}

- (void)applicationWillResignActive:(NSNotification *)notification
{
    if (_playInBackground || _playWhenInactive || _paused)
        return;
    
}

- (void)applicationDidEnterBackground:(NSNotification *)notification
{
    if (_playInBackground || _playWhenInactive || _paused)
        return;
    
    [_player pause];
}

- (void)applicationWillEnterForeground:(NSNotification *)notification
{
    if (_playInBackground || _playWhenInactive || _paused)
        return;
    
    [_player play];
}

- (void)setSrc:(NSDictionary *)source {
    NSString *uri = [source objectForKey:@"uri"];
    NSURL* url = [NSURL URLWithString:uri];
    
    [_player reset:NO];
    [_player setUrl:url];

    [_player prepareToPlay];
    
    if(self.onVideoLoadStart) {
        self.onVideoLoadStart(@{});
    }
}

- (void)setVideoFrame:(NSDictionary *)videoFrame {
    if (videoFrame){
        NSNumber* playerX = [videoFrame objectForKey:@"x"];
        NSNumber* playerY = [videoFrame objectForKey:@"y"];
        NSNumber* playerWidth = [videoFrame objectForKey:@"width"];
        NSNumber* playerHeight = [videoFrame objectForKey:@"height"];
        //    if (playerWidth > 0 && playerHeight > 0){
        _player.view.frame = CGRectMake(playerX.intValue, playerY.intValue, playerWidth.intValue, playerHeight.intValue);
        //    }
    }
    
}

- (void)setSeek:(float)seekTime {
    [_player seekTo:seekTime accurate:YES];
    if(self.onVideoSeek) {
        self.onVideoSeek(@{@"currentTime": [NSNumber numberWithFloat:_player.currentPlaybackTime],
                           @"seekTime": [NSNumber numberWithFloat:seekTime],
                           });
    }
}

-(void)setTimeout:(NSDictionary *)timeout {
    NSNumber* prepareTimeout = [timeout objectForKey:@"prepareTimeout"];
    NSNumber* readTimeout = [timeout objectForKey:@"readTimeout"];
    [_player setTimeout:prepareTimeout.intValue readTimeout:readTimeout.intValue];
}

-(void)setBufferTime:(float)time{
    _player.bufferTimeMax = time;
}

-(void)setBufferSize:(int)size{
    _player.bufferSizeMax = size;
}

- (void)setResizeMode:(NSString*)mode{
    NSLog(@"resizemode = %@", mode);
    if([mode isEqualToString:@"stretch"])
        _player.scalingMode = MPMovieScalingModeFill;
    else if([mode isEqualToString:@"cover"])
        _player.scalingMode = MPMovieScalingModeAspectFill;
    else if([mode isEqualToString:@"contain"])
        _player.scalingMode = MPMovieScalingModeAspectFit;
    else
        _player.scalingMode = MPMovieScalingModeNone;
}

-(void)setRepeat:(BOOL)repeat{
    _player.shouldLoop = repeat;
}

-(void)setPaused:(BOOL)paused{
    if(paused)
        [_player pause];
    else
        [_player play];
    
    _paused = paused;
}

-(void)setMuted:(BOOL)muted{
    _player.shouldMute = muted;
}

-(void)setMirror:(BOOL)mirror{
    _player.mirror = mirror;
}

-(void)setVolume:(float)volume{
    [_player setVolume:volume rigthVolume:volume];
}

-(void)setDegree:(int)degree{
    [_player setRotateDegress:degree];
}

- (void)setPlayInBackground:(BOOL)playInBackground {
    _playInBackground = playInBackground;
}

- (void)setPlayWhenInactive:(BOOL)playWhenInactive {
    _playWhenInactive = playWhenInactive;
}

- (void)registerObserver:(NSString *)notification player:(KSYMoviePlayerController*)player {
    [[NSNotificationCenter defaultCenter]addObserver:self
                                            selector:@selector(handlePlayerNotify:)
                                                name:(notification)
                                              object:player];
    [registeredNotifications addObject:notification];
}

- (void)setupObservers:(KSYMoviePlayerController*)player
{
    [self registerObserver:MPMediaPlaybackIsPreparedToPlayDidChangeNotification player:player];
    [self registerObserver:MPMoviePlayerPlaybackStateDidChangeNotification player:player];
    [self registerObserver:MPMoviePlayerPlaybackDidFinishNotification player:player];
    [self registerObserver:MPMoviePlayerLoadStateDidChangeNotification player:player];
    [self registerObserver:MPMovieNaturalSizeAvailableNotification player:player];
    [self registerObserver:MPMoviePlayerFirstVideoFrameRenderedNotification player:player];
    [self registerObserver:MPMoviePlayerFirstAudioFrameRenderedNotification player:player];
    [self registerObserver:MPMoviePlayerSuggestReloadNotification player:player];
    [self registerObserver:MPMoviePlayerPlaybackStatusNotification player:player];
    [self registerObserver:MPMoviePlayerNetworkStatusChangeNotification player:player];
    [self registerObserver:MPMoviePlayerSeekCompleteNotification player:player];
    [self registerObserver:MPMoviePlayerPlaybackTimedTextNotification player:player];
}

- (void)releaseObservers:(KSYMoviePlayerController*)player
{
    for (NSString *name in registeredNotifications) {
        [[NSNotificationCenter defaultCenter] removeObserver:self
                                                        name:name
                                                      object:player];
    }
}

-(void)handlePlayerNotify:(NSNotification*)notify
{
    if (!_player) {
        return;
    }
    if (MPMediaPlaybackIsPreparedToPlayDidChangeNotification ==  notify.name) {
        if(self.onVideoLoad){
            int width = _player.naturalSize.width;
            int height = _player.naturalSize.height;
            NSString* orientation = @"landscape";
            if(width > height)
                orientation = @"landscape";
            else
                orientation = @"portrait";
            
            self.onVideoLoad(@{
                               @"duration" : [NSNumber numberWithDouble:_player.duration],
                               @"currentTime": @0,
                               @"canPlayReverse": @NO,
                               @"canPlayFastForward": @YES,
                               @"canPlaySlowForward": @YES,
                               @"canPlaySlowReverse": @NO,
                               @"canStepBackward": @NO,
                               @"canStepForward": @NO,
                               @"naturalSize": @{
                                       @"width": [NSNumber numberWithInt:width],
                                       @"height": [NSNumber numberWithInt:height],
                                       @"orientation": orientation
                                       },
                               });
        }
    }
    if (MPMoviePlayerPlaybackStateDidChangeNotification ==  notify.name) {
        
    }
    if (MPMoviePlayerLoadStateDidChangeNotification ==  notify.name) {
        if (MPMovieLoadStateStalled & _player.loadState) {
            if(self.onPlaybackStalled) {
                self.onPlaybackStalled(@{});
            }
        }
        
        if (_player.bufferEmptyCount &&
            (MPMovieLoadStatePlayable & _player.loadState ||
             MPMovieLoadStatePlaythroughOK & _player.loadState)){
                if(self.onPlaybackResume) {
                    self.onPlaybackResume(@{});
                }
            }
    }
    if (MPMoviePlayerPlaybackDidFinishNotification ==  notify.name) {
        //结束播放的原因
        int reason = [[[notify userInfo] valueForKey:MPMoviePlayerPlaybackDidFinishReasonUserInfoKey] intValue];
        if (reason ==  MPMovieFinishReasonPlaybackEnded) {
            if(self.onVideoEnd){
                self.onVideoEnd(@{});
            }
        }else if (reason == MPMovieFinishReasonPlaybackError){
            if(self.onVideoError){
                self.onVideoError(@{@"errorcode" : [[notify userInfo] valueForKey:@"error"]});
            }
        }
    }
    if (MPMovieNaturalSizeAvailableNotification ==  notify.name) {
        NSLog(@"video size %.0f-%.0f, rotate:%ld\n", _player.naturalSize.width, _player.naturalSize.height, (long)_player.naturalRotate);
        if(((_player.naturalRotate / 90) % 2  == 0 && _player.naturalSize.width > _player.naturalSize.height) ||
           ((_player.naturalRotate / 90) % 2 != 0 && _player.naturalSize.width < _player.naturalSize.height))
        {
            //如果想要在宽大于高的时候横屏播放，你可以在这里旋转
        }
    }
    if (MPMoviePlayerFirstVideoFrameRenderedNotification == notify.name)
    {
        if(self.onReadyForDisplay) {
            self.onReadyForDisplay(@{});
        }
    }
    
    if (MPMoviePlayerFirstAudioFrameRenderedNotification == notify.name)
    {
        
    }
    
    if (MPMoviePlayerSuggestReloadNotification == notify.name)
    {

    }
    
    if(MPMoviePlayerPlaybackStatusNotification == notify.name)
    {
        int status = [[[notify userInfo] valueForKey:MPMoviePlayerPlaybackStatusUserInfoKey] intValue];
        if(MPMovieStatusVideoDecodeWrong == status)
            NSLog(@"Video Decode Wrong!\n");
        else if(MPMovieStatusAudioDecodeWrong == status)
            NSLog(@"Audio Decode Wrong!\n");
        else if (MPMovieStatusHWCodecUsed == status )
            NSLog(@"Hardware Codec used\n");
        else if (MPMovieStatusSWCodecUsed == status )
            NSLog(@"Software Codec used\n");
        else if(MPMovieStatusDLCodecUsed == status)
            NSLog(@"AVSampleBufferDisplayLayer  Codec used");
    }
    if(MPMoviePlayerNetworkStatusChangeNotification == notify.name)
    {
        
    }
    if(MPMoviePlayerSeekCompleteNotification == notify.name)
    {
        NSLog(@"Seek complete");
    }
    
    if (MPMoviePlayerPlaybackTimedTextNotification == notify.name)
    {

    }
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary *)change
                       context:(void *)context {
    if([keyPath isEqual:@"currentPlaybackTime"])
    {
        if(self.onVideoProgress) {
            self.onVideoProgress(@{
                                   @"currentTime" : [NSNumber numberWithDouble:_player.currentPlaybackTime],
                                   });
        }
    }
}

- (void)removeFromSuperview
{
    [_player stop];
    [_player removeObserver:self forKeyPath:@"currentPlaybackTime" context:nil];
    [self releaseObservers:_player];
    [_player.view removeFromSuperview];
    _player = nil;
}

- (void)saveBitmap:(NSString *)data
{
        dispatch_async(dispatch_get_main_queue(), ^{
            int retState = 0;
            NSString *newFileName;
            UIImage *image = [_player thumbnailImageAtCurrentTime];
            NSString *docmentPath = [[NSString alloc] initWithFormat:@"%@%s", [FileManager getDocumentPath], "/screenshots" ];
    //        if ([FileManager creatDir:docmentPath]){
            [FileManager creatDir:docmentPath];
                NSLog(@"创建截图目录成功:%i",retState);
                newFileName = [FileManager getRandomFileName:@"png"];
                NSData *data = UIImagePNGRepresentation(image);
    //            if ([FileManager creatFile:newFileName  withData:data]){
            NSString *newPath = [[NSString alloc] initWithFormat:@"%@/%@", docmentPath, newFileName ];
            [FileManager creatFile:newPath  withData:data];
                    NSLog(@"图片保存成功:%i",retState);
                    retState = 1;
    //            }else{
    //                NSLog(@"图片保存失败:%i",retState);
    //                retState = -2;
    //            }
    //        }else{
    //            NSLog(@"创建截图目录失败:%i",retState);
    //            retState = -1;
    //        }
            if(self.onVideoSaveBitmap) {
                NSMutableDictionary *gifResponse = [[NSMutableDictionary alloc] init];
                NSURL *fileURL = [NSURL fileURLWithPath:newPath];
                [gifResponse setObject:docmentPath forKey:@"path"];
                [gifResponse setObject:newFileName forKey:@"fileName"];
                [gifResponse setObject:[fileURL absoluteString] forKey:@"uri"];
                NSNumber *fileSizeValue = nil;
                NSError *fileSizeError = nil;
                [fileURL getResourceValue:&fileSizeValue forKey:NSURLFileSizeKey error:&fileSizeError];
                if (fileSizeValue){
                    [gifResponse setObject:fileSizeValue forKey:@"fileSize"];
                }
                NSLog(@"图片保存成功:%@",gifResponse);
                self.onVideoSaveBitmap(gifResponse);
            }
        });

}

- (void)recordVideo:(NSString *)data
{
    if (!_isRecording &&_player.isPreparedToPlay) {

        self.isRecording = YES;
        //初始化KSYAVWriter类
        self.avWriter = [[KSYAVWriter alloc] initWithDefaultCfg];
        //设置待写入的文件名
        
        NSString *docmentPath = [[NSString alloc] initWithFormat:@"%@%s", [FileManager getDocumentPath], "/records" ];
        [FileManager creatDir:docmentPath];
        NSLog(@"创建录像目录");
        NSString *newFileName = [FileManager getRandomFileName:@"mp4"];
        NSString *newPath = [[NSString alloc] initWithFormat:@"%@/%@", docmentPath, newFileName ];
        [self.avWriter setUrl:[NSURL URLWithString:newPath]];
        //开始写入
        [self.avWriter setMeta:[_player getMetadata:MPMovieMetaType_Audio] type:KSYAVWriter_MetaType_Audio];
        [self.avWriter setMeta:[_player getMetadata:MPMovieMetaType_Video] type:KSYAVWriter_MetaType_Video];
        [self.avWriter startRecordDeleteRecordedVideo:NO];
        
        NSMutableDictionary *dataResponse = [[NSMutableDictionary alloc] init];
        NSURL *fileURL = [NSURL fileURLWithPath:newPath];
        [dataResponse setObject:docmentPath forKey:@"path"];
        [dataResponse setObject:newFileName forKey:@"fileName"];
        [dataResponse setObject:[fileURL absoluteString] forKey:@"uri"];

        NSLog(@"开始录像:%@",dataResponse);
        self.onVideoSaveBitmap(dataResponse);

    }
}

- (void)stopRecordVideo:(NSString *)data
{
    //停止写入
    if (_isRecording) {
        [_avWriter stopRecord:^(NSMutableDictionary *dataResponse) {
            if(self.onVideoSaveBitmap) {
                NSLog(@"录像保存成功:%@",dataResponse);
                self.onVideoSaveBitmap(dataResponse);
            }
        }];
        self.isRecording = NO;
    } else {
        [self recordVideo:data];
    }
}

@end
