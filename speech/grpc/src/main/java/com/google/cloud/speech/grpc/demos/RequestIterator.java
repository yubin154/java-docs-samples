/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.speech.grpc.demos;

import com.google.cloud.speech.v1.InitialRecognizeRequest;
import com.google.cloud.speech.v1.AudioRequest;
import com.google.protobuf.ByteString;
import com.google.cloud.speech.v1.InitialRecognizeRequest.AudioEncoding;
import com.google.cloud.speech.v1.RecognizeRequest;

import java.io.InputStream;
import java.util.Iterator;

class RequestIterator implements Iterator<RecognizeRequest> {
  private InitialRecognizeRequest initial;
  private InputStream inputStream;
  private byte[] buffer;
  private int bytesRead;

  public RequestIterator(InputStream inputStream, int samplingRate) {
    // Build and send a RecognizeRequest containing the parameters for processing the audio.
    initial = InitialRecognizeRequest.newBuilder()
        .setEncoding(AudioEncoding.LINEAR16)
        .setSampleRate(samplingRate)
        .setInterimResults(true)
        .build();

    this.inputStream = inputStream;
    // For LINEAR16 at 16000 Hz sample rate, 3200 bytes corresponds to 100 milliseconds of audio.
    buffer = new byte[3200];
  }

  public boolean hasNext() {
    // Technically this is off-by-one, but it should serve.
    return bytesRead != -1;
  }

  public RecognizeRequest next() {
    // The initial request - return the request we prepared.
    if (initial != null) {
      RecognizeRequest request = RecognizeRequest.newBuilder()
          .setInitialRequest(initial)
          .build();
      initial = null;
      return request;
    }

    bytesRead = inputStream.read(buffer);

    // No more bytes to read - return an empty request.
    if (bytesRead == -1) {
      AudioRequest audio = AudioRequest.newBuilder()
          .setContent(ByteString.EMPTY)
          .build();
      return RecognizeRequest.newBuilder()
          .setAudioRequest(audio)
          .build();
    }

    // Construct a request from the bytes read.
    AudioRequest audio = AudioRequest.newBuilder()
        .setContent(ByteString.copyFrom(buffer, 0, bytesRead))
        .build();
    return RecognizeRequest.newBuilder()
        .setAudioRequest(audio)
        .build();
  }
}
