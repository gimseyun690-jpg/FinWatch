package com.finwatch.news.content;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public interface HostResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;
}
