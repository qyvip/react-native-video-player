import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  Vibration,
  View
} from 'react-native';
import BarcodeScanner from 'react-native-camera';

export default class ScanScreen extends Component {
  constructor(props) {
    super(props);
    this.state = {
      barcode: '',
      cameraType: 'back',
      text: '扫描二维码',
      torchMode: 'off',
      type: '',
    };
  }


  barcodeReceived(e) {
    this.setState({
      barcode: e.data,
      text: `${e.data}`,
      type: e.type,
    });
    
    const {navigate,goBack,state} = this.props.navigation;
    state.params.callback(e.data);
    goBack();
  }
  render() {
    return (
      <View style={styles.container}>
        <BarcodeScanner
          onBarCodeRead={this.barcodeReceived.bind(this)}
          style={{ flex: 1 }}
          torchMode={this.state.torchMode}
          cameraType={this.state.cameraType}
        />
        <View style={styles.statusBar}>
          <Text style={styles.statusBarText}>{this.state.text}</Text>
        </View>
      </View>
    );
  }
}
const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  statusBar: {
    height: 100,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusBarText: {
    fontSize: 20,
  },
}); 
