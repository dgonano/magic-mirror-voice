/**
 * Copyright (c) 2016 Justin Young
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * You may not use this file except in compliance with the License. A copy of the License is located the "LICENSE.txt"
 * file accompanying this source. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.whyjustin.magicmirror.alexa;

import java.io.IOException;

import com.amazon.alexa.avs.AVSAudioPlayerFactory;
import com.amazon.alexa.avs.AVSController;
import com.amazon.alexa.avs.AlertManagerFactory;
import com.amazon.alexa.avs.DialogRequestIdAuthority;
import com.amazon.alexa.avs.ExpectSpeechListener;
import com.amazon.alexa.avs.RecordingRMSListener;
import com.amazon.alexa.avs.RequestListener;
import com.amazon.alexa.avs.config.DeviceConfig;
import com.amazon.alexa.avs.http.AVSClientFactory;

public class MagicMirror
    implements ExpectSpeechListener, RecordingRMSListener
{
  private final VoicePatternTrigger voicePatternTrigger;

  private final AVSController avsController;
  private final CompanionServiceAuthenticator companionServiceAuthenticator;
  private final MagicMirrorProxy magicMirrorProxy;

  private Thread autoEndpoint = null;
  private static final int ENDPOINT_THRESHOLD = 5;
  private static final int ENDPOINT_SECONDS = 2;

  public MagicMirror(DeviceConfig deviceConfig, VoicePatternConfig voicePatternConfig,
                     MagicMirrorProxy magicMirrorProxy) throws Exception
  {
    this.magicMirrorProxy = magicMirrorProxy;

    voicePatternTrigger = new VoicePatternTrigger(magicMirrorProxy,
        voicePatternConfig.getDictionaryPath(), voicePatternConfig.getLanguageModelPath());

    avsController = new AVSController(this, new AVSAudioPlayerFactory(), new AlertManagerFactory(),
        new AVSClientFactory(deviceConfig), DialogRequestIdAuthority.getInstance());

    companionServiceAuthenticator = new CompanionServiceAuthenticator(deviceConfig);
    String registrationCode = companionServiceAuthenticator.requestRegistrationCode();
    magicMirrorProxy.handleRegistrationCode(registrationCode);

    avsController.startHandlingDirectives();
  }

  public void registerDevice() throws IOException {
    String token = companionServiceAuthenticator.requestAccessToken();
    avsController.onAccessTokenReceived(token);
    magicMirrorProxy.handleToken(token);
  }

  public void triggerAlexa() {
    avsController.onUserActivity();
    RequestListener requestListener = new RequestListener() {
      @Override
      public void onRequestSuccess() {
        avsController.processingFinished();
        magicMirrorProxy.handleAlexaCompleted();
      }

      @Override
      public void onRequestError(Throwable e) {
        avsController.processingFinished();
        magicMirrorProxy.handleAlexaCompleted();
      }
    };

    avsController.startRecording(this, requestListener);
  }

  public void listen() throws IOException {
    voicePatternTrigger.listen();
  }

  @Override
  public void onExpectSpeechDirective() {

  }

  @Override
  public void rmsChanged(final int rms) {
    // if greater than threshold or not recording, kill the autoendpoint thread
    if ((rms == 0) || (rms > ENDPOINT_THRESHOLD)) {
      if (autoEndpoint != null) {
        autoEndpoint.interrupt();
        autoEndpoint = null;
      }
    } else if (rms < ENDPOINT_THRESHOLD) {
      // start the autoendpoint thread if it isn't already running
      if (autoEndpoint == null) {
        autoEndpoint = new Thread() {
          @Override
          public void run() {
            try {
              Thread.sleep(ENDPOINT_SECONDS * 1000);
              avsController.stopRecording(); // hit stop if we get through the autoendpoint time
            } catch (InterruptedException e) {
              return;
            }
          }
        };
        autoEndpoint.start();
      }
    }
  }
}
