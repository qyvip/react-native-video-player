import React, { Component } from 'react';
import {
  AppRegistry,
  Text,
  View,
  Button,
  TextInput,
  StyleSheet,
  TouchableOpacity
} from 'react-native';
import { StackNavigator } from 'react-navigation';
import VodScreen from './view/VodScreen';
import LiveScreen from './view/LiveScreen';
import ScanScreen from './view/ScanScreen';
import VideoList from './view/VideoList'
class HomeScreen extends Component {
  static navigationOptions = {
    title: 'Welcome KsyMediaplay',
  };
//rtmp://live.hkstv.hk.lxdns.com/live/hks  http://192.168.77.35:8000/iphone7.mp4 /storage/emulated/0/Download/iphone7.mp4 /storage/emulated/0/Download/bbb_720p_qy265.flv
  constructor(props) {
      super(props);
      this.state = { text: 'rtmp://live.hkstv.hk.lxdns.com/live/hks' };
    }

  render() {
    const { navigate } = this.props.navigation;
    var value = this.state.text;
    return (
      <View>
        <View style={{flexDirection:'row', justifyContent:'flex-start', alignItems: 'center',}}>
          <TextInput
            style={{height: 40, width: 300}}
            onChangeText={(text) => this.setState({text})}
            value={this.state.text}
          />
          
          <TouchableOpacity 
            style={{marginLeft:10}} 
            onPress={() =>  navigate('ScanScreen', {callback: (data)=>{ this.setState({text:data}) ;}} ) }>
              <Text style={{color:'black'}}>扫一扫</Text>
          </TouchableOpacity>

        </View>

        <TouchableOpacity style={{marginTop:10}} >
          <Button
            onPress={() =>  navigate('PlayVod', { user: value }) }
            title="点播"
          />
        </TouchableOpacity>
        
        <TouchableOpacity style={{marginTop:10}} >
          <Button
            onPress={() =>  navigate('PlayLive', { user: value }) }
            title="直播"
          />
          </TouchableOpacity>
          <TouchableOpacity style={{marginTop:10}} >
          <Button
            onPress={() =>  navigate('VideoList', { user: value }) }
            title="录像列表"
          />
      </TouchableOpacity>

      </View>
    );
  }
}

export const KSYMediaPlayer_RN = StackNavigator({
  Home: { screen: HomeScreen },
  PlayVod: { screen: VodScreen , navigationOptions:{
              header:null
            }},
  PlayLive: { screen: LiveScreen , navigationOptions:{
              header:null
            }},
  ScanScreen: {screen: ScanScreen},
  VideoList:{screen: VideoList}
});

export default class App extends React.Component {
  render() {
    return <KSYMediaPlayer_RN />;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'flex-start',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
});
