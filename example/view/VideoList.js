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
  StatusBar
} from 'react-native';
import ScrollableTabView, { DefaultTabBar, } from 'react-native-scrollable-tab-view';
import KSYVideo from './KSYVideo';

export default class VideoList extends Component {
    constructor(props) {
      super(props);
      this.state = {
        screenshots: [],
        records:[]
      };
    }

    componentWillMount(){

      KSYVideo.getAllScreenshots((data)=>{
        this.setState({screenshots:data.files});
      })
    }

    render() {
    return (
      <ScrollableTabView
            style={{marginTop: 20, }}
            initialPage={1}
            renderTabBar={() => <DefaultTabBar />}
          >
            <ScrollView tabLabel="截图" style={styles.tabView}>
              <View style={styles.card}>
              <FlatList
                data={this.state.screenshots}
                renderItem={({item}) => <Text>{item}</Text>}
              />
              </View>
            </ScrollView>
            <ScrollView tabLabel="录像" style={styles.tabView}>
              <View style={styles.card}>
                <Text>Friends</Text>
              </View>
            </ScrollView>
          </ScrollableTabView>
    );
  }
}

const styles = StyleSheet.create({
  tabView: {
    flex: 1,
    padding: 10,
    backgroundColor: 'rgba(0,0,0,0.01)',
  },
  card: {
    borderWidth: 1,
    backgroundColor: '#fff',
    borderColor: 'rgba(0,0,0,0.1)',
    margin: 5,
    height: 150,
    padding: 15,
    shadowColor: '#ccc',
    shadowOffset: { width: 2, height: 2, },
    shadowOpacity: 0.5,
    shadowRadius: 3,
  },
});
