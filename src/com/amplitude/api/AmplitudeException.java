package com.amplitude.api;


public class AmplitudeException extends Exception {
  public AmplitudeException(String message) {
    super(message);
  }

  public AmplitudeException(String message, Throwable cause) {
    super(message, cause);
  }

  public AmplitudeException(Throwable cause) {
    super(cause);
  }
}
