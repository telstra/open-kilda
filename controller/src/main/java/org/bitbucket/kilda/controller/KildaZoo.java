package org.bitbucket.kilda.controller;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KildaZoo is the starting point for all things ZooKeeper.
 * 
 * It'll evolve and be refactored.
 * 
 * NB: examples:
 * - Java: http://www.programcreek.com/java-api-examples/index.php?api=org.apache.zookeeper.ZooKeeper
 * - Curator: http://curator.apache.org/getting-started.html
 * 
 */
public class KildaZoo {

	private static final Logger logger = LoggerFactory.getLogger(KildaZoo.class);
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
