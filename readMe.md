# MicEngine (App that Produces Engine Sounds Based on Microphone Input)

This app allows you to hear engine sounds through your phone's speaker based on the sound intensity detected by the microphone. As the microphone picks up higher sound levels, the engine sound's volume will increase in real-time. If the sound level increases significantly, additional effects such as exhaust pops and other variations will be triggered.

### Detailed Description:

This app is designed to work on Android devices. It primarily captures microphone data and dynamically adjusts the engine sound's speed and volume based on the sound intensity. As the sound intensity increases, users will hear the engine sound become faster and louder (Car Engine Sound). Below are the details of how each feature will work:

1. **Microphone Access**: The app will request microphone access permission by adding the necessary permissions to the Android manifest file and will request permission from the user while the app is running.
2. **Capturing Microphone Data**: The app will use the `AudioRecord` class to capture microphone data, and this data will be sampled every 100ms.
3. **Calculating Sound Intensity (RMS)**: The app will calculate the sound intensity from the microphone data, which will be used to adjust the engine sound's speed and volume.
4. **Playing Engine Sounds**: The app will use the `MediaPlayer` class to play the engine sounds.
5. **Dynamically Adjusting Speed and Volume**: Functions will be written to dynamically adjust the engine sound's speed and volume based on the RMS value of the microphone data.
6. **Smoothing Sound Transitions**: To avoid sudden jumps in sound, transitions will be smoothed, and the `Handler` class will be used to control these transitions.
7. **UI Design and Indicators**: A UI element (such as a progress bar) will be added to visually indicate the sound intensity, dynamically changing as the sound level increases.

The project will run on the Android platform, providing the user with a real-time engine sound experience.
