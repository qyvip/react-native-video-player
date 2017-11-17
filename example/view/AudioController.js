"use strict";
import _ from "lodash";
import React, {Component} from "react";
import PropTypes from 'prop-types';
import {Animated, PanResponder, Slider, StyleSheet, Text, TouchableOpacity, View, Image} from "react-native";

let radiusOfHolder = 8;
let radiusOfActiveHolder = 12;
class AudioController extends Component {

    constructor(props, context, ...args) {
        super(props, context, ...args);

        this.state = {lineX: new Animated.Value(0), slideX: new Animated.Value(0)};
        //let {slideX} = this.state;
        //slideX.setValue(75);
    }

    componentWillReceiveProps(nextProps) {
        if (!this.state.moving) {
            this.state.slideX.setValue(this.computeScreenX(nextProps.percent));
        }
    }

    computeScreenX(percent) {
        return percent * this.state.width / 100;
    }

    componentWillMount() {
        console.log("JS  onLayout");
        this.holderPanResponder = PanResponder.create({
            onStartShouldSetPanResponder: (evt, gestureState) => true,
            onMoveShouldSetPanResponder: (evt, gestureState) => true,
            onPanResponderGrant: (e, gestureState) => {
                let {slideX} = this.state;
                this.setState({moving: true});
                slideX.setOffset(slideX._value);
                slideX.setValue(0);
            },
            onPanResponderMove: (e, gestureState) => {
                let totalX = this.state.slideX._offset + gestureState.dx;
                let newPercent = (totalX / this.state.width) * 100;
                this.notifyPercentChange(newPercent);
                Animated.event([
                    null, {dx: this.state.slideX}
                ])(e, gestureState);
            },
            onPanResponderRelease: (e, gesture) => {
                this.state.slideX.flattenOffset();
                let newPercent = (this.state.slideX._value / this.state.width) * 100;
                this.setState({moving: false});
                this.notifyPercentChange(newPercent);
            }
        });
    }

    notifyPercentChange(newPercent) {
        let {onNewPercent} = this.props;
        if (onNewPercent instanceof Function) {
            onNewPercent(newPercent);
        }
    }

    onLayout(e) {
        console.log("JS  onLayout");
        var width = e.nativeEvent.layout.width - (radiusOfHolder * 2);
        this.setState({width: e.nativeEvent.layout.width - (radiusOfHolder * 2)});
        if (!this.state.moving) {
            let {percent} = this.props;
            this.state.slideX.setValue(percent*width/100);
        }
    }

    getHolderStyle() {
        let {moving, slideX, width} = this.state;

        if (width > 0) {
            var interpolatedAnimation = slideX.interpolate({
                inputRange: [0, width],
                outputRange: [0, width],
                extrapolate: "clamp"
            });
            return [styles.holder, moving && styles.activeHolder,
                (slideX._value || moving) && {transform: [{translateX: interpolatedAnimation}]}
            ];
        } else {
            return [styles.holder];
        }
    }

    onLinePressed(e) {
        let newPercent = (e.nativeEvent.locationX / this.state.width) * 100;
        this.notifyPercentChange(newPercent);
    }

    onShowAudioProgress(){
       this.setState({showAudioProgress: !this.state.showAudioProgress});
    }

    render() {
        let {moving} = this.state;
        let {percent} = this.props;
        return <View style={styles.view}>   
            <TouchableOpacity style={{padding:0, justifyContent: "center", alignItems: "flex-start", width:40, height:40}}
                onPress={this.onShowAudioProgress.bind(this)}>

                <Image style={{padding:0, width:40, height:40}} source={require("../res/images/volumn.png")}/>
                
            </TouchableOpacity>

            {this.state.showAudioProgress?(
                <View style={styles.barView}
                      onLayout={this.onLayout.bind(this)} {...this.holderPanResponder.panHandlers}>
                    <View style={{flex: 1, flexDirection: "row", top: moving ? radiusOfActiveHolder : radiusOfHolder}}>
                        <TouchableOpacity style={[styles.line, {flex: percent, borderColor: "pink"}]}
                                          onPress={this.onLinePressed.bind(this)}/>
                        <TouchableOpacity style={[styles.line, {flex: 100 - percent, borderColor: "white"}]}
                                          onPress={this.onLinePressed.bind(this)}/>
                    </View>
                    <Animated.View style={this.getHolderStyle()}/>
                </View>):(null)
            }
        </View>
    }
}

let height = 40;
let styles = StyleSheet.create({
    view: {flex: 1, flexDirection: "row", alignItems: "center"},
    barView: {flex: 1},
    timeText: {color: "white"},
    line: {borderWidth: 2, padding: 0},
    holder: {
        height: radiusOfHolder * 2,
        width: radiusOfHolder * 2,
        borderRadius: radiusOfHolder,
        backgroundColor: "white"
    },
    activeHolder: {
        height: radiusOfActiveHolder * 2,
        width: radiusOfActiveHolder * 2,
        borderRadius: radiusOfActiveHolder,
        backgroundColor: "white"
    }
});

AudioController.propTypes = {
    percent: PropTypes.number,
    onNewPercent: PropTypes.func,
};

export default AudioController;
