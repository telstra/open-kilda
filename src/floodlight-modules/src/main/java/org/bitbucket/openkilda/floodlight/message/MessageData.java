package org.bitbucket.openkilda.floodlight.message;

import java.io.Serializable;

public abstract class MessageData implements Serializable {

  static final long serialVersionUID = 1L;

  public MessageData() {
    // no args init for serializer
  }
}
