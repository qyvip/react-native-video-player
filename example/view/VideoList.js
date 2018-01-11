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
  ScrollView,
  NativeModules,
  FlatList,
  TouchableOpacity,
  Image,
  StatusBar,
  Platform
} from 'react-native';
import KSYVideo from './KSYVideo';
import ScrollableTabView, { DefaultTabBar, } from 'react-native-scrollable-tab-view';
var RNFS = require('react-native-fs');
import moment from 'moment';

export default class VideoList extends Component {

    
    constructor(props) {
      super(props);
      videos=[];
      RootPath = "";
      this.state = {
        screenshots: [],
        records:[]
      };
    }

    componentWillMount(){

      
      if (Platform.OS === 'android') {
        RootPath = RNFS.ExternalStorageDirectoryPath;
      }else{
        RootPath = RNFS.DocumentDirectoryPath;
      }
      RNFS.readDir(RootPath+"/screenshots") // On Android, use "RNFS.DocumentDirectoryPath" (MainBundlePath is not defined)
        .then((result) => {
          console.log('GOT RESULT', result);
          result.sort(function(a, b){
            return b.mtime - a.mtime;
          });
          this.setState({"screenshots": result});
          // stat the first file
          // return Promise.all([RNFS.stat(result[0].path), result[0].path]);
        })
        .catch((err) => {
          console.log(err.message, err.code);
        });

        RNFS.readDir(RootPath+"/records") // On Android, use "RNFS.DocumentDirectoryPath" (MainBundlePath is not defined)
        .then((result) => {
          console.log('GOT RESULT', result);
          result.sort(function(a, b){
            return b.mtime - a.mtime;
          });
          this.setState({"records": result});
          // stat the first file
          // return Promise.all([RNFS.stat(result[0].path), result[0].path]);
        })
        .catch((err) => {
          console.log(err.message, err.code);
        });
    }

    _fileSize=(size)=>{
      if (size < 1024 * 1024){
        return `${(size / 1024).toFixed(2)}k`;
      }else{
        return `${(size / 1024 / 1024).toFixed(2)}M`;
      }
    }

    _onPressScreenshotItemButton=(item)=>{
      alert(item.name);
    }

    _screenshotsRenderItem = ({item}) => {
      let filePath = "file://"+item.path;
      return (
        <TouchableOpacity onPress={()=>{this._onPressScreenshotItemButton(item)}}>
          <View style={styles.rowView}>
            <View style={styles.rowLeftView}><Image source={{uri : filePath}} style={{width:100,height:60}}/></View>
            <View style={styles.rowRightView}>
              <Text style={styles.rowFileName}>{moment(item.mtime).format("YYYY-MM-DD HH:mm:ss")}</Text>
                <Text style={styles.rowFileSize}>{this._fileSize(item.size)}</Text>
            </View>
          </View>
        </TouchableOpacity>
      )
    }

    _recordsRenderItem = ({item,index}) => {
      let filePath = "file://"+item.path;
      let recordScreenshot = "file://"+RootPath+"/recordScreenshots/"+item.name+".png";
      return (
        <TouchableOpacity onPress={()=>{this._onPressScreenshotItemButton(item)}}>
          <View style={styles.rowView}>
            <View style={styles.rowLeftView}>
            <Image source={{uri : recordScreenshot}} style={{width:100,height:60}}/>
            </View>
            <View style={styles.rowRightView}>
              <Text style={styles.rowFileName}>{moment(item.mtime).format("YYYY-MM-DD HH:mm:ss")}</Text>
                <Text style={styles.rowFileSize}>{this._fileSize(item.size)}</Text>
            </View>
          </View>
        </TouchableOpacity>
      )
    }

    _keyExtractor = (item, index) => item.name;

    render() {
    return (
      <ScrollableTabView
            style={{marginTop: 20, }}
            initialPage={0}
            renderTabBar={() => <DefaultTabBar />}
          >
            <ScrollView tabLabel="截图" style={styles.tabView}>
              <View style={styles.card}>
              <FlatList
                data={this.state.screenshots}
                renderItem={this._screenshotsRenderItem}
                keyExtractor={this._keyExtractor}
              />
              </View>
            </ScrollView>
            <ScrollView tabLabel="录像" style={styles.tabView}>
              <View style={styles.card}>
              <FlatList
                data={this.state.records}
                renderItem={this._recordsRenderItem}
                keyExtractor={this._keyExtractor}
              />
              </View>
            </ScrollView>
          </ScrollableTabView>
    );
  }
}

const styles = StyleSheet.create({
  tabView: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.01)',
  },
  card: {
    flex: 1,
    borderWidth: 1,
    backgroundColor: '#fff',
    borderColor: 'rgba(0,0,0,0.1)',
    padding: 5,
    shadowColor: '#ccc',
    shadowOffset: { width: 2, height: 2, },
    shadowOpacity: 0.5,
    shadowRadius: 3,
  },
  rowView:{
    flex: 1,
    flexDirection: 'row',
    height:80,
    padding:5,
  },
  rowLeftView:{
    width:100,
  },
  rowRightView:{
    flex: 1,
    flexDirection: 'column',
  },
  rowFileName:{
    height:30
  },
  rowRightSubView:{
    flex: 1,
    flexDirection: 'row',
  },
  rowFileTime:{
    height:20
  },
  rowFileSize:{
    height:20,
    width:100,
  },
  videoView:{
    width:100,
    height:60,
  }
});
