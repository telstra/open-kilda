package org.bitbucket.openkilda.tools.mininet;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface IMininetSwitch {

  String getName();

  void setName(String name);

  String getDpid();

  void setDpid(String dpid);

  List<MininetInterface> getInterface();

  void setInterface(List<MininetInterface> interfaces);

  Boolean getConnected();

  void setConnected(Boolean connected);

  void addInterface(MininetInterface intf);

  String toJson() throws JsonProcessingException;

}
