//
//  RCTKSYVideo.h
//  RCTKSYVideo
//
//  Created by mayudong on 2017/11/27.
//  Copyright © 2017年 mayudong. All rights reserved.
//

#import <React/RCTView.h>
#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import "KSYAVWriter.h"

@class RCTEventDispatcher;

@interface RCTKSYVideo : UIView

@property (nonatomic, strong)    KSYAVWriter              *avWriter;
@property (nonatomic, assign)    BOOL                      isRecording;
@property (nonatomic, copy) KSYPlyVideoDataBlock videoDataBlock;
@property (nonatomic, copy) KSYPlyAudioDataBlock audioDataBlock;

@property (nonatomic, copy) RCTBubblingEventBlock onVideoLoadStart;
@property (nonatomic, copy) RCTBubblingEventBlock onVideoLoad;

@property (nonatomic, copy) RCTBubblingEventBlock onVideoError;
@property (nonatomic, copy) RCTBubblingEventBlock onVideoProgress;
@property (nonatomic, copy) RCTBubblingEventBlock onVideoSeek;
@property (nonatomic, copy) RCTBubblingEventBlock onVideoEnd;
@property (nonatomic, copy) RCTBubblingEventBlock onVideoTouch;

@property (nonatomic, copy) RCTBubblingEventBlock onReadyForDisplay;
@property (nonatomic, copy) RCTBubblingEventBlock onPlaybackStalled;
@property (nonatomic, copy) RCTBubblingEventBlock onPlaybackResume;

@property (nonatomic, copy) RCTBubblingEventBlock onVideoSaveBitmap;

@property (nonatomic, copy) RCTBubblingEventBlock onRecordVideo;
@property (nonatomic, copy) RCTBubblingEventBlock onStopRecordVideo;

- (instancetype)initWithEventDispatcher:(RCTEventDispatcher *)eventDispatcher NS_DESIGNATED_INITIALIZER;
- (void)saveBitmap:(NSString *)data;
- (void)recordVideo:(NSString *)data;
- (void)stopRecordVideo:(NSString *)data;
@end
