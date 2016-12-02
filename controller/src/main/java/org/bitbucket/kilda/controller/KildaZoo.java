package org.bitbucket.kilda.controller;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * KildaZoo is the starting point for all things ZooKeeper.
 * 
 * It'll evolve and be refactored.
 * 
 * NB: Java examples:
 * http://www.programcreek.com/java-api-examples/index.php?api=org.apache.zookeeper.ZooKeeper
 * 
 */
public class KildaZoo {

	private String zooHost;
	private int zooPort;
	private CuratorFramework client;
	private static final RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);

	public KildaZoo(String zooHost, int zooPort) {
		this.zooHost = zooHost;
		this.zooPort = zooPort;
	}

	public CuratorFramework connect() {
		return connect("kilda");
	}
	
	public CuratorFramework connect(String namespace) {
		String address = this.zooHost + ":" + this.zooPort;
		client = CuratorFrameworkFactory.builder().connectString(address).retryPolicy(retryPolicy)
				.namespace(namespace).build();
		client.start();
		return client;
	}

	public CuratorFramework getClient() {
		return client;
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println("ZooKeeper is your friend.");
		KildaZoo zc = new KildaZoo("127.0.0.1", 2181);
		String path = "/brokers/ids/0";
		System.out.println(path + ": " + new String(zc.connect("").getData().forPath(path)));
	}

}
