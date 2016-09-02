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
 */
package com.whyjustin.magicmirror.alexa;

public interface MagicMirrorProxy
{
  void handleRegistrationCode(String registrationCode);
  void handleToken(String accessToken);
  void handleCommand(String command);
  void handleAlexaCompleted();
}
