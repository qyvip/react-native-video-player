/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 * @flow
 */
import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View,
  TextInput,
  NativeModules,
  processColor,
  TouchableOpacity,
  Image,
  StatusBar
} from 'react-native';
import KSYVideo from './KSYVideo';
import ProgressController from './ProgressController'

export default class VodScreen extends Component {
    constructor(props) {
      super(props);
      this.state = {showbar: true, 
        record: false, 
        paused: false, 
        windowWidth: 0, 
        windowHeight: 0,
        duration: 0.0,
        currentTime: 0.0,
      };
    }

    _onLayout(event) 
    {  
      let {x,y,width,height} = event.nativeEvent.layout;  
      this.setState({windowWidth:width, windowHeight:height});
    } 

    _onLoad(data){
      this.setState({ duration: data.duration });
    };

    _onProgress(data){
      if (this.state.showbar){
       this.setState({ currentTime: data.currentTime });
      }
    }

    _onVideoSaveBitmap(data){
      alert(data);
      console.log("视频截图返回结果：",data);
    }

    _onRecordVideo(data){
      alert(data.uri);
      console.log("视频录像开始，保存路径：",data);
    }

    _onStopRecordVideo(data){
      alert(data.timeSpent);
      console.log("视频录像停止：",data);
    }
    
    onProgressChanged(newPercent, paused) {
        if (paused){
          this.setState({paused: !this.state.paused});
        }
        else if (newPercent >= 0)
        {
          let {duration} = this.state;
          if (duration > 0){
            let newTime = newPercent * duration / 100;
            this.setState({currentTime: newTime});
            this.video.seek(newTime);
          }
        }
    }

    getCurrentTimePercentage(currentTime, duration) {
        if (currentTime > 0) {
            return parseFloat(currentTime) / parseFloat(duration);
        } else {
            return 0.0;
        }
    }

    render() {
    const { params } = this.props.navigation.state;
    let {currentTime, duration, paused, windowHeight} = this.state;
    const completedPercentage = this.getCurrentTimePercentage(currentTime, duration) * 100;
    return (
        <View style={styles.container} onLayout={this._onLayout.bind(this)}>   
           
            <StatusBar
              hidden={!this.state.showbar}
              />
          <View style={{width:100,height:20,backgroundColor:'#ccc'}}></View>
          <KSYVideo
              ref={(video)=>{this.video = video}}
              source={{uri:params.user}}
              videoFrame={{x:10,y:200,width:300,height:200}}
              timeout={{prepareTimeout:60, readTimeout:60}}
              paused={this.state.paused}
              playInBackground={true}
              controls={true}
              onTouch={()=>{
                              if (!this.state.record)
                                this.setState({showbar: !this.state.showbar})
                            }
                      }
              onLoad={this._onLoad.bind(this)}
              onEnd={()=>{this.props.navigation.goBack();console.log("JS onCompletion");}}
              onError={(data)=>{this.props.navigation.goBack();console.log("JS onError:" + data.errorcode);}}
              onProgress={this._onProgress.bind(this)}
              onReadyForDisplay = {(data)=>{console.log("JS Video render start");}}
              onVideoSaveBitmap = {this._onVideoSaveBitmap.bind(this)}
              onRecordVideo = {this._onRecordVideo.bind(this)}
              onStopRecordVideo = {this._onStopRecordVideo.bind(this)}
              style={styles.fullScreen}
            />
        
          {this.state.showbar?(
            <View style={{height: 10, marginRight:10, alignSelf:'flex-end'}}>
              <View style={{height:windowHeight, justifyContent:'center'}}>
                <TouchableOpacity onPress={()=>{this.video.saveBitmap();}}>
                   <Image style={{width:40,height:40}} source={require("../res/images/screen_shot.png")}/>
                </TouchableOpacity>

                <TouchableOpacity style={{marginTop:40}} onPress={()=>{this.setState({record:true}); this.video.recordVideo();}}>
                  <Image style={{width:40,height:40}} source={require("../res/images/screen_cap.png")}/>
                </TouchableOpacity>
              </View>
            </View>):(null)}      

          {this.state.record?(
            <View style={{alignSelf:'center'}}>
              <TouchableOpacity onPress={()=>{this.video.stopRecordVideo(); this.setState({record:false})}}>
                  <Image style={{width:50,height:50}} source={require("../res/images/cap_pause.png")}/>
              </TouchableOpacity>
            </View>
          ):(null)}
        
          {this.state.showbar?(
            <View style={styles.controller}>
              <View style={styles.progressBar}>
                <ProgressController duration={duration}
                                    paused={this.state.paused}
                                    currentTime={currentTime}
                                    percent={completedPercentage}
                                    onNewPercent={this.onProgressChanged.bind(this)}/>
              </View>
            </View>):(null)}

        </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    //justifyContent: 'center',
    //alignItems: 'center',
    // justifyContent: 'space-between',
    backgroundColor: 'red',
  },

  fullScreen: {
    // position: 'absolute',
    top: 10,
    left: 30,
    bottom: 0,
    right: 0,
width:350,
height:200,
    backgroundColor: 'blue',
  },

 controller: {
    height: 30,
    justifyContent: "center",
    alignItems: "center"
  },

  progressBar: {
    alignSelf: "stretch",
    margin: 30
  },
});
