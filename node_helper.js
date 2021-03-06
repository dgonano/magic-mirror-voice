/**
 * Copyright (c) 2016 Justin Young
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
const NodeHelper = require('node_helper');
const bootstrap = require('./magic-mirror-voice-bootstrap');
const alexaJavaClientFactory = require('./alexa-java-client/alexa-java-client');
const sphinxJavaClientFactory = require('./sphinx-java-client/sphinx-java-client');

var isLoaded = false;
var runtime = {
  isLoading: false,
  isRegistered: false,
  isAlexaSpeaking: false,
  isAlexaListening: false,
  registrationCode: undefined,
  registrationUrl: undefined
};
var alexaJavaClient = undefined;
var sphinxJavaClient = undefined;
var alexaListeningTimeout;

module.exports = NodeHelper.create({
  socketNotificationReceived: function(notification, payload) {
    var self = this;
    if (notification === 'INIT') {
      self.config = payload;
      runtime.isLoading = true;
      self.sendSocketNotification('UPDATE_DOM', runtime);

      if (!isLoaded) {
        isLoaded = true;

        bootstrap(self.config).then(function(java) {
          alexaJavaClient = alexaJavaClientFactory.get();
          alexaJavaClient.boot(java, self.config, {
            onRegistrationCode: function(registrationCode) {
              self.handleRegistrationCode(registrationCode);
            },
            onAccessToken: function(token) {
              self.handleAccessToken(token);
            },
            onAlexaCompleted: function() {

            },
            onAlexaSpeechStarted: function() {
              self.handleAlexaSpeechStarted();
            },
            onAlexaSpeechFinished: function() {
              self.handleAlexaSpeechFinished();
            }
          });

          sphinxJavaClient = sphinxJavaClientFactory.get();
          sphinxJavaClient.boot(java, self.config, {
            handleCommand: function(command) {
              self.handleCommand(command);
            }
          }).then(function() {
            sphinxJavaClient.listen();
            self.heartBeat();
          });

          runtime.isLoading = false;
          self.sendSocketNotification('UPDATE_DOM', runtime);
        });
      }
    }
  },
  // Recover from any failures by checking Sphinx every 30 seconds
  heartBeat: function() {
    setInterval(function() {
      // There is a state the module can get into where the Alexa client listen() has been called but Alexa does not
      // trigger speech started. This timeout will clear this state is if occurs for more than 30 seconds.
      if (runtime.isAlexaListening) {
        alexaListeningTimeout = setTimeout(function() {
          runtime.isAlexaListening = false;
        }, 30);
      }

      if (!runtime.isAlexaSpeaking && !runtime.isAlexaListening) {
        sphinxJavaClient.isListening().then(function(isListening) {
          if (!runtime.isAlexaSpeaking && !runtime.isAlexaListening) {
            if (!isListening) {
              sphinxJavaClient.listen();
            }
          }
        });
      }
    }, 1000 * 30);
  },
  handleRegistrationCode: function(registrationCode) {
    var self = this;
    runtime.registrationCode = registrationCode;
    runtime.registrationUrl = self.config.companion.serviceUrl + '/provision/' + runtime.registrationCode;
    console.info('Register Alexa Java Client by navigating to ' + runtime.registrationUrl);
    self.sendSocketNotification('UPDATE_DOM', runtime);
  },
  handleAccessToken: function(token) {
    var self = this;
    runtime.isRegistered = true;
    console.info('Alexa Java Client registered with token: ' + token);
    self.sendSocketNotification('UPDATE_DOM', runtime);
  },
  handleAlexaSpeechStarted: function() {
    runtime.isAlexaSpeaking = true;
    runtime.isAlexaListening = false;
    clearImmediate(alexaListeningTimeout);

    sphinxJavaClient.stop();
  },
  handleAlexaSpeechFinished: function() {
    runtime.isAlexaSpeaking = false;
    runtime.isAlexaTriggered = false;

    sphinxJavaClient.listen();
  },
  handleCommand: function(requestedCommand) {
    var self = this;
    runtime.isAlexaListening = false;
    clearImmediate(alexaListeningTimeout);

    for (var commandKey in self.config.sphinx.commands) {
      if (self.config.sphinx.commands.hasOwnProperty(commandKey)) {
        if (commandKey.toLowerCase() === requestedCommand.toLowerCase()) {
          var command = self.config.sphinx.commands[commandKey];
          var action = command.action;

          if (action === 'alexa') {
            if (runtime.isRegistered) {
              runtime.isAlexaListening = true;
              alexaJavaClient.triggerAlexa();
            }
          } else if (action == 'sendNotification') {
            self.sendSocketNotification('SEND_NOTIFICATION', command);
          }
        }
      }
    }
    if (!runtime.isAlexaListening) {
      sphinxJavaClient.listen();
    }
  }
});
