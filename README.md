## react-native-ksyvideo

A `<KSYVideo>` component for react-native, as seen in

Requires react-native >= 0.49.0

### Add it to your project

Run `npm install react-native-ksyvideo --save`


#### Android

Run `react-native link` to link the react-native-ksyvideo library.

Or if you have trouble, make the following additions to the given files manually:

**android/settings.gradle**

```gradle
include ':react-native-ksyvideo'
project(':react-native-ksyvideo').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-ksyvideo/android')
```

**android/app/build.gradle**

```gradle
dependencies {
   ...
   compile project(':react-native-ksyvideo')
}
```

**MainApplication.java**

On top, where imports are:

```java
import com.brentvatne.react.ReactKSYVideoPackage;
```

Add the `ReactKSYVideoPackage` class to your list of exported packages.

```java
@Override
protected List<ReactPackage> getPackages() {
    return Arrays.asList(
            new MainReactPackage(),
            new ReactKSYVideoPackage()
    );
}
```

## Usage

```javascript

<KSYVideo source={{uri: "rtmp://"}}   // Can be a URL or a local file.
       ref={(ref) => {
         this.player = ref
       }}                                      // Store reference
  
       volume={1.0}                            
       muted={false}                           
       paused={false}                          // Pauses playback entirely.
       resizeMode="cover"                      // Fill the whole screen at aspect ratio.*
       repeat={true}                           // Repeat forever.
       playInBackground={false}                // Audio continues to play when app entering background.
       progressUpdateInterval={250.0}          // Interval to fire onProgress (default to ~250ms)
       onLoadStart={this.loadStart}            // Callback when video starts to load
       onLoad={this.setDuration}               // Callback when video loads
       onProgress={this.setTime}               // Callback every ~250ms with currentTime
       onEnd={this.onEnd}                      // Callback when playback finishes
       onError={this.videoError}               // Callback when video cannot be loaded
       onBuffer={this.onBuffer}                // Callback when remote video is buffering
       style={styles.backgroundVideo} />


// Later on in your styles..
var styles = StyleSheet.create({
  backgroundVideo: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
  },
});
```

## Remarks
If you want to updata native sdk for KSYVideo,make the following additions
### Android
The KSYVideo is dependented on jcenter (https://bintray.com/ksvc/ksyplayer),you can modify the dependencies ,update to high version

build.gradle(Module:react-native-ksyvideo)
dependencies {
    compile "com.facebook.react:react-native:+"  // From node_modules

    compile 'com.ksyun.media:libksyplayer-arm64:2.1.0'
    compile 'com.ksyun.media:libksyplayer-x86:2.1.0'
    compile 'com.ksyun.media:libksyplayer-armv7a:2.1.0'
    compile 'com.ksyun.media:libksyplayer-java:2.1.0'
}

