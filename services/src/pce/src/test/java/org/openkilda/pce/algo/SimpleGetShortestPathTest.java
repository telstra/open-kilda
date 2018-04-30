package org.openkilda.pce.algo;

import org.junit.Test;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.model.SimpleIsl;

import java.util.LinkedList;

import static org.junit.Assert.*;

public class SimpleGetShortestPathTest {

    public AvailableNetwork buildNetwork1() {
        AvailableNetwork network = new AvailableNetwork();
        network.initOneEntry("00:00:00:22:3d:5a:04:87", "00:00:b0:d2:f5:00:5a:b8", 7, 60, 0, 3);
        network.initOneEntry("00:00:00:22:3d:5a:04:87","00:00:70:72:cf:d2:48:6c",5,32,10,18);
        network.initOneEntry("00:00:00:22:3d:5a:04:87","00:00:00:22:3d:6b:00:04",2,2,10,2);
        network.initOneEntry("00:00:00:22:3d:5a:04:87","00:00:70:72:cf:d2:47:a6",6,16,10,15);
        network.initOneEntry("00:00:00:22:3d:5a:04:87","00:00:00:22:3d:6c:00:b8",1,3,40,4);
        network.initOneEntry("00:00:00:22:3d:6b:00:04","00:00:00:22:3d:6c:00:b8",1,1,100,7);
        network.initOneEntry("00:00:00:22:3d:6b:00:04","00:00:00:22:3d:5a:04:87",2,2,10,1);
        network.initOneEntry("00:00:00:22:3d:6c:00:b8","00:00:b0:d2:f5:00:5a:b8",6,19,10,3);
        network.initOneEntry("00:00:00:22:3d:6c:00:b8","00:00:00:22:3d:6b:00:04",1,1,100,2);
        network.initOneEntry("00:00:00:22:3d:6c:00:b8","00:00:00:22:3d:5a:04:87",3,1,100,2);
        network.initOneEntry("00:00:70:72:cf:d2:47:a6","00:00:70:72:cf:d2:48:6c",52,52,10,381);
        network.initOneEntry("00:00:70:72:cf:d2:47:a6","00:00:00:22:3d:5a:04:87",16,6,10,18);
        network.initOneEntry("00:00:70:72:cf:d2:48:6c","00:00:b0:d2:f5:00:5a:b8",48,49,10,97);
        network.initOneEntry("00:00:70:72:cf:d2:48:6c","00:00:70:72:cf:d2:47:a6",52,52,10,1021);
        network.initOneEntry("00:00:70:72:cf:d2:48:6c","00:00:00:22:3d:5a:04:87",32,5,10,16);
        network.initOneEntry("00:00:b0:d2:f5:00:5a:b8","00:00:70:72:cf:d2:48:6c",49,48,10,0);
        network.initOneEntry("00:00:b0:d2:f5:00:5a:b8","00:00:00:22:3d:6c:00:b8",19,6,10,3);
        network.initOneEntry("00:00:b0:d2:f5:00:5a:b8","00:00:00:22:3d:5a:04:87",50,7,0,3);
        return network;
    }


    @Test
    public void getPath() {

        AvailableNetwork network = buildNetwork1();
        network.removeSelfLoops().reduceByCost();
        SimpleGetShortestPath forward = new SimpleGetShortestPath(network, "00:00:70:72:cf:d2:47:a6", "00:00:b0:d2:f5:00:5a:b8", 35);

        LinkedList<SimpleIsl> fpath = forward.getPath();
        System.out.println("forward.getPath() = " + fpath);
        SimpleGetShortestPath reverse = new SimpleGetShortestPath(network, "00:00:b0:d2:f5:00:5a:b8", "00:00:70:72:cf:d2:47:a6", 35);
        LinkedList<SimpleIsl> rpath = reverse.getPath(fpath);
        System.out.println("reverse.getPath() = " + rpath);




    }
}